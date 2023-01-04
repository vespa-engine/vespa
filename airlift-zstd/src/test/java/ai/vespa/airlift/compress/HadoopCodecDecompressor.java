/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.vespa.airlift.compress;

import org.apache.hadoop.io.compress.CompressionCodec;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

public class HadoopCodecDecompressor
        implements Decompressor
{
    private final CompressionCodec codec;

    public HadoopCodecDecompressor(CompressionCodec codec)
    {
        this.codec = codec;
    }

    @Override
    public int decompress(byte[] input, int inputOffset, int inputLength, byte[] output, int outputOffset, int maxOutputLength)
            throws MalformedInputException
    {
        try (InputStream in = codec.createInputStream(new ByteArrayInputStream(input, inputOffset, inputLength))) {
            int bytesRead = 0;
            while (bytesRead < maxOutputLength) {
                int size = in.read(output, outputOffset + bytesRead, maxOutputLength - bytesRead);
                if (size < 0) {
                    break;
                }
                bytesRead += size;
            }

            if (in.read() >= 0) {
                throw new RuntimeException("All input was not consumed");
            }

            return bytesRead;
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void decompress(ByteBuffer input, ByteBuffer output)
            throws MalformedInputException
    {
        throw new UnsupportedOperationException("not yet implemented");
    }
}
