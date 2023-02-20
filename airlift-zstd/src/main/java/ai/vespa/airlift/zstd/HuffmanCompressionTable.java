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

import java.util.Arrays;

import static ai.vespa.airlift.zstd.Huffman.MAX_FSE_TABLE_LOG;
import static ai.vespa.airlift.zstd.Huffman.MAX_SYMBOL;
import static ai.vespa.airlift.zstd.Huffman.MAX_SYMBOL_COUNT;
import static ai.vespa.airlift.zstd.Huffman.MAX_TABLE_LOG;
import static ai.vespa.airlift.zstd.Huffman.MIN_TABLE_LOG;
import static ai.vespa.airlift.zstd.UnsafeUtil.UNSAFE;
import static ai.vespa.airlift.zstd.Util.checkArgument;
import static ai.vespa.airlift.zstd.Util.minTableLog;

final class HuffmanCompressionTable
{
    private final short[] values;
    private final byte[] numberOfBits;

    private int maxSymbol;
    private int maxNumberOfBits;

    public HuffmanCompressionTable(int capacity)
    {
        this.values = new short[capacity];
        this.numberOfBits = new byte[capacity];
    }

    public static int optimalNumberOfBits(int maxNumberOfBits, int inputSize, int maxSymbol)
    {
        if (inputSize <= 1) {
            throw new IllegalArgumentException(); // not supported. Use RLE instead
        }

        int result = maxNumberOfBits;

        result = Math.min(result, Util.highestBit((inputSize - 1)) - 1); // we may be able to reduce accuracy if input is small

        // Need a minimum to safely represent all symbol values
        result = Math.max(result, minTableLog(inputSize, maxSymbol));

        result = Math.max(result, MIN_TABLE_LOG); // absolute minimum for Huffman
        result = Math.min(result, MAX_TABLE_LOG); // absolute maximum for Huffman

        return result;
    }

    public void initialize(int[] counts, int maxSymbol, int maxNumberOfBits, HuffmanCompressionTableWorkspace workspace)
    {
        checkArgument(maxSymbol <= MAX_SYMBOL, "Max symbol value too large");

        workspace.reset();

        NodeTable nodeTable = workspace.nodeTable;
        nodeTable.reset();

        int lastNonZero = buildTree(counts, maxSymbol, nodeTable);

        // enforce max table log
        maxNumberOfBits = setMaxHeight(nodeTable, lastNonZero, maxNumberOfBits, workspace);
        checkArgument(maxNumberOfBits <= MAX_TABLE_LOG, "Max number of bits larger than max table size");

        // populate table
        int symbolCount = maxSymbol + 1;
        for (int node = 0; node < symbolCount; node++) {
            int symbol = nodeTable.symbols[node];
            numberOfBits[symbol] = nodeTable.numberOfBits[node];
        }

        short[] entriesPerRank = workspace.entriesPerRank;
        short[] valuesPerRank = workspace.valuesPerRank;

        for (int n = 0; n <= lastNonZero; n++) {
            entriesPerRank[nodeTable.numberOfBits[n]]++;
        }

        // determine starting value per rank
        short startingValue = 0;
        for (int rank = maxNumberOfBits; rank > 0; rank--) {
            valuesPerRank[rank] = startingValue; // get starting value within each rank
            startingValue += entriesPerRank[rank];
            startingValue >>>= 1;
        }

        for (int n = 0; n <= maxSymbol; n++) {
            values[n] = valuesPerRank[numberOfBits[n]]++; // assign value within rank, symbol order
        }

        this.maxSymbol = maxSymbol;
        this.maxNumberOfBits = maxNumberOfBits;
    }

    private int buildTree(int[] counts, int maxSymbol, NodeTable nodeTable)
    {
        // populate the leaves of the node table from the histogram of counts
        // in descending order by count, ascending by symbol value.
        short current = 0;

        for (int symbol = 0; symbol <= maxSymbol; symbol++) {
            int count = counts[symbol];

            // simple insertion sort
            int position = current;
            while (position > 1 && count > nodeTable.count[position - 1]) {
                nodeTable.copyNode(position - 1, position);
                position--;
            }

            nodeTable.count[position] = count;
            nodeTable.symbols[position] = symbol;

            current++;
        }

        int lastNonZero = maxSymbol;
        while (nodeTable.count[lastNonZero] == 0) {
            lastNonZero--;
        }

        // populate the non-leaf nodes
        short nonLeafStart = MAX_SYMBOL_COUNT;
        current = nonLeafStart;

        int currentLeaf = lastNonZero;

        // combine the two smallest leaves to create the first intermediate node
        int currentNonLeaf = current;
        nodeTable.count[current] = nodeTable.count[currentLeaf] + nodeTable.count[currentLeaf - 1];
        nodeTable.parents[currentLeaf] = current;
        nodeTable.parents[currentLeaf - 1] = current;
        current++;
        currentLeaf -= 2;

        int root = MAX_SYMBOL_COUNT + lastNonZero - 1;

        // fill in sentinels
        for (int n = current; n <= root; n++) {
            nodeTable.count[n] = 1 << 30;
        }

        // create parents
        while (current <= root) {
            int child1;
            if (currentLeaf >= 0 && nodeTable.count[currentLeaf] < nodeTable.count[currentNonLeaf]) {
                child1 = currentLeaf--;
            }
            else {
                child1 = currentNonLeaf++;
            }

            int child2;
            if (currentLeaf >= 0 && nodeTable.count[currentLeaf] < nodeTable.count[currentNonLeaf]) {
                child2 = currentLeaf--;
            }
            else {
                child2 = currentNonLeaf++;
            }

            nodeTable.count[current] = nodeTable.count[child1] + nodeTable.count[child2];
            nodeTable.parents[child1] = current;
            nodeTable.parents[child2] = current;
            current++;
        }

        // distribute weights
        nodeTable.numberOfBits[root] = 0;
        for (int n = root - 1; n >= nonLeafStart; n--) {
            short parent = nodeTable.parents[n];
            nodeTable.numberOfBits[n] = (byte) (nodeTable.numberOfBits[parent] + 1);
        }

        for (int n = 0; n <= lastNonZero; n++) {
            short parent = nodeTable.parents[n];
            nodeTable.numberOfBits[n] = (byte) (nodeTable.numberOfBits[parent] + 1);
        }

        return lastNonZero;
    }

    // TODO: consider encoding 2 symbols at a time
    //   - need a table with 256x256 entries with
    //      - the concatenated bits for the corresponding pair of symbols
    //      - the sum of bits for the corresponding pair of symbols
    //   - read 2 symbols at a time from the input
    public void encodeSymbol(BitOutputStream output, int symbol)
    {
        output.addBitsFast(values[symbol], numberOfBits[symbol]);
    }

    public int write(Object outputBase, long outputAddress, int outputSize, HuffmanTableWriterWorkspace workspace)
    {
        byte[] weights = workspace.weights;

        long output = outputAddress;

        int maxNumberOfBits = this.maxNumberOfBits;
        int maxSymbol = this.maxSymbol;

        // convert to weights per RFC 8478 section 4.2.1
        for (int symbol = 0; symbol < maxSymbol; symbol++) {
            int bits = numberOfBits[symbol];

            if (bits == 0) {
                weights[symbol] = 0;
            }
            else {
                weights[symbol] = (byte) (maxNumberOfBits + 1 - bits);
            }
        }

        // attempt weights compression by FSE
        int size = compressWeights(outputBase, output + 1, outputSize - 1, weights, maxSymbol, workspace);

        if (maxSymbol > 127 && size > 127) {
            // This should never happen. Since weights are in the range [0, 12], they can be compressed optimally to ~3.7 bits per symbol for a uniform distribution.
            // Since maxSymbol has to be <= MAX_SYMBOL (255), this is 119 bytes + FSE headers.
            throw new AssertionError();
        }

        if (size != 0 && size != 1 && size < maxSymbol / 2) {
            // Go with FSE only if:
            //   - the weights are compressible
            //   - the compressed size is better than what we'd get with the raw encoding below
            //   - the compressed size is <= 127 bytes, which is the most that the encoding can hold for FSE-compressed weights (see RFC 8478 section 4.2.1.1). This is implied
            //     by the maxSymbol / 2 check, since maxSymbol must be <= 255
            UNSAFE.putByte(outputBase, output, (byte) size);
            return size + 1; // header + size
        }
        else {
            // Use raw encoding (4 bits per entry)

            // #entries = #symbols - 1 since last symbol is implicit. Thus, #entries = (maxSymbol + 1) - 1 = maxSymbol
            int entryCount = maxSymbol;

            size = (entryCount + 1) / 2;  // ceil(#entries / 2)
            checkArgument(size + 1 /* header */ <= outputSize, "Output size too small"); // 2 entries per byte

            // encode number of symbols
            // header = #entries + 127 per RFC
            UNSAFE.putByte(outputBase, output, (byte) (127 + entryCount));
            output++;

            weights[maxSymbol] = 0; // last weight is implicit, so set to 0 so that it doesn't get encoded below
            for (int i = 0; i < entryCount; i += 2) {
                UNSAFE.putByte(outputBase, output, (byte) ((weights[i] << 4) + weights[i + 1]));
                output++;
            }

            return (int) (output - outputAddress);
        }
    }

    /**
     * Can this table encode all symbols with non-zero count?
     */
    public boolean isValid(int[] counts, int maxSymbol)
    {
        if (maxSymbol > this.maxSymbol) {
            // some non-zero count symbols cannot be encoded by the current table
            return false;
        }

        for (int symbol = 0; symbol <= maxSymbol; ++symbol) {
            if (counts[symbol] != 0 && numberOfBits[symbol] == 0) {
                return false;
            }
        }
        return true;
    }

    public int estimateCompressedSize(int[] counts, int maxSymbol)
    {
        int numberOfBits = 0;
        for (int symbol = 0; symbol <= Math.min(maxSymbol, this.maxSymbol); symbol++) {
            numberOfBits += this.numberOfBits[symbol] * counts[symbol];
        }

        return numberOfBits >>> 3; // convert to bytes
    }

    // http://fastcompression.blogspot.com/2015/07/huffman-revisited-part-3-depth-limited.html
    private static int setMaxHeight(NodeTable nodeTable, int lastNonZero, int maxNumberOfBits, HuffmanCompressionTableWorkspace workspace)
    {
        int largestBits = nodeTable.numberOfBits[lastNonZero];

        if (largestBits <= maxNumberOfBits) {
            return largestBits;   // early exit: no elements > maxNumberOfBits
        }

        // there are several too large elements (at least >= 2)
        int totalCost = 0;
        int baseCost = 1 << (largestBits - maxNumberOfBits);
        int n = lastNonZero;

        while (nodeTable.numberOfBits[n] > maxNumberOfBits) {
            totalCost += baseCost - (1 << (largestBits - nodeTable.numberOfBits[n]));
            nodeTable.numberOfBits[n ] = (byte) maxNumberOfBits;
            n--;
        }  // n stops at nodeTable.numberOfBits[n + offset] <= maxNumberOfBits

        while (nodeTable.numberOfBits[n] == maxNumberOfBits) {
            n--;   // n ends at index of smallest symbol using < maxNumberOfBits
        }

        // renormalize totalCost
        totalCost >>>= (largestBits - maxNumberOfBits);  // note: totalCost is necessarily a multiple of baseCost

        // repay normalized cost
        int noSymbol = 0xF0F0F0F0;
        int[] rankLast = workspace.rankLast;
        Arrays.fill(rankLast, noSymbol);

        // Get pos of last (smallest) symbol per rank
        int currentNbBits = maxNumberOfBits;
        for (int pos = n; pos >= 0; pos--) {
            if (nodeTable.numberOfBits[pos] >= currentNbBits) {
                continue;
            }
            currentNbBits = nodeTable.numberOfBits[pos];   // < maxNumberOfBits
            rankLast[maxNumberOfBits - currentNbBits] = pos;
        }

        while (totalCost > 0) {
            int numberOfBitsToDecrease = Util.highestBit(totalCost) + 1;
            for (; numberOfBitsToDecrease > 1; numberOfBitsToDecrease--) {
                int highPosition = rankLast[numberOfBitsToDecrease];
                int lowPosition = rankLast[numberOfBitsToDecrease - 1];
                if (highPosition == noSymbol) {
                    continue;
                }
                if (lowPosition == noSymbol) {
                    break;
                }
                int highTotal = nodeTable.count[highPosition];
                int lowTotal = 2 * nodeTable.count[lowPosition];
                if (highTotal <= lowTotal) {
                    break;
                }
            }

            // only triggered when no more rank 1 symbol left => find closest one (note : there is necessarily at least one !)
            // HUF_MAX_TABLELOG test just to please gcc 5+; but it should not be necessary
            while ((numberOfBitsToDecrease <= MAX_TABLE_LOG) && (rankLast[numberOfBitsToDecrease] == noSymbol)) {
                numberOfBitsToDecrease++;
            }
            totalCost -= 1 << (numberOfBitsToDecrease - 1);
            if (rankLast[numberOfBitsToDecrease - 1] == noSymbol) {
                rankLast[numberOfBitsToDecrease - 1] = rankLast[numberOfBitsToDecrease];   // this rank is no longer empty
            }
            nodeTable.numberOfBits[rankLast[numberOfBitsToDecrease]]++;
            if (rankLast[numberOfBitsToDecrease] == 0) {   /* special case, reached largest symbol */
                rankLast[numberOfBitsToDecrease] = noSymbol;
            }
            else {
                rankLast[numberOfBitsToDecrease]--;
                if (nodeTable.numberOfBits[rankLast[numberOfBitsToDecrease]] != maxNumberOfBits - numberOfBitsToDecrease) {
                    rankLast[numberOfBitsToDecrease] = noSymbol;   // this rank is now empty
                }
            }
        }

        while (totalCost < 0) {  // Sometimes, cost correction overshoot
            if (rankLast[1] == noSymbol) {  /* special case : no rank 1 symbol (using maxNumberOfBits-1); let's create one from largest rank 0 (using maxNumberOfBits) */
                while (nodeTable.numberOfBits[n] == maxNumberOfBits) {
                    n--;
                }
                nodeTable.numberOfBits[n + 1]--;
                rankLast[1] = n + 1;
                totalCost++;
                continue;
            }
            nodeTable.numberOfBits[rankLast[1] + 1]--;
            rankLast[1]++;
            totalCost++;
        }

        return maxNumberOfBits;
    }

    /**
     * All elements within weightTable must be <= Huffman.MAX_TABLE_LOG
     */
    private static int compressWeights(Object outputBase, long outputAddress, int outputSize, byte[] weights, int weightsLength, HuffmanTableWriterWorkspace workspace)
    {
        if (weightsLength <= 1) {
            return 0; // Not compressible
        }

        // Scan input and build symbol stats
        int[] counts = workspace.counts;
        Histogram.count(weights, weightsLength, counts);
        int maxSymbol = Histogram.findMaxSymbol(counts, MAX_TABLE_LOG);
        int maxCount = Histogram.findLargestCount(counts, maxSymbol);

        if (maxCount == weightsLength) {
            return 1; // only a single symbol in source
        }
        if (maxCount == 1) {
            return 0; // each symbol present maximum once => not compressible
        }

        short[] normalizedCounts = workspace.normalizedCounts;

        int tableLog = FiniteStateEntropy.optimalTableLog(MAX_FSE_TABLE_LOG, weightsLength, maxSymbol);
        FiniteStateEntropy.normalizeCounts(normalizedCounts, tableLog, counts, weightsLength, maxSymbol);

        long output = outputAddress;
        long outputLimit = outputAddress + outputSize;

        // Write table description header
        int headerSize = FiniteStateEntropy.writeNormalizedCounts(outputBase, output, outputSize, normalizedCounts, maxSymbol, tableLog);
        output += headerSize;

        // Compress
        FseCompressionTable compressionTable = workspace.fseTable;
        compressionTable.initialize(normalizedCounts, maxSymbol, tableLog);
        int compressedSize = FiniteStateEntropy.compress(outputBase, output, (int) (outputLimit - output), weights, weightsLength, compressionTable);
        if (compressedSize == 0) {
            return 0;
        }
        output += compressedSize;

        return (int) (output - outputAddress);
    }
}
