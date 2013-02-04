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
package org.apache.hadoop.mapred.pipes;

import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableUtils;
import org.apache.hadoop.mapreduce.security.SecureShuffleUtils;
import org.apache.hadoop.mapreduce.security.token.JobTokenSecretManager;

import javax.crypto.SecretKey;
import java.io.*;
import java.net.Socket;

public class PipeApplicationRunnableStub {

  public static void main(String[] args) {
    PipeApplicationRunnableStub client = new PipeApplicationRunnableStub();
    client.binaryProtocolStub();
  }

  public void binaryProtocolStub() {
    Socket socket = null;
    try {

      int port = Integer
              .parseInt(System.getenv("mapreduce.pipes.command.port"));

      java.net.InetAddress address = java.net.InetAddress.getLocalHost();


      socket = new Socket(address.getHostName(), port);
      InputStream input = socket.getInputStream();
      OutputStream output = socket.getOutputStream();

      // try to read
      DataInputStream dataInput = new DataInputStream(input);

      WritableUtils.readVInt(dataInput);

      String str = Text.readString(dataInput);

      Text.readString(dataInput);

      DataOutputStream dataOut = new DataOutputStream(output);
      WritableUtils.writeVInt(dataOut, 57);
      String s = createDigest("password".getBytes(), str);

      Text.writeString(dataOut, s);


      // start

      WritableUtils.readVInt(dataInput);
      WritableUtils.readVInt(dataInput);

      // get conf
      // should be MessageType.SET_JOB_CONF.code
      WritableUtils.readVInt(dataInput);
      // array length

      int j = WritableUtils.readVInt(dataInput);
      for (int i = 0; i < j; i++) {
        Text.readString(dataInput);
        i++;
        Text.readString(dataInput);
      }


// RUN_MAP.code
      //should be 3

      WritableUtils.readVInt(dataInput);
      TestPipeApplication.FakeSplit split = new TestPipeApplication.FakeSplit();
      readObject(split, dataInput);

      WritableUtils.readVInt(dataInput);
      WritableUtils.readVInt(dataInput);

      //should be 2
      WritableUtils.readVInt(dataInput);
      Text.readString(dataInput);
      Text.readString(dataInput);

      // done
      WritableUtils.writeVInt(dataOut, 54);

      dataOut.flush();
      dataOut.close();

    } catch (Exception x) {
      x.printStackTrace();
    } finally {
      if (socket != null)
        try {
          socket.close();
        } catch (IOException e) {
          e.printStackTrace();
        }

    }
  }

  private String createDigest(byte[] password, String data) throws IOException {
    SecretKey key = JobTokenSecretManager.createSecretKey(password);

    return SecureShuffleUtils.hashFromString(data, key);

  }

  private void readObject(Writable obj, DataInputStream inStream) throws IOException {
    int numBytes = WritableUtils.readVInt(inStream);
    byte[] buffer;
    // For BytesWritable and Text, use the specified length to set the length
    // this causes the "obvious" translations to work. So that if you emit
    // a string "abc" from C++, it shows up as "abc".
    if (obj instanceof BytesWritable) {
      buffer = new byte[numBytes];
      inStream.readFully(buffer);
      ((BytesWritable) obj).set(buffer, 0, numBytes);
    } else if (obj instanceof Text) {
      buffer = new byte[numBytes];
      inStream.readFully(buffer);
      ((Text) obj).set(buffer);
    } else {
      obj.readFields(inStream);
    }
  }


}
