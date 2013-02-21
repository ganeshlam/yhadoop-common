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

import static org.junit.Assert.*;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.hdfs.MiniDFSNNTopology;
import org.apache.hadoop.hdfs.server.namenode.ClusterJspHelper.ClusterStatus;
import org.apache.hadoop.hdfs.server.namenode.ClusterJspHelper.DecommissionStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestClusterJspHelper {

  private MiniDFSCluster cluster;
  private Configuration conf;
  private final int dataNodeNumber = 2;
  private final int nameNodePort = 45541;
  private final int nameNodeHttpPort = 50070;    
  
  static final class ConfigurationForTestClusterJspHelper extends Configuration {
    static {
      addDefaultResource("testClusterJspHelperProp.xml");
    }
  }
  
  @Before
  public void setUp() throws Exception {
    conf = new ConfigurationForTestClusterJspHelper();  
    cluster = new MiniDFSCluster.Builder(conf)
        .nnTopology(
            MiniDFSNNTopology.simpleSingleNN(nameNodePort, nameNodeHttpPort))
        .numDataNodes(dataNodeNumber).build();
    cluster.waitClusterUp();
  }

  @After
  public void tearDown() throws Exception {
    if (cluster != null)
      cluster.shutdown();    
  }
  
  @Test
  public void testClusterJspHelperReports() {
    ClusterJspHelper clusterJspHelper = new ClusterJspHelper();
    ClusterStatus clusterStatus = clusterJspHelper
     .generateClusterHealthReport();
    assertNotNull("testClusterJspHelperReports ClusterStatus is null",
        clusterStatus);       
    DecommissionStatus decommissionStatus = clusterJspHelper
        .generateDecommissioningReport();
    assertNotNull("testClusterJspHelperReports DecommissionStatus is null",
        decommissionStatus);    
  }
}