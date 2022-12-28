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

import ai.vespa.airlift.compress.MalformedInputException;

import static ai.vespa.airlift.zstd.Constants.SIZE_OF_SHORT;
import static ai.vespa.airlift.zstd.UnsafeUtil.UNSAFE;

final class Util
{
    private Util()
    {
    }

    public static int highestBit(int value)
    {
        return 31 - Integer.numberOfLeadingZeros(value);
    }

    public static boolean isPowerOf2(int value)
    {
        return (value & (value - 1)) == 0;
    }

    public static int mask(int bits)
    {
        return (1 << bits) - 1;
    }

    public static void verify(boolean condition, long offset, String reason)
    {
        if (!condition) {
            throw new MalformedInputException(offset, reason);
        }
    }

    public static void checkArgument(boolean condition, String reason)
    {
        if (!condition) {
            throw new IllegalArgumentException(reason);
        }
    }

    public static void checkState(boolean condition, String reason)
    {
        if (!condition) {
            throw new IllegalStateException(reason);
        }
    }

    public static MalformedInputException fail(long offset, String reason)
    {
        throw new MalformedInputException(offset, reason);
    }

    public static int cycleLog(int hashLog, CompressionParameters.Strategy strategy)
    {
        int cycleLog = hashLog;
        if (strategy == CompressionParameters.Strategy.BTLAZY2 || strategy == CompressionParameters.Strategy.BTOPT || strategy == CompressionParameters.Strategy.BTULTRA) {
            cycleLog = hashLog - 1;
        }
        return cycleLog;
    }

    public static void put24BitLittleEndian(Object outputBase, long outputAddress, int value)
    {
        UNSAFE.putShort(outputBase, outputAddress, (short) value);
        UNSAFE.putByte(outputBase, outputAddress + SIZE_OF_SHORT, (byte) (value >>> Short.SIZE));
    }

    // provides the minimum logSize to safely represent a distribution
    public static int minTableLog(int inputSize, int maxSymbolValue)
    {
        if (inputSize <= 1) {
            throw new IllegalArgumentException("Not supported. RLE should be used instead"); // TODO
        }

        int minBitsSrc = highestBit((inputSize - 1)) + 1;
        int minBitsSymbols = highestBit(maxSymbolValue) + 2;
        return Math.min(minBitsSrc, minBitsSymbols);
    }
}
