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

import static ai.vespa.airlift.zstd.BitInputStream.peekBits;
import static ai.vespa.airlift.zstd.Constants.SIZE_OF_INT;
import static ai.vespa.airlift.zstd.Constants.SIZE_OF_LONG;
import static ai.vespa.airlift.zstd.Constants.SIZE_OF_SHORT;
import static ai.vespa.airlift.zstd.UnsafeUtil.UNSAFE;
import static ai.vespa.airlift.zstd.Util.checkArgument;
import static ai.vespa.airlift.zstd.Util.verify;
import static sun.misc.Unsafe.ARRAY_BYTE_BASE_OFFSET;

class FiniteStateEntropy
{
    public static final int MAX_SYMBOL = 255;
    public static final int MAX_TABLE_LOG = 12;
    public static final int MIN_TABLE_LOG = 5;

    private static final int[] REST_TO_BEAT = new int[] {0, 473195, 504333, 520860, 550000, 700000, 750000, 830000};
    private static final short UNASSIGNED = -2;

    private FiniteStateEntropy()
    {
    }

    public static int decompress(FiniteStateEntropy.Table table, final Object inputBase, final long inputAddress, final long inputLimit, byte[] outputBuffer)
    {
        final Object outputBase = outputBuffer;
        final long outputAddress = ARRAY_BYTE_BASE_OFFSET;
        final long outputLimit = outputAddress + outputBuffer.length;

        long input = inputAddress;
        long output = outputAddress;

        // initialize bit stream
        BitInputStream.Initializer initializer = new BitInputStream.Initializer(inputBase, input, inputLimit);
        initializer.initialize();
        int bitsConsumed = initializer.getBitsConsumed();
        long currentAddress = initializer.getCurrentAddress();
        long bits = initializer.getBits();

        // initialize first FSE stream
        int state1 = (int) peekBits(bitsConsumed, bits, table.log2Size);
        bitsConsumed += table.log2Size;

        BitInputStream.Loader loader = new BitInputStream.Loader(inputBase, input, currentAddress, bits, bitsConsumed);
        loader.load();
        bits = loader.getBits();
        bitsConsumed = loader.getBitsConsumed();
        currentAddress = loader.getCurrentAddress();

        // initialize second FSE stream
        int state2 = (int) peekBits(bitsConsumed, bits, table.log2Size);
        bitsConsumed += table.log2Size;

        loader = new BitInputStream.Loader(inputBase, input, currentAddress, bits, bitsConsumed);
        loader.load();
        bits = loader.getBits();
        bitsConsumed = loader.getBitsConsumed();
        currentAddress = loader.getCurrentAddress();

        byte[] symbols = table.symbol;
        byte[] numbersOfBits = table.numberOfBits;
        int[] newStates = table.newState;

        // decode 4 symbols per loop
        while (output <= outputLimit - 4) {
            int numberOfBits;

            UNSAFE.putByte(outputBase, output, symbols[state1]);
            numberOfBits = numbersOfBits[state1];
            state1 = (int) (newStates[state1] + peekBits(bitsConsumed, bits, numberOfBits));
            bitsConsumed += numberOfBits;

            UNSAFE.putByte(outputBase, output + 1, symbols[state2]);
            numberOfBits = numbersOfBits[state2];
            state2 = (int) (newStates[state2] + peekBits(bitsConsumed, bits, numberOfBits));
            bitsConsumed += numberOfBits;

            UNSAFE.putByte(outputBase, output + 2, symbols[state1]);
            numberOfBits = numbersOfBits[state1];
            state1 = (int) (newStates[state1] + peekBits(bitsConsumed, bits, numberOfBits));
            bitsConsumed += numberOfBits;

            UNSAFE.putByte(outputBase, output + 3, symbols[state2]);
            numberOfBits = numbersOfBits[state2];
            state2 = (int) (newStates[state2] + peekBits(bitsConsumed, bits, numberOfBits));
            bitsConsumed += numberOfBits;

            output += SIZE_OF_INT;

            loader = new BitInputStream.Loader(inputBase, input, currentAddress, bits, bitsConsumed);
            boolean done = loader.load();
            bitsConsumed = loader.getBitsConsumed();
            bits = loader.getBits();
            currentAddress = loader.getCurrentAddress();
            if (done) {
                break;
            }
        }

        while (true) {
            verify(output <= outputLimit - 2, input, "Output buffer is too small");
            UNSAFE.putByte(outputBase, output++, symbols[state1]);
            int numberOfBits = numbersOfBits[state1];
            state1 = (int) (newStates[state1] + peekBits(bitsConsumed, bits, numberOfBits));
            bitsConsumed += numberOfBits;

            loader = new BitInputStream.Loader(inputBase, input, currentAddress, bits, bitsConsumed);
            loader.load();
            bitsConsumed = loader.getBitsConsumed();
            bits = loader.getBits();
            currentAddress = loader.getCurrentAddress();

            if (loader.isOverflow()) {
                UNSAFE.putByte(outputBase, output++, symbols[state2]);
                break;
            }

            verify(output <= outputLimit - 2, input, "Output buffer is too small");
            UNSAFE.putByte(outputBase, output++, symbols[state2]);
            int numberOfBits1 = numbersOfBits[state2];
            state2 = (int) (newStates[state2] + peekBits(bitsConsumed, bits, numberOfBits1));
            bitsConsumed += numberOfBits1;

            loader = new BitInputStream.Loader(inputBase, input, currentAddress, bits, bitsConsumed);
            loader.load();
            bitsConsumed = loader.getBitsConsumed();
            bits = loader.getBits();
            currentAddress = loader.getCurrentAddress();

            if (loader.isOverflow()) {
                UNSAFE.putByte(outputBase, output++, symbols[state1]);
                break;
            }
        }

        return (int) (output - outputAddress);
    }

    public static int compress(Object outputBase, long outputAddress, int outputSize, byte[] input, int inputSize, FseCompressionTable table)
    {
        return compress(outputBase, outputAddress, outputSize, input, ARRAY_BYTE_BASE_OFFSET, inputSize, table);
    }

    public static int compress(Object outputBase, long outputAddress, int outputSize, Object inputBase, long inputAddress, int inputSize, FseCompressionTable table)
    {
        checkArgument(outputSize >= SIZE_OF_LONG, "Output buffer too small");

        final long start = inputAddress;
        final long inputLimit = start + inputSize;

        long input = inputLimit;

        if (inputSize <= 2) {
            return 0;
        }

        BitOutputStream stream = new BitOutputStream(outputBase, outputAddress, outputSize);

        int state1;
        int state2;

        if ((inputSize & 1) != 0) {
            input--;
            state1 = table.begin(UNSAFE.getByte(inputBase, input));

            input--;
            state2 = table.begin(UNSAFE.getByte(inputBase, input));

            input--;
            state1 = table.encode(stream, state1, UNSAFE.getByte(inputBase, input));

            stream.flush();
        }
        else {
            input--;
            state2 = table.begin(UNSAFE.getByte(inputBase, input));

            input--;
            state1 = table.begin(UNSAFE.getByte(inputBase, input));
        }

        // join to mod 4
        inputSize -= 2;

        if ((SIZE_OF_LONG * 8 > MAX_TABLE_LOG * 4 + 7) && (inputSize & 2) != 0) {  /* test bit 2 */
            input--;
            state2 = table.encode(stream, state2, UNSAFE.getByte(inputBase, input));

            input--;
            state1 = table.encode(stream, state1, UNSAFE.getByte(inputBase, input));

            stream.flush();
        }

        // 2 or 4 encoding per loop
        while (input > start) {
            input--;
            state2 = table.encode(stream, state2, UNSAFE.getByte(inputBase, input));

            if (SIZE_OF_LONG * 8 < MAX_TABLE_LOG * 2 + 7) {
                stream.flush();
            }

            input--;
            state1 = table.encode(stream, state1, UNSAFE.getByte(inputBase, input));

            if (SIZE_OF_LONG * 8 > MAX_TABLE_LOG * 4 + 7) {
                input--;
                state2 = table.encode(stream, state2, UNSAFE.getByte(inputBase, input));

                input--;
                state1 = table.encode(stream, state1, UNSAFE.getByte(inputBase, input));
            }

            stream.flush();
        }

        table.finish(stream, state2);
        table.finish(stream, state1);

        return stream.close();
    }

    public static int optimalTableLog(int maxTableLog, int inputSize, int maxSymbol)
    {
        if (inputSize <= 1) {
            throw new IllegalArgumentException(); // not supported. Use RLE instead
        }

        int result = maxTableLog;

        result = Math.min(result, Util.highestBit((inputSize - 1)) - 2); // we may be able to reduce accuracy if input is small

        // Need a minimum to safely represent all symbol values
        result = Math.max(result, Util.minTableLog(inputSize, maxSymbol));

        result = Math.max(result, MIN_TABLE_LOG);
        result = Math.min(result, MAX_TABLE_LOG);

        return result;
    }

    public static int normalizeCounts(short[] normalizedCounts, int tableLog, int[] counts, int total, int maxSymbol)
    {
        checkArgument(tableLog >= MIN_TABLE_LOG, "Unsupported FSE table size");
        checkArgument(tableLog <= MAX_TABLE_LOG, "FSE table size too large");
        checkArgument(tableLog >= Util.minTableLog(total, maxSymbol), "FSE table size too small");

        long scale = 62 - tableLog;
        long step = (1L << 62) / total;
        long vstep = 1L << (scale - 20);

        int stillToDistribute = 1 << tableLog;

        int largest = 0;
        short largestProbability = 0;
        int lowThreshold = total >>> tableLog;

        for (int symbol = 0; symbol <= maxSymbol; symbol++) {
            if (counts[symbol] == total) {
                throw new IllegalArgumentException(); // TODO: should have been RLE-compressed by upper layers
            }
            if (counts[symbol] == 0) {
                normalizedCounts[symbol] = 0;
                continue;
            }
            if (counts[symbol] <= lowThreshold) {
                normalizedCounts[symbol] = -1;
                stillToDistribute--;
            }
            else {
                short probability = (short) ((counts[symbol] * step) >>> scale);
                if (probability < 8) {
                    long restToBeat = vstep * REST_TO_BEAT[probability];
                    long delta = counts[symbol] * step - (((long) probability) << scale);
                    if (delta > restToBeat) {
                        probability++;
                    }
                }
                if (probability > largestProbability) {
                    largestProbability = probability;
                    largest = symbol;
                }
                normalizedCounts[symbol] = probability;
                stillToDistribute -= probability;
            }
        }

        if (-stillToDistribute >= (normalizedCounts[largest] >>> 1)) {
            // corner case. Need another normalization method
            // TODO size_t const errorCode = FSE_normalizeM2(normalizedCounter, tableLog, count, total, maxSymbolValue);
            normalizeCounts2(normalizedCounts, tableLog, counts, total, maxSymbol);
        }
        else {
            normalizedCounts[largest] += (short) stillToDistribute;
        }

        return tableLog;
    }

    private static int normalizeCounts2(short[] normalizedCounts, int tableLog, int[] counts, int total, int maxSymbol)
    {
        int distributed = 0;

        int lowThreshold = total >>> tableLog; // minimum count below which frequency in the normalized table is "too small" (~ < 1)
        int lowOne = (total * 3) >>> (tableLog + 1); // 1.5 * lowThreshold. If count in (lowThreshold, lowOne] => assign frequency 1

        for (int i = 0; i <= maxSymbol; i++) {
            if (counts[i] == 0) {
                normalizedCounts[i] = 0;
            }
            else if (counts[i] <= lowThreshold) {
                normalizedCounts[i] = -1;
                distributed++;
                total -= counts[i];
            }
            else if (counts[i] <= lowOne) {
                normalizedCounts[i] = 1;
                distributed++;
                total -= counts[i];
            }
            else {
                normalizedCounts[i] = UNASSIGNED;
            }
        }

        int normalizationFactor = 1 << tableLog;
        int toDistribute = normalizationFactor - distributed;

        if ((total / toDistribute) > lowOne) {
            /* risk of rounding to zero */
            lowOne = ((total * 3) / (toDistribute * 2));
            for (int i = 0; i <= maxSymbol; i++) {
                if ((normalizedCounts[i] == UNASSIGNED) && (counts[i] <= lowOne)) {
                    normalizedCounts[i] = 1;
                    distributed++;
                    total -= counts[i];
                }
            }
            toDistribute = normalizationFactor - distributed;
        }

        if (distributed == maxSymbol + 1) {
            // all values are pretty poor;
            // probably incompressible data (should have already been detected);
            // find max, then give all remaining points to max
            int maxValue = 0;
            int maxCount = 0;
            for (int i = 0; i <= maxSymbol; i++) {
                if (counts[i] > maxCount) {
                    maxValue = i;
                    maxCount = counts[i];
                }
            }
            normalizedCounts[maxValue] += (short) toDistribute;
            return 0;
        }

        if (total == 0) {
            // all of the symbols were low enough for the lowOne or lowThreshold
            for (int i = 0; toDistribute > 0; i = (i + 1) % (maxSymbol + 1)) {
                if (normalizedCounts[i] > 0) {
                    toDistribute--;
                    normalizedCounts[i]++;
                }
            }
            return 0;
        }

        // TODO: simplify/document this code
        long vStepLog = 62 - tableLog;
        long mid = (1L << (vStepLog - 1)) - 1;
        long rStep = (((1L << vStepLog) * toDistribute) + mid) / total;   /* scale on remaining */
        long tmpTotal = mid;
        for (int i = 0; i <= maxSymbol; i++) {
            if (normalizedCounts[i] == UNASSIGNED) {
                long end = tmpTotal + (counts[i] * rStep);
                int sStart = (int) (tmpTotal >>> vStepLog);
                int sEnd = (int) (end >>> vStepLog);
                int weight = sEnd - sStart;

                if (weight < 1) {
                    throw new AssertionError();
                }
                normalizedCounts[i] = (short) weight;
                tmpTotal = end;
            }
        }

        return 0;
    }

    public static int writeNormalizedCounts(Object outputBase, long outputAddress, int outputSize, short[] normalizedCounts, int maxSymbol, int tableLog)
    {
        checkArgument(tableLog <= MAX_TABLE_LOG, "FSE table too large");
        checkArgument(tableLog >= MIN_TABLE_LOG, "FSE table too small");

        long output = outputAddress;
        long outputLimit = outputAddress + outputSize;

        int tableSize = 1 << tableLog;

        int bitCount = 0;

        // encode table size
        int bitStream = (tableLog - MIN_TABLE_LOG);
        bitCount += 4;

        int remaining = tableSize + 1; // +1 for extra accuracy
        int threshold = tableSize;
        int tableBitCount = tableLog + 1;

        int symbol = 0;

        boolean previousIs0 = false;
        while (remaining > 1) {
            if (previousIs0) {
                // From RFC 8478, section 4.1.1:
                //   When a symbol has a probability of zero, it is followed by a 2-bit
                //   repeat flag.  This repeat flag tells how many probabilities of zeroes
                //   follow the current one.  It provides a number ranging from 0 to 3.
                //   If it is a 3, another 2-bit repeat flag follows, and so on.
                int start = symbol;

                // find run of symbols with count 0
                while (normalizedCounts[symbol] == 0) {
                    symbol++;
                }

                // encode in batches if 8 repeat sequences in one shot (representing 24 symbols total)
                while (symbol >= start + 24) {
                    start += 24;
                    bitStream |= (0b11_11_11_11_11_11_11_11 << bitCount);
                    checkArgument(output + SIZE_OF_SHORT <= outputLimit, "Output buffer too small");

                    UNSAFE.putShort(outputBase, output, (short) bitStream);
                    output += SIZE_OF_SHORT;

                    // flush now, so no need to increase bitCount by 16
                    bitStream >>>= Short.SIZE;
                }

                // encode remaining in batches of 3 symbols
                while (symbol >= start + 3) {
                    start += 3;
                    bitStream |= 0b11 << bitCount;
                    bitCount += 2;
                }

                // encode tail
                bitStream |= (symbol - start) << bitCount;
                bitCount += 2;

                // flush bitstream if necessary
                if (bitCount > 16) {
                    checkArgument(output + SIZE_OF_SHORT <= outputLimit, "Output buffer too small");

                    UNSAFE.putShort(outputBase, output, (short) bitStream);
                    output += SIZE_OF_SHORT;

                    bitStream >>>= Short.SIZE;
                    bitCount -= Short.SIZE;
                }
            }

            int count = normalizedCounts[symbol++];
            int max = (2 * threshold - 1) - remaining;
            remaining -= count < 0 ? -count : count;
            count++;   /* +1 for extra accuracy */
            if (count >= threshold) {
                count += max;
            }
            bitStream |= count << bitCount;
            bitCount += tableBitCount;
            bitCount -= (count < max ? 1 : 0);
            previousIs0 = (count == 1);

            if (remaining < 1) {
                throw new AssertionError();
            }

            while (remaining < threshold) {
                tableBitCount--;
                threshold >>= 1;
            }

            // flush bitstream if necessary
            if (bitCount > 16) {
                checkArgument(output + SIZE_OF_SHORT <= outputLimit, "Output buffer too small");

                UNSAFE.putShort(outputBase, output, (short) bitStream);
                output += SIZE_OF_SHORT;

                bitStream >>>= Short.SIZE;
                bitCount -= Short.SIZE;
            }
        }

        // flush remaining bitstream
        checkArgument(output + SIZE_OF_SHORT <= outputLimit, "Output buffer too small");
        UNSAFE.putShort(outputBase, output, (short) bitStream);
        output += (bitCount + 7) / 8;

        checkArgument(symbol <= maxSymbol + 1, "Error"); // TODO

        return (int) (output - outputAddress);
    }

    public static final class Table
    {
        int log2Size;
        final int[] newState;
        final byte[] symbol;
        final byte[] numberOfBits;

        public Table(int log2Capacity)
        {
            int capacity = 1 << log2Capacity;
            newState = new int[capacity];
            symbol = new byte[capacity];
            numberOfBits = new byte[capacity];
        }

        public Table(int log2Size, int[] newState, byte[] symbol, byte[] numberOfBits)
        {
            int size = 1 << log2Size;
            if (newState.length != size || symbol.length != size || numberOfBits.length != size) {
                throw new IllegalArgumentException("Expected arrays to match provided size");
            }

            this.log2Size = log2Size;
            this.newState = newState;
            this.symbol = symbol;
            this.numberOfBits = numberOfBits;
        }
    }
}
