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
package org.apache.hadoop.hdfs.server.namenode;

import java.io.File;
import java.io.IOException;

/**
 * Utility class to faciliate some fault injection tests for the checkpointing
 * process.
 */
class CheckpointFaultInjector {
  static CheckpointFaultInjector instance = new CheckpointFaultInjector();
  
  static CheckpointFaultInjector getInstance() {
    return instance;
  }
  
  public void beforeGetImageSetsHeaders() throws IOException {}
  public void afterSecondaryCallsRollEditLog() throws IOException {}
  public void duringMerge() throws IOException {}
  public void afterSecondaryUploadsNewImage() throws IOException {}
  public void aboutToSendFile(File localfile) throws IOException {}

  public boolean shouldSendShortFile(File localfile) {
    return false;
  }
  public boolean shouldCorruptAByte(File localfile) {
    return false;
  }
  
}
