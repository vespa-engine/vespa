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

import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static sun.misc.Unsafe.ARRAY_BYTE_BASE_OFFSET;

// forked from https://github.com/airlift/slice
public class TestXxHash64
{
    private static final long PRIME = 2654435761L;

    private final byte[] buffer = new byte[101];

    public TestXxHash64()
    {
        long value = PRIME;
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = (byte) (value >> 24);
            value *= value;
        }
    }

    @Test
    public void testSanity()
    {
        assertHash(0, buffer, 0, 0xEF46DB3751D8E999L);

        assertHash(0, buffer, 1, 0x4FCE394CC88952D8L);
        assertHash(PRIME, buffer, 1, 0x739840CB819FA723L);

        assertHash(0, buffer, 4, 0x9256E58AA397AEF1L);
        assertHash(PRIME, buffer, 4, 0x9D5FFDFB928AB4BL);

        assertHash(0, buffer, 8, 0xF74CB1451B32B8CFL);
        assertHash(PRIME, buffer, 8, 0x9C44B77FBCC302C5L);

        assertHash(0, buffer, 14, 0xCFFA8DB881BC3A3DL);
        assertHash(PRIME, buffer, 14, 0x5B9611585EFCC9CBL);

        assertHash(0, buffer, 32, 0xAF5753D39159EDEEL);
        assertHash(PRIME, buffer, 32, 0xDCAB9233B8CA7B0FL);

        assertHash(0, buffer, buffer.length, 0x0EAB543384F878ADL);
        assertHash(PRIME, buffer, buffer.length, 0xCAA65939306F1E21L);
    }

    @Test
    public void testMultipleLengths()
    {
        XXHash64 jpountz = XXHashFactory.fastestInstance().hash64();
        for (int i = 0; i < 20_000; i++) {
            byte[] data = new byte[i];
            long expected = jpountz.hash(data, 0, data.length, 0);
            assertHash(0, data, data.length, expected);
        }
    }

    private static void assertHash(long seed, byte[] data, int length, long expected)
    {
        assertEquals(hash(seed, data, length), expected);
    }

    private static long hash(long seed, byte[] data, int length)
    {
        return XxHash64.hash(seed, data, ARRAY_BYTE_BASE_OFFSET, length);
    }
}
