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
package org.apache.hadoop.io.compress.zlib;

import static org.junit.Assert.*;
import static org.junit.Assume.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.CommonConfigurationKeys;
import org.apache.hadoop.io.DataInputBuffer;
import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.Compressor;
import org.apache.hadoop.io.compress.Decompressor;
import org.apache.hadoop.io.compress.DecompressorStream;
import org.apache.hadoop.io.compress.GzipCodec;
import org.apache.hadoop.io.compress.zlib.ZlibCompressor.CompressionLevel;
import org.apache.hadoop.io.compress.zlib.ZlibCompressor.CompressionStrategy;
import org.apache.hadoop.util.NativeCodeLoader;
import org.apache.hadoop.util.ReflectionUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class TestZlibCompressorDecompressor {

  @Before
  public void before() {
    assumeTrue(NativeCodeLoader.isNativeCodeLoaded());
  }

  interface ByteGenerator {
    byte[] generate(int size);
  }

  // return char 97 - 122
  private static final ByteGenerator TEST_CHAR_TO_BYTE_GENERATOR = new ByteGenerator() {
    private final Random random = new Random();
    private byte[] data;
    int start = 97;

    @Override
    public byte[] generate(int size) {
      data = new byte[size];
      for (int i = 0; i < size; i++)
        data[i] = (byte) (start + random.nextInt(22));

      return data;
    }
  };

  @Test
  public void testZlibCompressorDecompressorWithConfiguration() {
    Configuration conf = new Configuration();
    conf.setBoolean(CommonConfigurationKeys.IO_NATIVE_LIB_AVAILABLE_KEY, true);
    if (ZlibFactory.isNativeZlibLoaded(conf)) {
      byte[] rawData;
      int tryNumber = 5;
      int BYTE_SIZE = 10 * 1024;
      Compressor zlibCompressor = ZlibFactory.getZlibCompressor(conf);
      Decompressor zlibDecompressor = ZlibFactory.getZlibDecompressor(conf);
      rawData = TEST_CHAR_TO_BYTE_GENERATOR.generate(BYTE_SIZE);
      try {
        for (int i = 0; i < tryNumber; i++)
          compressDecompressZlib(rawData, (ZlibCompressor) zlibCompressor,
              (ZlibDecompressor) zlibDecompressor);
        zlibCompressor.reinit(conf);
      } catch (Exception ex) {
        fail("testZlibCompressorDecompressorWithConfiguration ex error " + ex);
      }
    } else {
      assertFalse("ZlibFactory is using native libs against request",
          ZlibFactory.isNativeZlibLoaded(conf));
    }
  }

  @Test
  public void testZlibCompressDecompress() {
    byte[] rawData = null;
    int rawDataSize = 0;
    rawDataSize = 1024 * 64;
    rawData = TEST_CHAR_TO_BYTE_GENERATOR.generate(rawDataSize);
    try {
      ZlibCompressor compressor = new ZlibCompressor();
      ZlibDecompressor decompressor = new ZlibDecompressor();
      assertFalse("testZlibCompressDecompress finished error",
          compressor.finished());
      compressor.setInput(rawData, 0, rawData.length);
      assertTrue("testZlibCompressDecompress getBytesRead before error",
          compressor.getBytesRead() == 0);
      compressor.finish();

      byte[] compressedResult = new byte[rawDataSize];
      int cSize = compressor.compress(compressedResult, 0, rawDataSize);
      assertTrue("testZlibCompressDecompress getBytesRead ather error",
          compressor.getBytesRead() == rawDataSize);
      assertTrue(
          "testZlibCompressDecompress compressed size no less then original size",
          cSize < rawDataSize);
      decompressor.setInput(compressedResult, 0, cSize);
      byte[] decompressedBytes = new byte[rawDataSize];
      decompressor.decompress(decompressedBytes, 0, decompressedBytes.length);
      assertArrayEquals("testZlibCompressDecompress arrays not equals ",
          rawData, decompressedBytes);
      compressor.reset();
      decompressor.reset();
    } catch (IOException ex) {
      fail("testZlibCompressDecompress ex !!!");
    }
  }

  @Test
  public void testZlibCompressorDecompressorSetDictionary() {
    Configuration conf = new Configuration();
    conf.setBoolean(CommonConfigurationKeys.IO_NATIVE_LIB_AVAILABLE_KEY, true);
    if (ZlibFactory.isNativeZlibLoaded(conf)) {
      Compressor zlibCompressor = ZlibFactory.getZlibCompressor(conf);
      Decompressor zlibDecompressor = ZlibFactory.getZlibDecompressor(conf);

      checkSetDictionaryNullPointerException(zlibCompressor);
      checkSetDictionaryNullPointerException(zlibDecompressor);

      checkSetDictionaryArrayIndexOutOfBoundsException(zlibDecompressor);
      checkSetDictionaryArrayIndexOutOfBoundsException(zlibCompressor);
    } else {
      assertFalse("ZlibFactory is using native libs against request",
          ZlibFactory.isNativeZlibLoaded(conf));
    }
  }

  @Test
  public void testZlibFactory() {
    Configuration cfg = new Configuration();

    assertTrue("testZlibFactory compression level error !!!",
        CompressionLevel.DEFAULT_COMPRESSION == ZlibFactory
            .getCompressionLevel(cfg));

    assertTrue("testZlibFactory compression strategy error !!!",
        CompressionStrategy.DEFAULT_STRATEGY == ZlibFactory
            .getCompressionStrategy(cfg));

    ZlibFactory.setCompressionLevel(cfg, CompressionLevel.BEST_COMPRESSION);
    assertTrue("testZlibFactory compression strategy error !!!",
        CompressionLevel.BEST_COMPRESSION == ZlibFactory
            .getCompressionLevel(cfg));

    ZlibFactory.setCompressionStrategy(cfg, CompressionStrategy.FILTERED);
    assertTrue("testZlibFactory compression strategy error !!!",
        CompressionStrategy.FILTERED == ZlibFactory.getCompressionStrategy(cfg));
  }

  private boolean checkSetDictionaryNullPointerException(
      Decompressor decompressor) {
    try {
      decompressor.setDictionary(null, 0, 1);
    } catch (NullPointerException ex) {
      return true;
    } catch (Exception ex) {
    }
    return false;
  }

  private boolean checkSetDictionaryNullPointerException(Compressor compressor) {
    try {
      compressor.setDictionary(null, 0, 1);
    } catch (NullPointerException ex) {
      return true;
    } catch (Exception ex) {
    }
    return false;
  }

  private boolean checkSetDictionaryArrayIndexOutOfBoundsException(
      Compressor compressor) {
    try {
      compressor.setDictionary(new byte[] { (byte) 0 }, 0, -1);
    } catch (ArrayIndexOutOfBoundsException e) {
      return true;
    } catch (Exception e) {
    }
    return false;
  }

  private boolean checkSetDictionaryArrayIndexOutOfBoundsException(
      Decompressor decompressor) {
    try {
      decompressor.setDictionary(new byte[] { (byte) 0 }, 0, -1);
    } catch (ArrayIndexOutOfBoundsException e) {
      return true;
    } catch (Exception e) {
    }
    return false;
  }

  private byte[] compressDecompressZlib(byte[] rawData,
      ZlibCompressor zlibCompressor, ZlibDecompressor zlibDecompressor)
      throws IOException {
    int cSize = 0;
    byte[] compressedByte = new byte[rawData.length];
    byte[] decompressedRawData = new byte[rawData.length];
    zlibCompressor.setInput(rawData, 0, rawData.length);
    zlibCompressor.finish();
    while (!zlibCompressor.finished()) {
      cSize = zlibCompressor.compress(compressedByte, 0, compressedByte.length);
    }
    zlibCompressor.reset();

    assertTrue(zlibDecompressor.getBytesWritten() == 0);
    assertTrue(zlibDecompressor.getBytesRead() == 0);
    assertTrue(zlibDecompressor.needsInput());
    zlibDecompressor.setInput(compressedByte, 0, cSize);
    assertFalse(zlibDecompressor.needsInput());
    while (!zlibDecompressor.finished()) {
      zlibDecompressor.decompress(decompressedRawData, 0,
          decompressedRawData.length);
    }
    assertTrue(zlibDecompressor.getBytesWritten() == rawData.length);
    assertTrue(zlibDecompressor.getBytesRead() == cSize);
    zlibDecompressor.reset();
    assertTrue(zlibDecompressor.getRemaining() == 0);
    assertArrayEquals(
        "testZlibCompressorDecompressorWithConfiguration array equals error",
        rawData, decompressedRawData);

    return decompressedRawData;
  }

  @Test
  public void testBuiltInGzipDecompressorExceptions() {
    BuiltInGzipDecompressor decompresser = new BuiltInGzipDecompressor();
    try {
      decompresser.setInput(null, 0, 1);
    } catch (NullPointerException ex) {
      // expected
    } catch (Exception ex) {
      fail("testBuiltInGzipDecompressorExceptions npe error " + ex);
    }

    try {
      decompresser.setInput(new byte[] { 0 }, 0, -1);
    } catch (ArrayIndexOutOfBoundsException ex) {
      // expected
    } catch (Exception ex) {
      fail("testBuiltInGzipDecompressorExceptions aioob error" + ex);
    }        
    
    assertTrue("decompresser.getBytesRead error",
        decompresser.getBytesRead() == 0);
    assertTrue("decompresser.getRemaining error",
        decompresser.getRemaining() == 0);
    decompresser.reset();
    decompresser.end();

    InputStream decompStream = null;
    try {
      // invalid 0 and 1 bytes , must be 31, -117
      int buffSize = 1 * 1024;
      byte buffer[] = new byte[buffSize];
      Decompressor decompressor = new BuiltInGzipDecompressor();
      DataInputBuffer gzbuf = new DataInputBuffer();
      decompStream = new DecompressorStream(gzbuf, decompressor);
      gzbuf.reset(new byte[] { 0, 0, 1, 1, 1, 1, 11, 1, 1, 1, 1 }, 11);
      decompStream.read(buffer);
    } catch (IOException ioex) {
      // expected
    } catch (Exception ex) {
      fail("invalid 0 and 1 byte in gzip stream" + ex);
    }

    // invalid 2 byte, must be 8
    try {
      int buffSize = 1 * 1024;
      byte buffer[] = new byte[buffSize];
      Decompressor decompressor = new BuiltInGzipDecompressor();
      DataInputBuffer gzbuf = new DataInputBuffer();
      decompStream = new DecompressorStream(gzbuf, decompressor);
      gzbuf.reset(new byte[] { 31, -117, 7, 1, 1, 1, 1, 11, 1, 1, 1, 1 }, 11);
      decompStream.read(buffer);
    } catch (IOException ioex) {
      // expected
    } catch (Exception ex) {
      fail("invalid 2 byte in gzip stream" + ex);
    }

    try {
      int buffSize = 1 * 1024;
      byte buffer[] = new byte[buffSize];
      Decompressor decompressor = new BuiltInGzipDecompressor();
      DataInputBuffer gzbuf = new DataInputBuffer();
      decompStream = new DecompressorStream(gzbuf, decompressor);
      gzbuf.reset(new byte[] { 31, -117, 8, -32, 1, 1, 1, 11, 1, 1, 1, 1 }, 11);
      decompStream.read(buffer);
    } catch (IOException ioex) {
      // expected
    } catch (Exception ex) {
      fail("invalid 3 byte in gzip stream" + ex);
    }
    try {
      int buffSize = 1 * 1024;
      byte buffer[] = new byte[buffSize];
      Decompressor decompressor = new BuiltInGzipDecompressor();
      DataInputBuffer gzbuf = new DataInputBuffer();
      decompStream = new DecompressorStream(gzbuf, decompressor);
      gzbuf.reset(new byte[] { 31, -117, 8, 4, 1, 1, 1, 11, 1, 1, 1, 1 }, 11);
      decompStream.read(buffer);
    } catch (IOException ioex) {
      // expected
    } catch (Exception ex) {
      fail("invalid 3 byte make hasExtraField" + ex);
    }
  }  
}
