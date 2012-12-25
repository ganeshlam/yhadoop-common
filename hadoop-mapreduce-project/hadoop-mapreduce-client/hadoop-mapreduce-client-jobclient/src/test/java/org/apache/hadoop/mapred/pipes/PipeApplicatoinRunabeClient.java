package org.apache.hadoop.mapred.pipes;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import javax.crypto.SecretKey;

import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableUtils;
import org.apache.hadoop.mapreduce.security.SecureShuffleUtils;
import org.apache.hadoop.mapreduce.security.token.JobTokenSecretManager;

public class PipeApplicatoinRunabeClient {

  public static void main(String[] args) {
    PipeApplicatoinRunabeClient client = new PipeApplicatoinRunabeClient();
    client.binaryProtocolStub();
  }

  public void binaryProtocolStub() {
    Socket socket =null;
    try {

      int port = Integer 
          .parseInt(System.getenv("mapreduce.pipes.command.port"));
      
      java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
      
      
      socket = new Socket(addr.getHostName(), port);
      InputStream input = socket.getInputStream();
      OutputStream output = socket.getOutputStream();

      // try to read
      DataInputStream dataInput = new DataInputStream(input);

      int i = WritableUtils.readVInt(dataInput);

      String str = Text.readString(dataInput);

      Text.readString(dataInput);

      DataOutputStream dataout = new DataOutputStream(output);
      WritableUtils.writeVInt(dataout, 57);
      String s = createDigest("password".getBytes(), str);

      Text.writeString(dataout, s);

      // start

      i = WritableUtils.readVInt(dataInput);
      i = WritableUtils.readVInt(dataInput);

  // get conf
      // should be MessageType.SET_JOB_CONF.code
      i = WritableUtils.readVInt(dataInput);
          // array length

      int j = WritableUtils.readVInt(dataInput);
      for (i = 0; i < j; i++) {
        Text.readString(dataInput);
        i++;
        Text.readString(dataInput);
      }


// RUN_MAP.code
      //should be 3

      i = WritableUtils.readVInt(dataInput);
      
      TestPipeApplication.FakeSplit split= new TestPipeApplication.FakeSplit() ; 
      readObject(split, dataInput);
      i = WritableUtils.readVInt(dataInput);
      i = WritableUtils.readVInt(dataInput);
      
      //should be 2
      
      i = WritableUtils.readVInt(dataInput);
      s= Text.readString(dataInput);
      s= Text.readString(dataInput);
      
/*
      
      System.out.println("start translate:" );

      for (int k=0;k<20;k++){
        FloatWritable fw= new FloatWritable(k);
        fw.write(dataout);
        Text tx = new Text("k:"+k);
         tx.write(dataout);
      }
      System.out.println("finish translate:" );
   */
      
      // done
      WritableUtils.writeVInt(dataout, 54);
  
      dataout.flush();
      dataout.close();

    } catch (Exception x) {
      x.printStackTrace();
    }finally{
      if( socket!=null )
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

  private void readObject(Writable obj , DataInputStream inStream)  throws IOException {
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
