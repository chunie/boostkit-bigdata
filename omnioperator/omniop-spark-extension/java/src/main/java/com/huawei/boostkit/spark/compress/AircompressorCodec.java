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

package com.huawei.boostkit.spark.compress;

import io.airlift.compress.Compressor;
import io.airlift.compress.Decompressor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.EnumSet;

public class AircompressorCodec implements CompressionCodec {
    private final Compressor compressor;
    private final Decompressor decompressor;

    AircompressorCodec(Compressor compressor,Decompressor decompressor) {
        this.compressor = compressor;
        this.decompressor = decompressor;
    }

    // Thread local buffer
    private static final ThreadLocal<byte[]> threadBuffer =
            new ThreadLocal<byte[]>(){
                @Override
                protected byte[] initialValue() {
                    return null;
                }
            };

    protected static byte[] getBuffer(int size) {
        byte[] result = threadBuffer.get();
        if (result == null || result.length < size || result.length > size * 2) {
            result = new byte[size];
            threadBuffer.set(result);
        }
        return result;
    }

    @Override
    public boolean compress(ByteBuffer in,ByteBuffer out,
                            ByteBuffer overflow) throws IOException {
        int inBytes = in.remaining();
        // I should work on a patch for Snappy to support an overflow buffer
        // to prevent the extra buffer copy.
        byte[] compressed = getBuffer(compressor.maxCompressedLength(inBytes));
        int outBytes =
                compressor.compress(in.array(),in.arrayOffset() + in.position(),inBytes,
                        compressed,0,compressed.length);
        if (outBytes < inBytes) {
            int remaining = out.remaining();
            if(remaining >= outBytes) {
                System.arraycopy(compressed,0,out.array(),out.arrayOffset() +
                    out.position(),outBytes);
                out.position(out.position() + outBytes);
            } else {
                System.arraycopy(compressed,0,out.array(),out.arrayOffset() +
                    out.position(),remaining);
                out.position(out.limit());
                System.arraycopy(compressed,remaining,overflow.array(),
                    overflow.arrayOffset(),outBytes - remaining);
                overflow.position(outBytes - remaining);
            }
            return true;
        } else {
          return false;
        }
    }

    @Override
    public int decompress(byte[] input,int inputLength,byte[] output) throws IOException {
        int uncompressLen =
                decompressor.decompress(input, 0, inputLength,
                        output, 0, output.length);
        return uncompressLen;
    }

    @Override
    public CompressionCodec modify(EnumSet<Modifier> modifiers) {
        // snappy allows no modifications
        return this;
    }

    @Override
    public void reset() {
        // Nothing to do.
    }

    @Override
    public void close() {
        // Nothing to do.
    }
}
