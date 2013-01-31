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

package org.apache.hadoop.hdfs.tools.offlineEditsViewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hdfs.DFSTestUtil;
import org.apache.hadoop.hdfs.server.namenode.FSEditLogOpCodes;
import org.apache.hadoop.hdfs.server.namenode.OfflineEditsViewerHelper;
import org.apache.hadoop.hdfs.tools.offlineEditsViewer.OfflineEditsViewer.Flags;
import org.junit.Before;
import org.junit.Test;

public class TestOfflineEditsViewer {
  private static final Log LOG = LogFactory.getLog(TestOfflineEditsViewer.class);

  private static final Map<FSEditLogOpCodes, Boolean> obsoleteOpCodes =
    new HashMap<FSEditLogOpCodes, Boolean>();

  static { initializeObsoleteOpCodes(); }

  private static String buildDir =
    System.getProperty("test.build.data", "build/test/data");

  private static String cacheDir =
    System.getProperty("test.cache.data", "build/test/cache");

  // to create edits and get edits filename
  private static final OfflineEditsViewerHelper nnHelper 
    = new OfflineEditsViewerHelper();

  /**
   * Initialize obsoleteOpCodes
   *
   * Reason for suppressing "deprecation" warnings:
   *
   * These are the opcodes that are not used anymore, some
   * are marked deprecated, we need to include them here to make
   * sure we exclude them when checking for completeness of testing,
   * that's why the "deprecation" warnings are suppressed.
   */
  @SuppressWarnings("deprecation")
  private static void initializeObsoleteOpCodes() {
    obsoleteOpCodes.put(FSEditLogOpCodes.OP_DATANODE_ADD, true);
    obsoleteOpCodes.put(FSEditLogOpCodes.OP_DATANODE_REMOVE, true);
    obsoleteOpCodes.put(FSEditLogOpCodes.OP_SET_NS_QUOTA, true);
    obsoleteOpCodes.put(FSEditLogOpCodes.OP_CLEAR_NS_QUOTA, true);
  }

  @Before
  public void setup() {
    new File(cacheDir).mkdirs();
  }
  
  /**
   * Test the OfflineEditsViewer
   */
  @Test
  public void testGenerated() throws IOException {

    LOG.info("START - testing with generated edits");

    nnHelper.startCluster();

    // edits generated by nnHelper (MiniDFSCluster), should have all op codes
    // binary, XML, reparsed binary
    String edits          = nnHelper.generateEdits();
    String editsParsedXml = cacheDir + "/editsParsed.xml";
    String editsReparsed  = cacheDir + "/editsReparsed";

    // parse to XML then back to binary
    assertEquals(0, runOev(edits, editsParsedXml, "xml", false));
    assertEquals(0, runOev(editsParsedXml, editsReparsed, "binary", false));

    // judgment time
    assertTrue(
      "Edits " + edits + " should have all op codes",
      hasAllOpCodes(edits));
    assertTrue(
      "Generated edits and reparsed (bin to XML to bin) should be same",
      filesEqualIgnoreTrailingZeros(edits, editsReparsed));

    // removes edits so do this at the end
    nnHelper.shutdownCluster();

    LOG.info("END");
  }

  @Test
  public void testRecoveryMode() throws IOException {
    LOG.info("START - testing with generated edits");

    nnHelper.startCluster();

    // edits generated by nnHelper (MiniDFSCluster), should have all op codes
    // binary, XML, reparsed binary
    String edits          = nnHelper.generateEdits();
    
    // Corrupt the file by truncating the end
    FileChannel editsFile = new FileOutputStream(edits, true).getChannel();
    editsFile.truncate(editsFile.size() - 5);
    
    String editsParsedXml = cacheDir + "/editsRecoveredParsed.xml";
    String editsReparsed  = cacheDir + "/editsRecoveredReparsed";
    String editsParsedXml2 = cacheDir + "/editsRecoveredParsed2.xml";

    // Can't read the corrupted file without recovery mode
    assertEquals(-1, runOev(edits, editsParsedXml, "xml", false));
    
    // parse to XML then back to binary
    assertEquals(0, runOev(edits, editsParsedXml, "xml", true));
    assertEquals(0, runOev(editsParsedXml, editsReparsed,  "binary", false));
    assertEquals(0, runOev(editsReparsed, editsParsedXml2, "xml", false));

    // judgment time
    assertTrue("Test round trip",
      filesEqualIgnoreTrailingZeros(editsParsedXml, editsParsedXml2));

    // removes edits so do this at the end
    nnHelper.shutdownCluster();

    LOG.info("END");
  }

  @Test
  public void testStored() throws IOException {

    LOG.info("START - testing with stored reference edits");

    // reference edits stored with source code (see build.xml)
    // binary, XML, reparsed binary
    String editsStored             = cacheDir + "/editsStored";
    String editsStoredParsedXml    = cacheDir + "/editsStoredParsed.xml";
    String editsStoredReparsed     = cacheDir + "/editsStoredReparsed";
    // reference XML version of editsStored (see build.xml)
    String editsStoredXml          = cacheDir + "/editsStored.xml";
      
    // parse to XML then back to binary
    assertEquals(0, runOev(editsStored, editsStoredParsedXml, "xml", false));
    assertEquals(0, runOev(editsStoredParsedXml, editsStoredReparsed,
        "binary", false));

    // judgement time
    assertTrue(
      "Edits " + editsStored + " should have all op codes",
      hasAllOpCodes(editsStored));
    assertTrue(
      "Reference XML edits and parsed to XML should be same",
      filesEqual(editsStoredXml, editsStoredParsedXml));
    assertTrue(
      "Reference edits and reparsed (bin to XML to bin) should be same",
      filesEqualIgnoreTrailingZeros(editsStored, editsStoredReparsed));

    LOG.info("END");
  }

  /**
   * Run OfflineEditsViewer
   *
   * @param inFilename input edits filename
   * @param outFilename oputput edits filename
   */
  private int runOev(String inFilename, String outFilename, String processor,
      boolean recovery) throws IOException {

    LOG.info("Running oev [" + inFilename + "] [" + outFilename + "]");

    OfflineEditsViewer oev = new OfflineEditsViewer();
    Flags flags = new Flags();
    flags.setPrintToScreen();
    if (recovery) {
      flags.setRecoveryMode();
    }
    return oev.go(inFilename, outFilename, processor, flags, null);
  }

  /**
   * Checks that the edits file has all opCodes
   *
   * @param filename edits file
   * @return true is edits (filename) has all opCodes
   */
  private boolean hasAllOpCodes(String inFilename) throws IOException {
    String outFilename = inFilename + ".stats";
    FileOutputStream fout = new FileOutputStream(outFilename);
    StatisticsEditsVisitor visitor = new StatisticsEditsVisitor(fout);
    OfflineEditsViewer oev = new OfflineEditsViewer();
    if (oev.go(inFilename, outFilename, "stats", new Flags(), visitor) != 0)
      return false;
    LOG.info("Statistics for " + inFilename + "\n" +
      visitor.getStatisticsString());
    
    boolean hasAllOpCodes = true;
    for(FSEditLogOpCodes opCode : FSEditLogOpCodes.values()) {
      // don't need to test obsolete opCodes
      if(obsoleteOpCodes.containsKey(opCode)) {
        continue;
      }
      if (opCode == FSEditLogOpCodes.OP_INVALID)
        continue;
      Long count = visitor.getStatistics().get(opCode);
      if((count == null) || (count == 0)) {
        hasAllOpCodes = false;
        LOG.info("Opcode " + opCode + " not tested in " + inFilename);
      }
    }
    return hasAllOpCodes;
  }

  /**
   * Compare two files, ignore trailing zeros at the end,
   * for edits log the trailing zeros do not make any difference,
   * throw exception is the files are not same
   *
   * @param filenameSmall first file to compare (doesn't have to be smaller)
   * @param filenameLarge second file to compare (doesn't have to be larger)
   */
  private boolean filesEqualIgnoreTrailingZeros(String filenameSmall,
    String filenameLarge) throws IOException {

    ByteBuffer small = ByteBuffer.wrap(DFSTestUtil.loadFile(filenameSmall));
    ByteBuffer large = ByteBuffer.wrap(DFSTestUtil.loadFile(filenameLarge));

    // now correct if it's otherwise
    if(small.capacity() > large.capacity()) {
      ByteBuffer tmpByteBuffer = small;
      small = large;
      large = tmpByteBuffer;
      String tmpFilename = filenameSmall;
      filenameSmall = filenameLarge;
      filenameLarge = tmpFilename;
    }

    // compare from 0 to capacity of small
    // the rest of the large should be all zeros
    small.position(0);
    small.limit(small.capacity());
    large.position(0);
    large.limit(small.capacity());

    // compares position to limit
    if(!small.equals(large)) { return false; }

    // everything after limit should be 0xFF
    int i = large.limit();
    large.clear();
    for(; i < large.capacity(); i++) {
      if(large.get(i) != FSEditLogOpCodes.OP_INVALID.getOpCode()) {
        return false;
      }
    }

    return true;
  }

  /**
   * Compare two files, throw exception is they are not same
   *
   * @param filename1 first file to compare
   * @param filename2 second file to compare
   */
  private boolean filesEqual(String filename1,
    String filename2) throws IOException {

    // make file 1 the small one
    ByteBuffer bb1 = ByteBuffer.wrap(DFSTestUtil.loadFile(filename1));
    ByteBuffer bb2 = ByteBuffer.wrap(DFSTestUtil.loadFile(filename2));

    // compare from 0 to capacity
    bb1.position(0);
    bb1.limit(bb1.capacity());
    bb2.position(0);
    bb2.limit(bb2.capacity());

    return bb1.equals(bb2);
  }
}
