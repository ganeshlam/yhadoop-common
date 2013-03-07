/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.mapred;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.lib.IdentityMapper;
import org.apache.hadoop.mapred.lib.IdentityReducer;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.test.PathUtils;
import org.junit.Test;


public class TestNetworkedJob {
  private static Path testDir = new Path(PathUtils.getTestPath(TestNetworkedJob.class), "data");
  private static Path inFile = new Path(testDir, "in");
  private static Path outDir = new Path(testDir, "out");

  @Test
  public void testGetNullCounters() throws Exception {
    //mock creation
    Job mockJob = mock(Job.class);
    RunningJob underTest = new JobClient.NetworkedJob(mockJob); 

    when(mockJob.getCounters()).thenReturn(null);
    assertNull(underTest.getCounters());
    //verification
    verify(mockJob).getCounters();
  }
  
  @Test
  public void testGetJobStatus() throws IOException, InterruptedException,
      ClassNotFoundException {
    MiniMRClientCluster mr = null;
    FileSystem fileSys = null;

    try {
      mr = new MiniMRClientClusterBuilder(this.getClass()).noOfNMs(2).build();

      JobConf job = new JobConf(mr.getConfig());

      fileSys = FileSystem.get(job);
      fileSys.delete(testDir, true);
      FSDataOutputStream out = fileSys.create(inFile, true);
      out.writeBytes("This is a test file");
      out.close();

      FileInputFormat.setInputPaths(job, inFile);
      FileOutputFormat.setOutputPath(job, outDir);

      job.setInputFormat(TextInputFormat.class);
      job.setOutputFormat(TextOutputFormat.class);

      job.setMapperClass(IdentityMapper.class);
      job.setReducerClass(IdentityReducer.class);
      job.setNumReduceTasks(0);

      JobClient client = new JobClient(mr.getConfig());
      RunningJob rj = client.submitJob(job);
      JobID jobId = rj.getID();

      // The following asserts read JobStatus twice and ensure the returned
      // JobStatus objects correspond to the same Job.
      assertEquals("Expected matching JobIDs", jobId, client.getJob(jobId)
          .getJobStatus().getJobID());
      assertEquals("Expected matching startTimes", rj.getJobStatus()
          .getStartTime(), client.getJob(jobId).getJobStatus()
          .getStartTime());
    } finally {
      if (fileSys != null) {
        fileSys.delete(testDir, true);
      }
      if (mr != null) {
        mr.stop();
      }
    }
  }
}
