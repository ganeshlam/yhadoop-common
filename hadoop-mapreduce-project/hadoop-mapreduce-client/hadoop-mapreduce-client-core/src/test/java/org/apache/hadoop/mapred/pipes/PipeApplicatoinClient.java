package org.apache.hadoop.mapred.pipes;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import javax.crypto.SecretKey;

import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableUtils;
import org.apache.hadoop.mapreduce.security.SecureShuffleUtils;
import org.apache.hadoop.mapreduce.security.token.JobTokenSecretManager;

public class PipeApplicatoinClient {

  public static void main(String[] args) {
    PipeApplicatoinClient client = new PipeApplicatoinClient();
    client.binaryProtocolStub();
  }

  public void binaryProtocolStub() {
    try {

      int port = Integer
          .parseInt(System.getenv("mapreduce.pipes.command.port"));
      Socket socket = new Socket("localhost", port);
      InputStream input = socket.getInputStream();
      OutputStream output = socket.getOutputStream();
      System.out.println("port1:" + port);

      // try to read
      DataInputStream dataInput = new DataInputStream(input);

      int i = WritableUtils.readVInt(dataInput);

      String str = Text.readString(dataInput);

      Text.readString(dataInput);

      DataOutputStream dataout = new DataOutputStream(output);
      WritableUtils.writeVInt(dataout, 57);
      String s = createDigest("password".getBytes(), str);

      Text.writeString(dataout, s);
      System.out.println("security ok");
      // start

      i = WritableUtils.readVInt(dataInput);
      System.out.println("code:" + i);
      i = WritableUtils.readVInt(dataInput);

      System.out.println("version:" + i);

      i = WritableUtils.readVInt(dataInput);
      // get conf
      // should be MessageType.SET_JOB_CONF.code
      int j = WritableUtils.readVInt(dataInput);
      // array length
      j = WritableUtils.readVInt(dataInput);
      for (i = 0; i < j; i++) {
        String key = Text.readString(dataInput);
        String value = Text.readString(dataInput);
        System.out.println("key:" + key + " value:" + value);
      }

      // output code
      System.out.println("1");
      WritableUtils.writeVInt(dataout, 50);

      IntWritable iw = new IntWritable();
      iw.set(123);
      writeObject(iw, dataout);

      writeObject(new Text("value"), dataout);
      System.out.println("4");
      // STATUS

      WritableUtils.writeVInt(dataout, 52);
      Text.writeString(dataout, "PROGRESS");
      // progress
      WritableUtils.writeVInt(dataout, 53);
      dataout.writeFloat(50.5f);
      // register cunter
      WritableUtils.writeVInt(dataout, 55);
      // id
      WritableUtils.writeVInt(dataout, 1);
      Text.writeString(dataout, "group");
      Text.writeString(dataout, "name");
      // increment counter

      WritableUtils.writeVInt(dataout, 56);
      WritableUtils.writeVInt(dataout, 1);

      WritableUtils.writeVLong(dataout, 2);

      // done

      WritableUtils.writeVInt(dataout, 54);

      dataout.writeFloat(50.5f);

    } catch (Exception x) {
      x.printStackTrace();
    }
  }

  private String createDigest(byte[] password, String data) throws IOException {
    SecretKey key = JobTokenSecretManager.createSecretKey(password);

    return SecureShuffleUtils.hashFromString(data, key);

  }

  private void writeObject(Writable obj, DataOutputStream stream)
      throws IOException {
    // For Text and BytesWritable, encode them directly, so that they end up
    // in C++ as the natural translations.
    DataOutputBuffer buffer = new DataOutputBuffer();

    if (obj instanceof Text) {
      Text t = (Text) obj;
      int len = t.getLength();
      WritableUtils.writeVInt(stream, len);
      stream.write(t.getBytes(), 0, len);
    } else if (obj instanceof BytesWritable) {
      BytesWritable b = (BytesWritable) obj;
      int len = b.getLength();
      WritableUtils.writeVInt(stream, len);
      stream.write(b.getBytes(), 0, len);
    } else {
      buffer.reset();
      obj.write(buffer);
      int length = buffer.getLength();
      WritableUtils.writeVInt(stream, length);
      stream.write(buffer.getData(), 0, length);
    }
  }

}
