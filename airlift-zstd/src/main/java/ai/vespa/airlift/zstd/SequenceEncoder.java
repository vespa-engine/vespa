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

import static ai.vespa.airlift.zstd.Constants.DEFAULT_MAX_OFFSET_CODE_SYMBOL;
import static ai.vespa.airlift.zstd.Constants.LITERALS_LENGTH_BITS;
import static ai.vespa.airlift.zstd.Constants.LITERAL_LENGTH_TABLE_LOG;
import static ai.vespa.airlift.zstd.Constants.LONG_NUMBER_OF_SEQUENCES;
import static ai.vespa.airlift.zstd.Constants.MATCH_LENGTH_BITS;
import static ai.vespa.airlift.zstd.Constants.MATCH_LENGTH_TABLE_LOG;
import static ai.vespa.airlift.zstd.Constants.MAX_LITERALS_LENGTH_SYMBOL;
import static ai.vespa.airlift.zstd.Constants.MAX_MATCH_LENGTH_SYMBOL;
import static ai.vespa.airlift.zstd.Constants.MAX_OFFSET_CODE_SYMBOL;
import static ai.vespa.airlift.zstd.Constants.OFFSET_TABLE_LOG;
import static ai.vespa.airlift.zstd.Constants.SEQUENCE_ENCODING_BASIC;
import static ai.vespa.airlift.zstd.Constants.SEQUENCE_ENCODING_COMPRESSED;
import static ai.vespa.airlift.zstd.Constants.SEQUENCE_ENCODING_RLE;
import static ai.vespa.airlift.zstd.Constants.SIZE_OF_SHORT;
import static ai.vespa.airlift.zstd.FiniteStateEntropy.optimalTableLog;
import static ai.vespa.airlift.zstd.UnsafeUtil.UNSAFE;
import static ai.vespa.airlift.zstd.Util.checkArgument;

class SequenceEncoder
{
    private static final int DEFAULT_LITERAL_LENGTH_NORMALIZED_COUNTS_LOG = 6;
    private static final short[] DEFAULT_LITERAL_LENGTH_NORMALIZED_COUNTS = {4, 3, 2, 2, 2, 2, 2, 2,
                                                                             2, 2, 2, 2, 2, 1, 1, 1,
                                                                             2, 2, 2, 2, 2, 2, 2, 2,
                                                                             2, 3, 2, 1, 1, 1, 1, 1,
                                                                             -1, -1, -1, -1};

    private static final int DEFAULT_MATCH_LENGTH_NORMALIZED_COUNTS_LOG = 6;
    private static final short[] DEFAULT_MATCH_LENGTH_NORMALIZED_COUNTS = {1, 4, 3, 2, 2, 2, 2, 2,
                                                                           2, 1, 1, 1, 1, 1, 1, 1,
                                                                           1, 1, 1, 1, 1, 1, 1, 1,
                                                                           1, 1, 1, 1, 1, 1, 1, 1,
                                                                           1, 1, 1, 1, 1, 1, 1, 1,
                                                                           1, 1, 1, 1, 1, 1, -1, -1,
                                                                           -1, -1, -1, -1, -1};

    private static final int DEFAULT_OFFSET_NORMALIZED_COUNTS_LOG = 5;
    private static final short[] DEFAULT_OFFSET_NORMALIZED_COUNTS = {1, 1, 1, 1, 1, 1, 2, 2,
                                                                     2, 1, 1, 1, 1, 1, 1, 1,
                                                                     1, 1, 1, 1, 1, 1, 1, 1,
                                                                     -1, -1, -1, -1, -1};

    private static final FseCompressionTable DEFAULT_LITERAL_LENGTHS_TABLE = FseCompressionTable.newInstance(DEFAULT_LITERAL_LENGTH_NORMALIZED_COUNTS, MAX_LITERALS_LENGTH_SYMBOL, DEFAULT_LITERAL_LENGTH_NORMALIZED_COUNTS_LOG);
    private static final FseCompressionTable DEFAULT_MATCH_LENGTHS_TABLE = FseCompressionTable.newInstance(DEFAULT_MATCH_LENGTH_NORMALIZED_COUNTS, MAX_MATCH_LENGTH_SYMBOL, DEFAULT_LITERAL_LENGTH_NORMALIZED_COUNTS_LOG);
    private static final FseCompressionTable DEFAULT_OFFSETS_TABLE = FseCompressionTable.newInstance(DEFAULT_OFFSET_NORMALIZED_COUNTS, DEFAULT_MAX_OFFSET_CODE_SYMBOL, DEFAULT_OFFSET_NORMALIZED_COUNTS_LOG);

    private SequenceEncoder()
    {
    }

    public static int compressSequences(Object outputBase, final long outputAddress, int outputSize, SequenceStore sequences, CompressionParameters.Strategy strategy, SequenceEncodingContext workspace)
    {
        long output = outputAddress;
        long outputLimit = outputAddress + outputSize;

        checkArgument(outputLimit - output > 3 /* max sequence count Size */ + 1 /* encoding type flags */, "Output buffer too small");

        int sequenceCount = sequences.sequenceCount;
        if (sequenceCount < 0x7F) {
            UNSAFE.putByte(outputBase, output, (byte) sequenceCount);
            output++;
        }
        else if (sequenceCount < LONG_NUMBER_OF_SEQUENCES) {
            UNSAFE.putByte(outputBase, output, (byte) (sequenceCount >>> 8 | 0x80));
            UNSAFE.putByte(outputBase, output + 1, (byte) sequenceCount);
            output += SIZE_OF_SHORT;
        }
        else {
            UNSAFE.putByte(outputBase, output, (byte) 0xFF);
            output++;
            UNSAFE.putShort(outputBase, output, (short) (sequenceCount - LONG_NUMBER_OF_SEQUENCES));
            output += SIZE_OF_SHORT;
        }

        if (sequenceCount == 0) {
            return (int) (output - outputAddress);
        }

        // flags for FSE encoding type
        long headerAddress = output++;

        int maxSymbol;
        int largestCount;

        // literal lengths
        int[] counts = workspace.counts;
        Histogram.count(sequences.literalLengthCodes, sequenceCount, workspace.counts);
        maxSymbol = Histogram.findMaxSymbol(counts, MAX_LITERALS_LENGTH_SYMBOL);
        largestCount = Histogram.findLargestCount(counts, maxSymbol);

        int literalsLengthEncodingType = selectEncodingType(largestCount, sequenceCount, DEFAULT_LITERAL_LENGTH_NORMALIZED_COUNTS_LOG, true, strategy);

        FseCompressionTable literalLengthTable;
        switch (literalsLengthEncodingType) {
            case SEQUENCE_ENCODING_RLE:
                UNSAFE.putByte(outputBase, output, sequences.literalLengthCodes[0]);
                output++;
                workspace.literalLengthTable.initializeRleTable(maxSymbol);
                literalLengthTable = workspace.literalLengthTable;
                break;
            case SEQUENCE_ENCODING_BASIC:
                literalLengthTable = DEFAULT_LITERAL_LENGTHS_TABLE;
                break;
            case SEQUENCE_ENCODING_COMPRESSED:
                output += buildCompressionTable(
                        workspace.literalLengthTable,
                        outputBase,
                        output,
                        outputLimit,
                        sequenceCount,
                        LITERAL_LENGTH_TABLE_LOG,
                        sequences.literalLengthCodes,
                        workspace.counts,
                        maxSymbol,
                        workspace.normalizedCounts);
                literalLengthTable = workspace.literalLengthTable;
                break;
            default:
                throw new UnsupportedOperationException("not yet implemented");
        }

        // offsets
        Histogram.count(sequences.offsetCodes, sequenceCount, workspace.counts);
        maxSymbol = Histogram.findMaxSymbol(counts, MAX_OFFSET_CODE_SYMBOL);
        largestCount = Histogram.findLargestCount(counts, maxSymbol);

        // We can only use the basic table if max <= DEFAULT_MAX_OFFSET_CODE_SYMBOL, otherwise the offsets are too large .
        boolean defaultAllowed = maxSymbol < DEFAULT_MAX_OFFSET_CODE_SYMBOL;

        int offsetEncodingType = selectEncodingType(largestCount, sequenceCount, DEFAULT_OFFSET_NORMALIZED_COUNTS_LOG, defaultAllowed, strategy);

        FseCompressionTable offsetCodeTable;
        switch (offsetEncodingType) {
            case SEQUENCE_ENCODING_RLE:
                UNSAFE.putByte(outputBase, output, sequences.offsetCodes[0]);
                output++;
                workspace.offsetCodeTable.initializeRleTable(maxSymbol);
                offsetCodeTable = workspace.offsetCodeTable;
                break;
            case SEQUENCE_ENCODING_BASIC:
                offsetCodeTable = DEFAULT_OFFSETS_TABLE;
                break;
            case SEQUENCE_ENCODING_COMPRESSED:
                output += buildCompressionTable(
                        workspace.offsetCodeTable,
                        outputBase,
                        output,
                        output + outputSize,
                        sequenceCount,
                        OFFSET_TABLE_LOG,
                        sequences.offsetCodes,
                        workspace.counts,
                        maxSymbol,
                        workspace.normalizedCounts);
                offsetCodeTable = workspace.offsetCodeTable;
                break;
            default:
                throw new UnsupportedOperationException("not yet implemented");
        }

        // match lengths
        Histogram.count(sequences.matchLengthCodes, sequenceCount, workspace.counts);
        maxSymbol = Histogram.findMaxSymbol(counts, MAX_MATCH_LENGTH_SYMBOL);
        largestCount = Histogram.findLargestCount(counts, maxSymbol);

        int matchLengthEncodingType = selectEncodingType(largestCount, sequenceCount, DEFAULT_MATCH_LENGTH_NORMALIZED_COUNTS_LOG, true, strategy);

        FseCompressionTable matchLengthTable;
        switch (matchLengthEncodingType) {
            case SEQUENCE_ENCODING_RLE:
                UNSAFE.putByte(outputBase, output, sequences.matchLengthCodes[0]);
                output++;
                workspace.matchLengthTable.initializeRleTable(maxSymbol);
                matchLengthTable = workspace.matchLengthTable;
                break;
            case SEQUENCE_ENCODING_BASIC:
                matchLengthTable = DEFAULT_MATCH_LENGTHS_TABLE;
                break;
            case SEQUENCE_ENCODING_COMPRESSED:
                output += buildCompressionTable(
                        workspace.matchLengthTable,
                        outputBase,
                        output,
                        outputLimit,
                        sequenceCount,
                        MATCH_LENGTH_TABLE_LOG,
                        sequences.matchLengthCodes,
                        workspace.counts,
                        maxSymbol,
                        workspace.normalizedCounts);
                matchLengthTable = workspace.matchLengthTable;
                break;
            default:
                throw new UnsupportedOperationException("not yet implemented");
        }

        // flags
        UNSAFE.putByte(outputBase, headerAddress, (byte) ((literalsLengthEncodingType << 6) | (offsetEncodingType << 4) | (matchLengthEncodingType << 2)));

        output += encodeSequences(outputBase, output, outputLimit, matchLengthTable, offsetCodeTable, literalLengthTable, sequences);

        return (int) (output - outputAddress);
    }

    private static int buildCompressionTable(FseCompressionTable table, Object outputBase, long output, long outputLimit, int sequenceCount, int maxTableLog, byte[] codes, int[] counts, int maxSymbol, short[] normalizedCounts)
    {
        int tableLog = optimalTableLog(maxTableLog, sequenceCount, maxSymbol);

        // this is a minor optimization. The last symbol is embedded in the initial FSE state, so it's not part of the bitstream. We can omit it from the
        // statistics (but only if its count is > 1). This makes the statistics a tiny bit more accurate.
        if (counts[codes[sequenceCount - 1]] > 1) {
            counts[codes[sequenceCount - 1]]--;
            sequenceCount--;
        }

        FiniteStateEntropy.normalizeCounts(normalizedCounts, tableLog, counts, sequenceCount, maxSymbol);
        table.initialize(normalizedCounts, maxSymbol, tableLog);

        return FiniteStateEntropy.writeNormalizedCounts(outputBase, output, (int) (outputLimit - output), normalizedCounts, maxSymbol, tableLog); // TODO: pass outputLimit directly
    }

    private static int encodeSequences(
            Object outputBase,
            long output,
            long outputLimit,
            FseCompressionTable matchLengthTable,
            FseCompressionTable offsetsTable,
            FseCompressionTable literalLengthTable,
            SequenceStore sequences)
    {
        byte[] matchLengthCodes = sequences.matchLengthCodes;
        byte[] offsetCodes = sequences.offsetCodes;
        byte[] literalLengthCodes = sequences.literalLengthCodes;

        BitOutputStream blockStream = new BitOutputStream(outputBase, output, (int) (outputLimit - output));

        int sequenceCount = sequences.sequenceCount;

        // first symbols
        int matchLengthState = matchLengthTable.begin(matchLengthCodes[sequenceCount - 1]);
        int offsetState = offsetsTable.begin(offsetCodes[sequenceCount - 1]);
        int literalLengthState = literalLengthTable.begin(literalLengthCodes[sequenceCount - 1]);

        blockStream.addBits(sequences.literalLengths[sequenceCount - 1], LITERALS_LENGTH_BITS[literalLengthCodes[sequenceCount - 1]]);
        blockStream.addBits(sequences.matchLengths[sequenceCount - 1], MATCH_LENGTH_BITS[matchLengthCodes[sequenceCount - 1]]);
        blockStream.addBits(sequences.offsets[sequenceCount - 1], offsetCodes[sequenceCount - 1]);
        blockStream.flush();

        if (sequenceCount >= 2) {
            for (int n = sequenceCount - 2; n >= 0; n--) {
                byte literalLengthCode = literalLengthCodes[n];
                byte offsetCode = offsetCodes[n];
                byte matchLengthCode = matchLengthCodes[n];

                int literalLengthBits = LITERALS_LENGTH_BITS[literalLengthCode];
                int offsetBits = offsetCode;
                int matchLengthBits = MATCH_LENGTH_BITS[matchLengthCode];

                // (7)
                offsetState = offsetsTable.encode(blockStream, offsetState, offsetCode); // 15
                matchLengthState = matchLengthTable.encode(blockStream, matchLengthState, matchLengthCode); // 24
                literalLengthState = literalLengthTable.encode(blockStream, literalLengthState, literalLengthCode); // 33

                if ((offsetBits + matchLengthBits + literalLengthBits >= 64 - 7 - (LITERAL_LENGTH_TABLE_LOG + MATCH_LENGTH_TABLE_LOG + OFFSET_TABLE_LOG))) {
                    blockStream.flush();                                /* (7)*/
                }

                blockStream.addBits(sequences.literalLengths[n], literalLengthBits);
                if (((literalLengthBits + matchLengthBits) > 24)) {
                    blockStream.flush();
                }

                blockStream.addBits(sequences.matchLengths[n], matchLengthBits);
                if ((offsetBits + matchLengthBits + literalLengthBits > 56)) {
                    blockStream.flush();
                }

                blockStream.addBits(sequences.offsets[n], offsetBits); // 31
                blockStream.flush(); // (7)
            }
        }

        matchLengthTable.finish(blockStream, matchLengthState);
        offsetsTable.finish(blockStream, offsetState);
        literalLengthTable.finish(blockStream, literalLengthState);

        int streamSize = blockStream.close();
        checkArgument(streamSize > 0, "Output buffer too small");

        return streamSize;
    }

    private static int selectEncodingType(
            int largestCount,
            int sequenceCount,
            int defaultNormalizedCountsLog,
            boolean isDefaultTableAllowed,
            CompressionParameters.Strategy strategy)
    {
        if (largestCount == sequenceCount) { // => all entries are equal
            if (isDefaultTableAllowed && sequenceCount <= 2) {
                /* Prefer set_basic over set_rle when there are 2 or fewer symbols,
                 * since RLE uses 1 byte, but set_basic uses 5-6 bits per symbol.
                 * If basic encoding isn't possible, always choose RLE.
                 */
                return SEQUENCE_ENCODING_BASIC;
            }

            return SEQUENCE_ENCODING_RLE;
        }

        if (strategy.ordinal() < CompressionParameters.Strategy.LAZY.ordinal()) { // TODO: more robust check. Maybe encapsulate in strategy objects
            if (isDefaultTableAllowed) {
                int factor = 10 - strategy.ordinal(); // TODO more robust. Move it to strategy
                int baseLog = 3;
                long minNumberOfSequences = ((1L << defaultNormalizedCountsLog) * factor) >> baseLog;  /* 28-36 for offset, 56-72 for lengths */

                if ((sequenceCount < minNumberOfSequences) || (largestCount < (sequenceCount >> (defaultNormalizedCountsLog - 1)))) {
                    /* The format allows default tables to be repeated, but it isn't useful.
                     * When using simple heuristics to select encoding type, we don't want
                     * to confuse these tables with dictionaries. When running more careful
                     * analysis, we don't need to waste time checking both repeating tables
                     * and default tables.
                     */
                    return SEQUENCE_ENCODING_BASIC;
                }
            }
        }
        else {
            // TODO implement when other strategies are supported
            throw new UnsupportedOperationException("not yet implemented");
        }

        return SEQUENCE_ENCODING_COMPRESSED;
    }
}
