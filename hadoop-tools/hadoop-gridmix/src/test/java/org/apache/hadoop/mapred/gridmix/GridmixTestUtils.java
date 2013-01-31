package org.apache.hadoop.mapred.gridmix;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.mapred.MiniMRClientCluster;
import org.apache.hadoop.mapred.MiniMRClientClusterFactory;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapreduce.server.jobtracker.JTConfig;
import org.apache.hadoop.conf.Configuration;

import java.io.IOException;

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
public class GridmixTestUtils {
  private static final Log LOG = LogFactory.getLog(GridmixTestUtils.class);
  static final Path DEST = new Path("/gridmix");
  static FileSystem dfs = null;
  static MiniDFSCluster dfsCluster = null;
  static MiniMRClientCluster mrvl=null; 

  public static void initCluster(Class<?> caller) throws IOException {
    Configuration conf = new Configuration();
    conf.set("mapred.queue.names", "default,q1,q2");
    conf.set("yarn.scheduler.capacity.root.queues", "default");
    conf.set("yarn.scheduler.capacity.root.default.capacity", "100.0");

    dfsCluster = new  MiniDFSCluster.Builder(conf).numDataNodes(1).format(true).build();//  MiniDFSCluster(conf, 3, true, null);
    dfs = dfsCluster.getFileSystem();
    conf.set(JTConfig.JT_RETIREJOBS, "false");
    mrvl=  MiniMRClientClusterFactory.create(caller, 2, conf);
  }

  public static void shutdownCluster() throws IOException {
    if (mrvl != null) {
      mrvl.stop();
    }
    if (dfsCluster != null) {
      dfsCluster.shutdown();
    }
  }

  
  /**
   * Methods to generate the home directory for dummy users.
   *
   * @param conf
   */
  public static void createHomeAndStagingDirectory(String user, Configuration conf) {
    try {
      FileSystem fs = dfsCluster.getFileSystem();
      String path = "/user/" + user;
      Path homeDirectory = new Path(path);
      if(fs.exists(homeDirectory)) {
        fs.delete(homeDirectory,true);
      }
      LOG.info("Creating Home directory : " + homeDirectory);
      fs.mkdirs(homeDirectory);
      changePermission(user,homeDirectory, fs);
      Path stagingArea = 
        new Path(conf.get("mapreduce.jobtracker.staging.root.dir",
                          "/tmp/hadoop/mapred/staging"));
      LOG.info("Creating Staging root directory : " + stagingArea);
      fs.mkdirs(stagingArea);
      fs.setPermission(stagingArea, new FsPermission((short) 0777));
    } catch (IOException ioe) {
      ioe.printStackTrace();
    }
  }

  static void changePermission(String user, Path homeDirectory, FileSystem fs)
    throws IOException {
    fs.setOwner(homeDirectory, user, "");
  }
}
