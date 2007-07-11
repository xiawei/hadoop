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

package org.apache.hadoop.fs;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Checksum;

/**
 * This is a generic output stream for generating checksums for
 * data before it is written to the underlying stream
 */

abstract public class FSOutputSummer extends OutputStream {
  // data checksum
  private Checksum sum;
  // internal buffer for storing data before it is checksumed
  private byte buf[];
  // internal buffer for storing checksum
  private byte checksum[];
  // The number of valid bytes in the buffer.
  private int count;
  
  protected FSOutputSummer(Checksum sum, int maxChunkSize, int checksumSize) {
    this.sum = sum;
    this.buf = new byte[maxChunkSize];
    this.checksum = new byte[checksumSize];
    this.count = 0;
  }
  
  /* write the data chunk in <code>b</code> staring at <code>offset</code> with
   * a length of <code>len</code>, and its checksum
   */
  protected abstract void writeChunk(byte[] b, int offset, int len, byte[] checksum)
  throws IOException;

  /** Write one byte */
  public synchronized void write(int b) throws IOException {
    sum.update(b);
    buf[count++] = (byte)b;
    if(count == buf.length) {
      flushBuffer();
    }
  }

  /**
   * Writes <code>len</code> bytes from the specified byte array 
   * starting at offset <code>off</code> and generate a checksum for
   * each data chunk.
   *
   * <p> This method stores bytes from the given array into this
   * stream's buffer before it gets checksumed. The buffer gets checksumed 
   * and flushed to the underlying output stream when all data 
   * in a checksum chunk are in the buffer.  If the buffer is empty and
   * requested length is at least as large as the size of next checksum chunk
   * size, this method will checksum and write the chunk directly 
   * to the underlying output stream.  Thus it avoids uneccessary data copy.
   *
   * @param      b     the data.
   * @param      off   the start offset in the data.
   * @param      len   the number of bytes to write.
   * @exception  IOException  if an I/O error occurs.
   */
  public synchronized void write(byte b[], int off, int len)
  throws IOException {
    if (off < 0 || len < 0 || off > b.length - len) {
      throw new ArrayIndexOutOfBoundsException();
    }

    for (int n=0;n<len;n+=write1(b, off+n, len-n)) {
    }
  }
  
  /*
   * Write a portion of an array, flushing to the underlying
   * stream at most once if necessary.
   */

  private int write1(byte b[], int off, int len) throws IOException {
    if(count==0 && len>=buf.length) {
      // local buffer is empty and user data has one chunk
      // checksum and output data
      sum.update(b, off, buf.length);
      writeChecksumChunk(b, off, buf.length);
      return buf.length;
    }
    
    // copy user data to local buffer
    int bytesToCopy = buf.length-count;
    bytesToCopy = (len<bytesToCopy) ? len : bytesToCopy;
    sum.update(b, off, bytesToCopy);
    System.arraycopy(b, off, buf, count, bytesToCopy);
    count += bytesToCopy;
    if (count == buf.length) {
      // local buffer is full
      flushBuffer();
    } 
    return bytesToCopy;
  }

  /* Forces any buffered output bytes to be checksumed and written out to
   * the underlying output stream. 
   */
  protected synchronized void flushBuffer() throws IOException {
    if(count != 0) {
      writeChecksumChunk(buf, 0, count);
      count = 0;
    }
  }
  
  /* Generate checksum for the data chunk and output data chunk & checksum
   * to the underlying output stream
   */
  private void writeChecksumChunk(byte b[], int off, int len)
  throws IOException {
    int tempChecksum = (int)sum.getValue();
    sum.reset();
    
    checksum[0] = (byte)((tempChecksum >>> 24) & 0xFF);
    checksum[1] = (byte)((tempChecksum >>> 16) & 0xFF);
    checksum[2] = (byte)((tempChecksum >>>  8) & 0xFF);
    checksum[3] = (byte)((tempChecksum >>>  0) & 0xFF);

    writeChunk(b, off, len, checksum);
  }
}
