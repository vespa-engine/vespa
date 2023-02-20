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

import static ai.vespa.airlift.zstd.FiniteStateEntropy.MAX_SYMBOL;

class FseCompressionTable
{
    private final short[] nextState;
    private final int[] deltaNumberOfBits;
    private final int[] deltaFindState;

    private int log2Size;

    public FseCompressionTable(int maxTableLog, int maxSymbol)
    {
        nextState = new short[1 << maxTableLog];
        deltaNumberOfBits = new int[maxSymbol + 1];
        deltaFindState = new int[maxSymbol + 1];
    }

    public static FseCompressionTable newInstance(short[] normalizedCounts, int maxSymbol, int tableLog)
    {
        FseCompressionTable result = new FseCompressionTable(tableLog, maxSymbol);
        result.initialize(normalizedCounts, maxSymbol, tableLog);

        return result;
    }

    public void initializeRleTable(int symbol)
    {
        log2Size = 0;

        nextState[0] = 0;
        nextState[1] = 0;

        deltaFindState[symbol] = 0;
        deltaNumberOfBits[symbol] = 0;
    }

    public void initialize(short[] normalizedCounts, int maxSymbol, int tableLog)
    {
        int tableSize = 1 << tableLog;

        byte[] table = new byte[tableSize]; // TODO: allocate in workspace
        int highThreshold = tableSize - 1;

        // TODO: make sure FseCompressionTable has enough size
        log2Size = tableLog;

        // For explanations on how to distribute symbol values over the table:
        // http://fastcompression.blogspot.fr/2014/02/fse-distributing-symbol-values.html

        // symbol start positions
        int[] cumulative = new int[MAX_SYMBOL + 2]; // TODO: allocate in workspace
        cumulative[0] = 0;
        for (int i = 1; i <= maxSymbol + 1; i++) {
            if (normalizedCounts[i - 1] == -1) {  // Low probability symbol
                cumulative[i] = cumulative[i - 1] + 1;
                table[highThreshold--] = (byte) (i - 1);
            }
            else {
                cumulative[i] = cumulative[i - 1] + normalizedCounts[i - 1];
            }
        }
        cumulative[maxSymbol + 1] = tableSize + 1;

        // Spread symbols
        int position = spreadSymbols(normalizedCounts, maxSymbol, tableSize, highThreshold, table);

        if (position != 0) {
            throw new AssertionError("Spread symbols failed");
        }

        // Build table
        for (int i = 0; i < tableSize; i++) {
            byte symbol = table[i];
            nextState[cumulative[symbol]++] = (short) (tableSize + i);  /* TableU16 : sorted by symbol order; gives next state value */
        }

        // Build symbol transformation table
        int total = 0;
        for (int symbol = 0; symbol <= maxSymbol; symbol++) {
            switch (normalizedCounts[symbol]) {
                case 0:
                    deltaNumberOfBits[symbol] = ((tableLog + 1) << 16) - tableSize;
                    break;
                case -1:
                case 1:
                    deltaNumberOfBits[symbol] = (tableLog << 16) - tableSize;
                    deltaFindState[symbol] = total - 1;
                    total++;
                    break;
                default:
                    int maxBitsOut = tableLog - Util.highestBit(normalizedCounts[symbol] - 1);
                    int minStatePlus = normalizedCounts[symbol] << maxBitsOut;
                    deltaNumberOfBits[symbol] = (maxBitsOut << 16) - minStatePlus;
                    deltaFindState[symbol] = total - normalizedCounts[symbol];
                    total += normalizedCounts[symbol];
                    break;
            }
        }
    }

    public int begin(byte symbol)
    {
        int outputBits = (deltaNumberOfBits[symbol] + (1 << 15)) >>> 16;
        int base = ((outputBits << 16) - deltaNumberOfBits[symbol]) >>> outputBits;
        return nextState[base + deltaFindState[symbol]];
    }

    public int encode(BitOutputStream stream, int state, int symbol)
    {
        int outputBits = (state + deltaNumberOfBits[symbol]) >>> 16;
        stream.addBits(state, outputBits);
        return nextState[(state >>> outputBits) + deltaFindState[symbol]];
    }

    public void finish(BitOutputStream stream, int state)
    {
        stream.addBits(state, log2Size);
        stream.flush();
    }

    private static int calculateStep(int tableSize)
    {
        return (tableSize >>> 1) + (tableSize >>> 3) + 3;
    }

    public static int spreadSymbols(short[] normalizedCounters, int maxSymbolValue, int tableSize, int highThreshold, byte[] symbols)
    {
        int mask = tableSize - 1;
        int step = calculateStep(tableSize);

        int position = 0;
        for (byte symbol = 0; symbol <= maxSymbolValue; symbol++) {
            for (int i = 0; i < normalizedCounters[symbol]; i++) {
                symbols[position] = symbol;
                do {
                    position = (position + step) & mask;
                }
                while (position > highThreshold);
            }
        }
        return position;
    }
}
