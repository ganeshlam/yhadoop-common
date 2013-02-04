/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.mapred.gridmix;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.impl.Log4JLogger;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.ContentSummary;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobID;
import org.apache.hadoop.mapred.TaskReport;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.MRJobConfig;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.tools.rumen.JobStory;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Level;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class TestLoadJob {

  public static final Log LOG = LogFactory.getLog(Gridmix.class);

  {
    ((Log4JLogger) LogFactory.getLog("org.apache.hadoop.mapred.gridmix"))
        .getLogger().setLevel(Level.DEBUG);
    ((Log4JLogger) LogFactory.getLog(StressJobFactory.class)).getLogger()
        .setLevel(Level.DEBUG);
  }

  static GridmixJobSubmissionPolicy policy = GridmixJobSubmissionPolicy.REPLAY;
  private static final int NJOBS = 2;
  private static final long GENDATA = 1; // in megabytes

  @Before
  public void init() throws IOException {
    GridmixTestUtils.initCluster(TestLoadJob.class);
  }

  @After
  public void shutDown() throws IOException {
    GridmixTestUtils.shutdownCluster();
  }

  static class TestMonitor extends JobMonitor {
    private final BlockingQueue<Job> retiredJobs;
    private final int expected;

    public TestMonitor(int expected, Statistics stats) {
      super(3, TimeUnit.SECONDS, stats);
      this.expected = expected;
      retiredJobs = new LinkedBlockingQueue<Job>();
    }

    @Override
    protected void onSuccess(Job job) {
      LOG.info(" Job Sucess " + job);
      retiredJobs.add(job);
    }

    @Override
    protected void onFailure(Job job) {
      fail("Job failure: " + job);
    }

    public void verify(ArrayList<JobStory> submitted) throws Exception {
      assertEquals("Bad job count", expected, retiredJobs.size());
      
      final ArrayList<Job> succeeded = new ArrayList<Job>();
      assertEquals("Bad job count", expected, retiredJobs.drainTo(succeeded));
      final HashMap<String, JobStory> sub = new HashMap<String, JobStory>();
      for (JobStory spec : submitted) {
        sub.put(spec.getJobID().toString(), spec);
      }
      final JobClient client = new JobClient(GridmixTestUtils.mrvl.getConfig()); // mrCluster.createJobConf());
      for (Job job : succeeded) {
        final String jobName = job.getJobName();
        Configuration conf = job.getConfiguration();
        if (GenerateData.JOB_NAME.equals(jobName)) {
          RemoteIterator<LocatedFileStatus> rit = GridmixTestUtils.dfs
              .listFiles(new Path("/"), true);
          while (rit.hasNext()) {
            System.out.println(rit.next().toString());
          }
          final Path in = new Path("foo").makeQualified(GridmixTestUtils.dfs.getUri(),GridmixTestUtils.dfs.getWorkingDirectory());
          final ContentSummary generated =    GridmixTestUtils.dfs.getContentSummary(in);
          assertEquals( 550000, generated.getLength(),   10000);
          
          // we've written to space! and we wrote compressed data
          Counter counter = job.getCounters()
              .getGroup("org.apache.hadoop.mapreduce.FileSystemCounter")
              .findCounter("HDFS_BYTES_WRITTEN");
          assertEquals("Mismatched data gen", 550000, counter.getValue(),
              20000);
          assertEquals( generated.getLength(), counter.getValue());
          
          continue;
        } else if (GenerateDistCacheData.JOB_NAME.equals(jobName)) {
          continue;
        }

        final String originalJobId = conf.get(Gridmix.ORIGINAL_JOB_ID);
        final JobStory spec = sub.get(originalJobId);
        assertNotNull("No spec for " + jobName, spec);
        assertNotNull("No counters for " + jobName, job.getCounters());
        final String originalJobName = spec.getName();
        System.out.println("originalJobName=" + originalJobName
            + ";GridmixJobName=" + jobName + ";originalJobID=" + originalJobId);
        assertTrue("Original job name is wrong.",
            originalJobName.equals(conf.get(Gridmix.ORIGINAL_JOB_NAME)));

        // Gridmix job seqNum contains 6 digits
        int seqNumLength = 6;
        String jobSeqNum = new DecimalFormat("000000").format(conf.getInt(
            GridmixJob.GRIDMIX_JOB_SEQ, -1));
        // Original job name is of the format MOCKJOB<6 digit sequence number>
        // because MockJob jobNames are of this format.
        assertTrue(originalJobName.substring(
            originalJobName.length() - seqNumLength).equals(jobSeqNum));

        assertTrue("Gridmix job name is not in the expected format.",
            jobName.equals(GridmixJob.JOB_NAME_PREFIX + jobSeqNum));
        final FileStatus stat = GridmixTestUtils.dfs.getFileStatus(new Path(
            GridmixTestUtils.DEST, "" + Integer.valueOf(jobSeqNum)));
        assertEquals("Wrong owner for " + jobName, spec.getUser(),
            stat.getOwner());
        final int nMaps = spec.getNumberMaps();
        final TaskReport[] mReports = client.getMapTaskReports(JobID
            .downgrade(job.getJobID()));
        assertEquals("Mismatched map count", nMaps, mReports.length);
     
      }
      
    }
  }

  static class DebugGridmix extends Gridmix {

    private JobFactory<?> factory;
    private TestMonitor monitor;

    @Override
    protected JobMonitor createJobMonitor(Statistics stats)
        throws IOException {
      monitor = new TestMonitor(3, stats);
      return monitor;
    }

    @Override
    protected JobFactory<?> createJobFactory(JobSubmitter submitter,
        String traceIn, Path scratchDir, Configuration conf,
        CountDownLatch startFlag, UserResolver userResolver) throws IOException {
      factory = DebugJobFactory.getFactory(submitter, scratchDir, NJOBS, conf,
          startFlag, userResolver);
      return factory;
    }

    public void checkMonitor() throws Exception {
      monitor.verify(((DebugJobFactory.Debuggable) factory).getSubmitted());
    }
  }
/*
 * test serial Policy
 */
  @Test
  public void testSerialSubmit() throws Exception {
    policy = GridmixJobSubmissionPolicy.SERIAL;
    LOG.info("Serial started at " + System.currentTimeMillis());
    DebugGridmix client= doSubmission();
    Summarizer  summarizer= client.getSummarizer();
    assertEquals(2, summarizer.getExecutionSummarizer().getNumSuccessfulJobs());
    assertTrue( summarizer.getExecutionSummarizer().getNumMapTasksLaunched()>0);
    assertTrue( summarizer.getExecutionSummarizer().getNumReduceTasksLaunched()>0);
    assertTrue( summarizer.getExecutionSummarizer().getSimulationTime()>0);
    assertEquals("STRESS",summarizer.getExecutionSummarizer().getJobSubmissionPolicy());

    
    LOG.info("Serial ended at " + System.currentTimeMillis());
  }
  /*
   * test reply Policy
   */
  @Test
  public void testReplaySubmit() throws Exception {
    policy = GridmixJobSubmissionPolicy.REPLAY;
    LOG.info(" Replay started at " + System.currentTimeMillis());
    DebugGridmix client=doSubmission();
    Summarizer  summarizer= client.getSummarizer();
    assertEquals(2, summarizer.getExecutionSummarizer().getNumSuccessfulJobs());
    assertTrue( summarizer.getExecutionSummarizer().getNumMapTasksLaunched()>0);
    assertTrue( summarizer.getExecutionSummarizer().getNumReduceTasksLaunched()>0);
    assertTrue( summarizer.getExecutionSummarizer().getSimulationTime()>0);
    assertEquals("STRESS",summarizer.getExecutionSummarizer().getJobSubmissionPolicy());

    LOG.info(" Replay ended at " + System.currentTimeMillis());
  }

  private DebugGridmix doSubmission(String... optional) throws Exception {

    final Path in = new Path("foo").makeQualified(
        GridmixTestUtils.dfs.getUri(),
        GridmixTestUtils.dfs.getWorkingDirectory());
    final Path out = GridmixTestUtils.DEST.makeQualified(
        GridmixTestUtils.dfs.getUri(),
        GridmixTestUtils.dfs.getWorkingDirectory());
    final Path root = new Path("/user");
    Configuration conf = null;
    try {
      // required options
      final String[] required = {
          "-D" + FilePool.GRIDMIX_MIN_FILE + "=0",
          "-D" + Gridmix.GRIDMIX_OUT_DIR + "=" + out,
          "-D" + Gridmix.GRIDMIX_USR_RSV + "="
              + EchoUserResolver.class.getName(),
          "-D" + JobCreator.GRIDMIX_JOB_TYPE + "=" + JobCreator.LOADJOB.name(),
       //   "-D" + SleepJob.GRIDMIX_SLEEP_INTERVAL + "=" + "10" 
          };
      // mandatory arguments

   
      final String[] mandatory = { "-generate", String.valueOf(GENDATA) + "m",
          in.toString(), "-"
      };

      ArrayList<String> argv = new ArrayList<String>(required.length
          + optional.length + mandatory.length);
      for (String s : required) {
        argv.add(s);
      }
      for (String s : optional) {
        argv.add(s);
      }
      for (String s : mandatory) {
        argv.add(s);
      }

      DebugGridmix client = new DebugGridmix();
      conf = new Configuration();
      conf.setEnum(GridmixJobSubmissionPolicy.JOB_SUBMISSION_POLICY, policy);
      conf = GridmixTestUtils.mrvl.getConfig();
      // set timestamps
     // conf.setStrings("1", "2");
       //GridmixTestUtils.createHomeAndStagingDirectory(conf);
      // allow synthetic users to create home directories
      GridmixTestUtils.dfs.mkdirs(root, new FsPermission((short) 0777));
      GridmixTestUtils.dfs.setPermission(root, new FsPermission((short) 0777));
      String[] args = argv.toArray(new String[argv.size()]);
      LOG.info("Command line arguments:");
      for (int i = 0; i < args.length; ++i) {
        System.out.printf("    [%d] %s\n", i, args[i]);
      }
      UserGroupInformation ugi = UserGroupInformation.getLoginUser();
      conf.set(MRJobConfig.USER_NAME, ugi.getUserName());
      // run Gridmix
      int res = ToolRunner.run(conf, client, args);
      assertEquals("Client exited with nonzero status", 0, res);
      // check result
      client.checkMonitor();
      return client;
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      in.getFileSystem(conf).delete(in, true);
      out.getFileSystem(conf).delete(out, true);
      root.getFileSystem(conf).delete(root, true);
    }
    return null;
  }

}
