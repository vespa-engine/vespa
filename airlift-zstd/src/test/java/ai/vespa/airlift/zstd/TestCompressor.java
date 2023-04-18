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
package ai.vespa.airlift.zstd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static sun.misc.Unsafe.ARRAY_BYTE_BASE_OFFSET;

@SuppressWarnings("proprietary")
public class TestCompressor
{
    @Test
    public void testMagic()
    {
        byte[] buffer = new byte[4];
        int address = ARRAY_BYTE_BASE_OFFSET;

        ZstdFrameCompressor.writeMagic(buffer, address, address + buffer.length);
        ZstdFrameDecompressor.verifyMagic(buffer, address, address + buffer.length);
    }

    @Test
    public void testMagicFailsWithSmallBuffer()
    {
        byte[] buffer = new byte[3];
        Throwable t = assertThrows(IllegalArgumentException.class, () -> {
                ZstdFrameCompressor.writeMagic(buffer, ARRAY_BYTE_BASE_OFFSET, ARRAY_BYTE_BASE_OFFSET + buffer.length);
            });
        assertTrue(t.getMessage().matches(".*buffer too small.*"));
    }

    @Test
    public void testFrameHeaderFailsWithSmallBuffer()
    {
        byte[] buffer = new byte[ZstdFrameCompressor.MAX_FRAME_HEADER_SIZE - 1];
        Throwable t = assertThrows(IllegalArgumentException.class, () -> {
                ZstdFrameCompressor.writeFrameHeader(buffer, ARRAY_BYTE_BASE_OFFSET, ARRAY_BYTE_BASE_OFFSET + buffer.length, 1000, 1024);
            });
        assertTrue(t.getMessage().matches(".*buffer too small.*"));
    }

    @Test
    public void testFrameHeader()
    {
        verifyFrameHeader(1, 1024, new FrameHeader(2, -1, 1, -1, true));
        verifyFrameHeader(256, 1024, new FrameHeader(3, -1, 256, -1, true));

        verifyFrameHeader(65536 + 256, 1024 + 128, new FrameHeader(6, 1152, 65536 + 256, -1, true));
        verifyFrameHeader(65536 + 256, 1024 + 128 * 2, new FrameHeader(6, 1024 + 128 * 2, 65536 + 256, -1, true));
        verifyFrameHeader(65536 + 256, 1024 + 128 * 3, new FrameHeader(6, 1024 + 128 * 3, 65536 + 256, -1, true));
        verifyFrameHeader(65536 + 256, 1024 + 128 * 4, new FrameHeader(6, 1024 + 128 * 4, 65536 + 256, -1, true));
        verifyFrameHeader(65536 + 256, 1024 + 128 * 5, new FrameHeader(6, 1024 + 128 * 5, 65536 + 256, -1, true));
        verifyFrameHeader(65536 + 256, 1024 + 128 * 6, new FrameHeader(6, 1024 + 128 * 6, 65536 + 256, -1, true));
        verifyFrameHeader(65536 + 256, 1024 + 128 * 7, new FrameHeader(6, 1024 + 128 * 7, 65536 + 256, -1, true));
        verifyFrameHeader(65536 + 256, 1024 + 128 * 8, new FrameHeader(6, 1024 + 128 * 8, 65536 + 256, -1, true));

        verifyFrameHeader(65536 + 256, 2048, new FrameHeader(6, 2048, 65536 + 256, -1, true));

        verifyFrameHeader(Integer.MAX_VALUE, 1024, new FrameHeader(6, 1024, Integer.MAX_VALUE, -1, true));
    }

    @Test
    public void testMinimumWindowSize()
    {
        byte[] buffer = new byte[ZstdFrameCompressor.MAX_FRAME_HEADER_SIZE];
        int address = ARRAY_BYTE_BASE_OFFSET;

        Throwable t = assertThrows(IllegalArgumentException.class, () -> {
                ZstdFrameCompressor.writeFrameHeader(buffer, address, address + buffer.length, 2000, 1023);
            });
        assertTrue(t.getMessage().matches("Minimum window size is 1024"));
    }

    @Test
    public void testWindowSizePrecision()
    {
        byte[] buffer = new byte[ZstdFrameCompressor.MAX_FRAME_HEADER_SIZE];
        int address = ARRAY_BYTE_BASE_OFFSET;

        Throwable t = assertThrows(IllegalArgumentException.class, () -> {
                ZstdFrameCompressor.writeFrameHeader(buffer, address, address + buffer.length, 2000, 1025);
            });
        assertTrue(t.getMessage().matches("\\QWindow size of magnitude 2^10 must be multiple of 128\\E"));
    }

    private void verifyFrameHeader(int inputSize, int windowSize, FrameHeader expected)
    {
        byte[] buffer = new byte[ZstdFrameCompressor.MAX_FRAME_HEADER_SIZE];
        int address = ARRAY_BYTE_BASE_OFFSET;

        int size = ZstdFrameCompressor.writeFrameHeader(buffer, address, address + buffer.length, inputSize, windowSize);

        assertEquals(size, expected.headerSize);

        FrameHeader actual = ZstdFrameDecompressor.readFrameHeader(buffer, address, address + buffer.length);
        assertEquals(actual, expected);
    }
}
