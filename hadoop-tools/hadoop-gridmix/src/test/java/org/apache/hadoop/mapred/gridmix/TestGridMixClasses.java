package org.apache.hadoop.mapred.gridmix;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.apache.hadoop.CustomOutputCommitter;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PositionedReadable;
import org.apache.hadoop.fs.Seekable;
import org.apache.hadoop.io.DataInputBuffer;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.RawComparator;
import org.apache.hadoop.io.WritableUtils;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.JobContext;
import org.apache.hadoop.mapred.RawKeyValueIterator;
import org.apache.hadoop.mapred.gridmix.GridmixKey.Spec;
import org.apache.hadoop.mapred.gridmix.SleepJob.SleepReducer;
import org.apache.hadoop.mapred.gridmix.SleepJob.SleepSplit;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.MRJobConfig;
import org.apache.hadoop.mapreduce.MapContext;
import org.apache.hadoop.mapreduce.OutputCommitter;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.RecordWriter;
import org.apache.hadoop.mapreduce.ReduceContext;
import org.apache.hadoop.mapreduce.StatusReporter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.TaskAttemptID;
import org.apache.hadoop.mapreduce.Mapper.Context;
import org.apache.hadoop.mapreduce.counters.GenericCounter;
import org.apache.hadoop.mapreduce.lib.input.CombineFileSplit;
import org.apache.hadoop.mapreduce.lib.map.WrappedMapper;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.reduce.WrappedReducer;
import org.apache.hadoop.mapreduce.task.MapContextImpl;
import org.apache.hadoop.mapreduce.task.ReduceContextImpl;
import org.apache.hadoop.mapreduce.task.TaskAttemptContextImpl;
import org.apache.hadoop.mapreduce.task.TaskAttemptContextImpl.DummyReporter;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.tools.rumen.JobStory;
import org.apache.hadoop.tools.rumen.JobStoryProducer;
import org.apache.hadoop.tools.rumen.ResourceUsageMetrics;
import org.apache.hadoop.tools.rumen.ZombieJobProducer;
import org.apache.hadoop.util.Progress;
import org.junit.Test;
import static org.mockito.Mockito.*;

import static org.junit.Assert.*;

public class TestGridMixClasses {

  @Test
  public void testLoadSplit() throws Exception {

    LoadSplit test = getLoadSplit();

    ByteArrayOutputStream data = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(data);
    test.write(out);
    LoadSplit copy = new LoadSplit();
    copy.readFields(new DataInputStream(new ByteArrayInputStream(data
        .toByteArray())));

    // data should be the same
    assertEquals(test.getId(), copy.getId());
    assertEquals(test.getMapCount(), copy.getMapCount());
    assertEquals(test.getInputRecords(), copy.getInputRecords());

    assertEquals(test.getOutputBytes()[0], copy.getOutputBytes()[0]);
    assertEquals(test.getOutputRecords()[0], copy.getOutputRecords()[0]);
    assertEquals(test.getReduceBytes(0), copy.getReduceBytes(0));
    assertEquals(test.getReduceRecords(0), copy.getReduceRecords(0));
    assertEquals(test.getMapResourceUsageMetrics().getCumulativeCpuUsage(),
        copy.getMapResourceUsageMetrics().getCumulativeCpuUsage());
    assertEquals(test.getReduceResourceUsageMetrics(0).getCumulativeCpuUsage(),
        copy.getReduceResourceUsageMetrics(0).getCumulativeCpuUsage());

  }

  @Test
  public void testGridmixSplit() throws Exception {
    Path[] files = { new Path("one"), new Path("two") };
    long[] start = { 1, 2 };
    long[] lengths = { 100, 200 };
    String[] locations = { "locOne", "loctwo" };

    CombineFileSplit cfsplit = new CombineFileSplit(files, start, lengths,
        locations);
    ResourceUsageMetrics metrics = new ResourceUsageMetrics();
    metrics.setCumulativeCpuUsage(200);

    double[] reduceBytes = { 8.1d, 8.2d };
    double[] reduceRecords = { 9.1d, 9.2d };
    long[] reduceOutputBytes = { 101L, 102L };
    long[] reduceOutputRecords = { 111L, 112L };

    GridmixSplit test = new GridmixSplit(cfsplit, 2, 3, 4L, 5L, 6L, 7L,
        reduceBytes, reduceRecords, reduceOutputBytes, reduceOutputRecords);

    ByteArrayOutputStream data = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(data);
    test.write(out);
    GridmixSplit copy = new GridmixSplit();
    copy.readFields(new DataInputStream(new ByteArrayInputStream(data
        .toByteArray())));

    // data should be the same
    assertEquals(test.getId(), copy.getId());
    assertEquals(test.getMapCount(), copy.getMapCount());
    assertEquals(test.getInputRecords(), copy.getInputRecords());

    assertEquals(test.getOutputBytes()[0], copy.getOutputBytes()[0]);
    assertEquals(test.getOutputRecords()[0], copy.getOutputRecords()[0]);
    assertEquals(test.getReduceBytes(0), copy.getReduceBytes(0));
    assertEquals(test.getReduceRecords(0), copy.getReduceRecords(0));

  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  @Test
  public void test1() throws Exception {

    Configuration conf = new Configuration();
    conf.setInt(JobContext.NUM_REDUCES, 2);

    CompressionEmulationUtil.setCompressionEmulationEnabled(conf, true);
    conf.setBoolean(MRJobConfig.MAP_OUTPUT_COMPRESS, true);

    TaskAttemptID taskid = new TaskAttemptID();
    RecordReader reader = new FakeRecordReader();

    LoadRecordWriter writer = new LoadRecordWriter();

    OutputCommitter committer = new CustomOutputCommitter();
    StatusReporter reporter = new TaskAttemptContextImpl.DummyReporter();
    LoadSplit split = getLoadSplit();

    MapContext mapcontext = new MapContextImpl(conf, taskid, reader, writer,
        committer, reporter, split);
    Context ctxt = new WrappedMapper().getMapContext(mapcontext);

    reader.initialize(split, ctxt);
    ctxt.getConfiguration().setBoolean(MRJobConfig.MAP_OUTPUT_COMPRESS, true);
    CompressionEmulationUtil.setCompressionEmulationEnabled(
        ctxt.getConfiguration(), true);

    // when(ctxt.getCounter(TaskCounter.SPILLED_RECORDS)).thenReturn(counter);

    LoadJob.LoadMapper mapper = new LoadJob.LoadMapper();

    mapper.run(ctxt);

    // mapper.cleanup(ctxt);
    System.out.println("OK");
    Map<Object, Object> data = writer.getData();
    assertEquals(2, data.size());

  }

  private LoadSplit getLoadSplit() throws Exception {

    Path[] files = { new Path("one"), new Path("two") };
    long[] start = { 1, 2 };
    long[] lengths = { 100, 200 };
    String[] locations = { "locOne", "loctwo" };

    CombineFileSplit cfsplit = new CombineFileSplit(files, start, lengths,
        locations);
    ResourceUsageMetrics metrics = new ResourceUsageMetrics();
    metrics.setCumulativeCpuUsage(200);
    ResourceUsageMetrics[] rMetrics = { metrics };

    double[] reduceBytes = { 8.1d, 8.2d };
    double[] reduceRecords = { 9.1d, 9.2d };
    long[] reduceOutputBytes = { 101L, 102L };
    long[] reduceOutputRecords = { 111L, 112L };

    LoadSplit result = new LoadSplit(cfsplit, 2, 1, 4L, 5L, 6L, 7L,
        reduceBytes, reduceRecords, reduceOutputBytes, reduceOutputRecords,
        metrics, rMetrics);
    return result;
  }

  protected class FakeRecordReader extends
      RecordReader<NullWritable, GridmixRecord> {

    int counter = 10;

    @Override
    public void initialize(InputSplit split, TaskAttemptContext context)
        throws IOException, InterruptedException {

    }

    @Override
    public boolean nextKeyValue() throws IOException, InterruptedException {
      counter--;
      return counter > 0;
    }

    @Override
    public NullWritable getCurrentKey() throws IOException,
        InterruptedException {

      return NullWritable.get();
    }

    @Override
    public GridmixRecord getCurrentValue() throws IOException,
        InterruptedException {
      return new GridmixRecord(100, 100L);
    }

    @Override
    public float getProgress() throws IOException, InterruptedException {
      return counter / 10.0f;
    }

    @Override
    public void close() throws IOException {
      counter = 10;
    }
  }

  private class LoadRecordWriter extends RecordWriter<Object, Object> {
    private Map<Object, Object> data = new HashMap<Object, Object>();

    @Override
    public void write(Object key, Object value) throws IOException,
        InterruptedException {
      data.put(key, value);
    }

    @Override
    public void close(TaskAttemptContext context) throws IOException,
        InterruptedException {
    }

    public Map<Object, Object> getData() {
      return data;
    }

  };

  @Test
  public void testLoadJobLoadSortComparator() throws Exception {
    LoadJob.LoadSortComparator test = new LoadJob.LoadSortComparator();

    ByteArrayOutputStream data = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(data);
    WritableUtils.writeVInt(dos, 2);
    WritableUtils.writeVInt(dos, 1);
    WritableUtils.writeVInt(dos, 4);
    WritableUtils.writeVInt(dos, 7);
    WritableUtils.writeVInt(dos, 4);

    byte[] b1 = data.toByteArray();

    byte[] b2 = data.toByteArray();

    // int s1, int l1, byte[] b2, int s2, int l2
    assertEquals(0, test.compare(b1, 0, 1, b2, 0, 1));
    b2[2] = 5;
    assertEquals(-1, test.compare(b1, 0, 1, b2, 0, 1));
    b2[2] = 2;
    assertEquals(2, test.compare(b1, 0, 1, b2, 0, 1));
    // compare arrays by first byte witch offset (2-1) because 4==4
    b2[2] = 4;
    assertEquals(1, test.compare(b1, 0, 1, b2, 1, 1));

  }

  @Test
  public void testGridmixJobSpecGroupingComparator() throws Exception {
    GridmixJob.SpecGroupingComparator test = new GridmixJob.SpecGroupingComparator();

    ByteArrayOutputStream data = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(data);
    WritableUtils.writeVInt(dos, 2);
    WritableUtils.writeVInt(dos, 1);
    WritableUtils.writeVInt(dos, 0);
    WritableUtils.writeVInt(dos, 7);
    WritableUtils.writeVInt(dos, 4);

    byte[] b1 = data.toByteArray();

    byte[] b2 = data.toByteArray();

    // int s1, int l1, byte[] b2, int s2, int l2
    assertEquals(0, test.compare(b1, 0, 1, b2, 0, 1));
    b2[2] = 1;
    assertEquals(-1, test.compare(b1, 0, 1, b2, 0, 1));
    // by Reduce spec
    b2[2] = 1;
    assertEquals(-1, test.compare(b1, 0, 1, b2, 0, 1));

    assertEquals(0, test.compare(new GridmixKey(GridmixKey.DATA, 100, 2),
        new GridmixKey(GridmixKey.DATA, 100, 2)));
    // REDUSE SPEC
    assertEquals(-1, test.compare(
        new GridmixKey(GridmixKey.REDUCE_SPEC, 100, 2), new GridmixKey(
            GridmixKey.DATA, 100, 2)));
    assertEquals(1, test.compare(new GridmixKey(GridmixKey.DATA, 100, 2),
        new GridmixKey(GridmixKey.REDUCE_SPEC, 100, 2)));
    // only DATA
    assertEquals(2, test.compare(new GridmixKey(GridmixKey.DATA, 102, 2),
        new GridmixKey(GridmixKey.DATA, 100, 2)));

  }

  @Test
  public void testCompareGridmixJob() throws Exception {
    Configuration conf = new Configuration();
    Path outRoot = new Path("target");
    JobStory jobdesc = mock(JobStory.class);
    when(jobdesc.getName()).thenReturn("JobName");
    when(jobdesc.getJobConf()).thenReturn(new JobConf(conf));
    UserGroupInformation ugi = UserGroupInformation.getCurrentUser();
    // final Configuration conf, long submissionMillis,
    // final JobStory jobdesc, Path outRoot, UserGroupInformation ugi,
    // final int seq

    GridmixJob j1 = new LoadJob(conf, 1000L, jobdesc, outRoot, ugi, 0);
    GridmixJob j2 = new LoadJob(conf, 1000L, jobdesc, outRoot, ugi, 0);
    assertTrue(j1.equals(j2));
    assertEquals(0, j1.compareTo(j2));
  }

  /*
   * test ReadRecordFactory hould read all data from inputstream
   */
  @Test
  public void testReadRecordFactory() throws Exception {

    // RecordFactory factory, InputStream src, Configuration conf
    RecordFactory rf = new FakeRecordFactory();
    FakeInputStream input = new FakeInputStream();
    ReadRecordFactory test = new ReadRecordFactory(rf, input,
        new Configuration());
    GridmixKey key = new GridmixKey(GridmixKey.DATA, 100, 2);
    GridmixRecord val = new GridmixRecord(200, 2);
    while (test.next(key, val)) {

    }
    // should be read 10* (GridmixKey.size +GridmixRecord.value)
    assertEquals(3000, input.getCounter());
    // shoutd be 0;
    assertEquals(-1, rf.getProgress(), 0.01);

    System.out.println("dd:" + input.getCounter());
    System.out.println("rf:" + rf.getProgress());
    test.close();
  }

  private class FakeRecordFactory extends RecordFactory {

    private int counter = 10;

    @Override
    public void close() throws IOException {

    }

    @Override
    public boolean next(GridmixKey key, GridmixRecord val) throws IOException {
      counter--;
      return counter >= 0;
    }

    @Override
    public float getProgress() throws IOException {
      return counter;
    }

  }

  private class FakeInputStream extends InputStream implements Seekable,
      PositionedReadable {
    private long counter;

    @Override
    public int read() throws IOException {
      return 0;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      int realLen = len - off;
      counter += realLen;
      for (int i = 0; i < b.length; i++) {
        b[i] = 0;
      }
      return realLen;
    }

    public long getCounter() {
      return counter;
    }

    @Override
    public void seek(long pos) throws IOException {

    }

    @Override
    public long getPos() throws IOException {
      return counter;
    }

    @Override
    public boolean seekToNewSource(long targetPos) throws IOException {
      return false;
    }

    @Override
    public int read(long position, byte[] buffer, int offset, int length)
        throws IOException {
      return 0;
    }

    @Override
    public void readFully(long position, byte[] buffer, int offset, int length)
        throws IOException {

    }

    @Override
    public void readFully(long position, byte[] buffer) throws IOException {

    }
  }

  private class FakeFSDataInputStream extends FSDataInputStream {

    public FakeFSDataInputStream(InputStream in) throws IOException {
      super(in);

    }

  }

  @Test
  public void testLoadJobLoadRecordReader() throws Exception {
    LoadJob.LoadRecordReader test = new LoadJob.LoadRecordReader();
    Configuration conf = new Configuration();

    FileSystem fs1 = mock(FileSystem.class);
    when(fs1.open((Path) anyObject())).thenReturn(
        new FakeFSDataInputStream(new FakeInputStream()));
    Path p1 = mock(Path.class);
    when(p1.getFileSystem((JobConf) anyObject())).thenReturn(fs1);

    FileSystem fs2 = mock(FileSystem.class);
    when(fs2.open((Path) anyObject())).thenReturn(
        new FakeFSDataInputStream(new FakeInputStream()));
    Path p2 = mock(Path.class);
    when(p2.getFileSystem((JobConf) anyObject())).thenReturn(fs2);

    Path[] paths = { p1, p2 };

    long[] start = { 0, 0 };
    long[] lengths = { 1000, 1000 };
    String[] locations = { "temp1", "temp2" };
    CombineFileSplit cfsplit = new CombineFileSplit(paths, start, lengths,
        locations);
    double[] reduceBytes = { 100, 100 };
    double[] reduceRecords = { 2, 2 };
    long[] reduceOutputBytes = { 500, 500 };
    long[] reduceOutputRecords = { 2, 2 };
    ResourceUsageMetrics metrics = new ResourceUsageMetrics();
    ResourceUsageMetrics[] rMetrics = { new ResourceUsageMetrics(),
        new ResourceUsageMetrics() };
    LoadSplit input = new LoadSplit(cfsplit, 2, 3, 1500L, 2L, 3000L, 2L,
        reduceBytes, reduceRecords, reduceOutputBytes, reduceOutputRecords,
        metrics, rMetrics);
    TaskAttemptID taskId = new TaskAttemptID();
    TaskAttemptContext ctxt = new TaskAttemptContextImpl(conf, taskId);
    test.initialize(input, ctxt);
    GridmixRecord gr = test.getCurrentValue();
    int counter = 0;
    while (test.nextKeyValue()) {
      gr = test.getCurrentValue();
      if (counter == 0) {
        assertEquals(0.5, test.getProgress(), 0.001);
      } else if (counter == 1) {
        assertEquals(1.0, test.getProgress(), 0.001);
      }
      assertEquals(1000, gr.getSize());
      counter++;
    }
    assertEquals(1000, gr.getSize());
    assertEquals(2, counter);

    test.close();
  }

  @Test
  public void testLoadJobLoadReducer() throws Exception {
    LoadJob.LoadReducer test = new LoadJob.LoadReducer();

    Configuration conf = new Configuration();
    conf.setInt(JobContext.NUM_REDUCES, 2);
    CompressionEmulationUtil.setCompressionEmulationEnabled(conf, true);
    conf.setBoolean(FileOutputFormat.COMPRESS, true);

    CompressionEmulationUtil.setCompressionEmulationEnabled(conf, true);
    conf.setBoolean(MRJobConfig.MAP_OUTPUT_COMPRESS, true);
    TaskAttemptID taskid = new TaskAttemptID();

    RawKeyValueIterator input = new FakeRawKeyValueIterator();

    Counter counter = new GenericCounter();
    Counter inputValueCounter = new GenericCounter();
    LoadRecordWriter output = new LoadRecordWriter();

    OutputCommitter committer = new CustomOutputCommitter();

    StatusReporter reporter = new DummyReporter();
    RawComparator comparator = new FakeRawComparator();

    ReduceContext<GridmixKey, GridmixRecord, NullWritable, GridmixRecord> reducecontext = new ReduceContextImpl<GridmixKey, GridmixRecord, NullWritable, GridmixRecord>(
        conf, taskid, input, counter, inputValueCounter, (RecordWriter) output,
        committer, reporter, comparator, GridmixKey.class, GridmixRecord.class);
    reducecontext.nextKeyValue();
    org.apache.hadoop.mapreduce.Reducer<GridmixKey, GridmixRecord, NullWritable, GridmixRecord>.Context context = new WrappedReducer<GridmixKey, GridmixRecord, NullWritable, GridmixRecord>()
        .getReducerContext(reducecontext);

    // test.setup(context);
    test.run(context);
    assertEquals(9, counter.getValue());
    assertEquals(10, inputValueCounter.getValue());
    assertEquals(1, output.getData().size());
    GridmixRecord record = (GridmixRecord) output.getData().values().iterator()
        .next();
    assertEquals(1593, record.getSize());
    System.out.println("OK");
  }

  protected class FakeRawKeyValueIterator implements RawKeyValueIterator {

    int counter = 10;

    @Override
    public DataInputBuffer getKey() throws IOException {
      ByteArrayOutputStream dt = new ByteArrayOutputStream();
      GridmixKey key = new GridmixKey(GridmixKey.REDUCE_SPEC, 10 * counter, 1L);
      Spec spec = new Spec();
      spec.rec_in = counter;
      spec.rec_out = counter;
      spec.bytes_out = counter * 100;

      key.setSpec(spec);
      key.write(new DataOutputStream(dt));
      DataInputBuffer result = new DataInputBuffer();
      byte[] b = dt.toByteArray();
      result.reset(b, 0, b.length);
      return result;
    }

    @Override
    public DataInputBuffer getValue() throws IOException {
      ByteArrayOutputStream dt = new ByteArrayOutputStream();
      GridmixRecord key = new GridmixRecord(100, 1);
      key.write(new DataOutputStream(dt));
      DataInputBuffer result = new DataInputBuffer();
      byte[] b = dt.toByteArray();
      result.reset(b, 0, b.length);
      return result;
    }

    @Override
    public boolean next() throws IOException {
      counter--;
      return counter >= 0;
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public Progress getProgress() {
      return null;
    }

  }

  private class FakeRawComparator implements RawComparator<GridmixKey> {

    @Override
    public int compare(GridmixKey o1, GridmixKey o2) {
      return 0;
    }

    @Override
    public int compare(byte[] b1, int s1, int l1, byte[] b2, int s2, int l2) {
      if ((l1 - s1) != (l2 - s2)) {
        return (l1 - s1) - (l2 - s2);
      }
      int len = l1 - s1;
      for (int i = 0; i < len; i++) {
        if (b1[s1 + i] != b2[s2 + i]) {
          return b1[s1 + i] - b2[s2 + i];
        }
      }
      return 0;
    }

  }

  @Test
  public void testSerialReaderThread() throws Exception {
    /*
     * JobSubmitter submitter, JobStoryProducer jobProducer, Path scratch,
     * Configuration conf, CountDownLatch startFlag, UserResolver resolver
     */
    Configuration conf = new Configuration();
    File fin = new File("src" + File.separator + "test" + File.separator
        + "resources" + File.separator + "data" + File.separator
        + "wordcount2.json");

    JobStoryProducer jobProducer = new ZombieJobProducer(new Path(
        fin.getAbsolutePath()), null, conf);
    CountDownLatch startFlag = new CountDownLatch(1);
    UserResolver resolver = new SubmitterUserResolver();
    FakeJobSubmitter submitter = new FakeJobSubmitter();
    File ws = new File("target" + File.separator + this.getClass().getName());
    if (!ws.exists()) {
      ws.mkdirs();
    }

    SerialJobFactory jobfactory = new SerialJobFactory(submitter, jobProducer,
        new Path(ws.getAbsolutePath()), conf, startFlag, resolver);

    Path ioPath = new Path(ws.getAbsolutePath());
    jobfactory.setDistCacheEmulator(new DistributedCacheEmulator(conf, ioPath));
    Thread test = jobfactory.createReaderThread();
    test.start();
    Thread.sleep(1000);
    // SerialReaderThread waits startFlag
    assertEquals(0, submitter.getJobs().size());
    startFlag.countDown();
    while (test.isAlive()) {
      Thread.sleep(1000);
      jobfactory.update(null);
    }
    // submitter was called  twice
    assertEquals(2, submitter.getJobs().size());
  }

  private class FakeJobSubmitter extends JobSubmitter {
    // counter for submitted jobs
    private List<GridmixJob> jobs = new ArrayList<GridmixJob>();

    public FakeJobSubmitter() {
      super(null, 1, 1, null, null);

    }

    @Override
    public void add(GridmixJob job) throws InterruptedException {
      jobs.add(job);
    }

    public List<GridmixJob> getJobs() {
      return jobs;
    }
  }
  
  @Test
  public  void testSleepMapper() throws Exception{
    SleepJob.SleepMapper test = new SleepJob.SleepMapper(); 
    /**
     * LongWritable key, LongWritable value, Context context
     */
  
    Configuration conf = new Configuration();
    conf.setInt(JobContext.NUM_REDUCES, 2);

    CompressionEmulationUtil.setCompressionEmulationEnabled(conf, true);
    conf.setBoolean(MRJobConfig.MAP_OUTPUT_COMPRESS, true);
    TaskAttemptID taskid = new TaskAttemptID();
    RecordReader reader = new FakeRecordReader();
    LoadRecordWriter writer = new LoadRecordWriter();
    OutputCommitter committer = new CustomOutputCommitter();
    StatusReporter reporter = new TaskAttemptContextImpl.DummyReporter();
    SleepSplit split = getSleepSplit();
    MapContext mapcontext = new MapContextImpl(conf, taskid, reader, writer,
        committer, reporter, split);
    Context context = new WrappedMapper().getMapContext(mapcontext);
   
    long start = System.currentTimeMillis();
    System.out.println("start:"+start);
    LongWritable key = new LongWritable(start+2000);
    LongWritable value = new LongWritable(start+2000);
    
    test.map(key, value, context);
    System.out.println("finish:"+System.currentTimeMillis());
    assertTrue(System.currentTimeMillis()>(start+2000));
    
    test.cleanup(context);
    assertEquals(1, writer.data.size());
  }
  
  private SleepSplit getSleepSplit() throws Exception {

    String[] locations = { "locOne", "loctwo" };

    long[] reduceDurations = { 101L, 102L };
    SleepSplit result = new SleepSplit(0, 2000L, reduceDurations, 2, locations);
    return result;
  }
  
  @Test
  public void testSleepReducer() throws Exception{
    Configuration conf = new Configuration();
    conf.setInt(JobContext.NUM_REDUCES, 2);
    CompressionEmulationUtil.setCompressionEmulationEnabled(conf, true);
    conf.setBoolean(FileOutputFormat.COMPRESS, true);

    CompressionEmulationUtil.setCompressionEmulationEnabled(conf, true);
    conf.setBoolean(MRJobConfig.MAP_OUTPUT_COMPRESS, true);
    TaskAttemptID taskid = new TaskAttemptID();

    RawKeyValueIterator input = new FakeRawKeyValueReducerIterator();

    Counter counter = new GenericCounter();
    Counter inputValueCounter = new GenericCounter();
    RecordWriter<NullWritable, NullWritable> output = new LoadRecordReduceWriter();

    OutputCommitter committer = new CustomOutputCommitter();

    StatusReporter reporter = new DummyReporter();
    RawComparator<GridmixKey> comparator = new FakeRawComparator();

    ReduceContext<GridmixKey, NullWritable, NullWritable, NullWritable> reducecontext = new ReduceContextImpl<GridmixKey, NullWritable, NullWritable, NullWritable>(
        conf, taskid, input, counter, inputValueCounter, output,
        committer, reporter, comparator, GridmixKey.class, NullWritable.class);
    org.apache.hadoop.mapreduce.Reducer<GridmixKey, NullWritable, NullWritable, NullWritable>.Context context = new WrappedReducer<GridmixKey, NullWritable, NullWritable, NullWritable>()
        .getReducerContext(reducecontext);

    SleepReducer test=new SleepReducer();
    long start = System.currentTimeMillis();
    test.setup(context);
    assertEquals("Sleeping... 900 ms left", context.getStatus());
    assertTrue( System.currentTimeMillis()>(start+900));
    test.cleanup(context);
    assertEquals("Slept for 900", context.getStatus());

    System.out.println("OK!");
  }
  private class LoadRecordReduceWriter extends RecordWriter<NullWritable, NullWritable> {
    private Map<NullWritable, NullWritable> data = new HashMap<NullWritable, NullWritable>();

    @Override
    public void write(NullWritable key, NullWritable value) throws IOException,
        InterruptedException {
      data.put(key, value);
    }

    @Override
    public void close(TaskAttemptContext context) throws IOException,
        InterruptedException {
    }

    public Map<NullWritable, NullWritable> getData() {
      return data;
    }
  };
  
  protected class FakeRawKeyValueReducerIterator implements RawKeyValueIterator {

    int counter = 10;

    @Override
    public DataInputBuffer getKey() throws IOException {
      ByteArrayOutputStream dt = new ByteArrayOutputStream();
      GridmixKey key = new GridmixKey(GridmixKey.REDUCE_SPEC, 10 * counter, 1L);
      Spec spec = new Spec();
      spec.rec_in = counter;
      spec.rec_out = counter;
      spec.bytes_out = counter * 100;

      key.setSpec(spec);
      key.write(new DataOutputStream(dt));
      DataInputBuffer result = new DataInputBuffer();
      byte[] b = dt.toByteArray();
      result.reset(b, 0, b.length);
      return result;
    }

    @Override
    public DataInputBuffer getValue() throws IOException {
      ByteArrayOutputStream dt = new ByteArrayOutputStream();
      NullWritable key =  NullWritable.get();
      key.write(new DataOutputStream(dt));
      DataInputBuffer result = new DataInputBuffer();
      byte[] b = dt.toByteArray();
      result.reset(b, 0, b.length);
      return result;
    }

    @Override
    public boolean next() throws IOException {
      counter--;
      return counter >= 0;
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public Progress getProgress() {
      return null;
    }

  }
}
