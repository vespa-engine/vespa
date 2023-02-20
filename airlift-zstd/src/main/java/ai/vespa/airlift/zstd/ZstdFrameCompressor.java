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

import static ai.vespa.airlift.zstd.Constants.COMPRESSED_BLOCK;
import static ai.vespa.airlift.zstd.Constants.COMPRESSED_LITERALS_BLOCK;
import static ai.vespa.airlift.zstd.Constants.MAGIC_NUMBER;
import static ai.vespa.airlift.zstd.Constants.MAX_BLOCK_SIZE;
import static ai.vespa.airlift.zstd.Constants.MIN_BLOCK_SIZE;
import static ai.vespa.airlift.zstd.Constants.MIN_WINDOW_LOG;
import static ai.vespa.airlift.zstd.Constants.RAW_BLOCK;
import static ai.vespa.airlift.zstd.Constants.RAW_LITERALS_BLOCK;
import static ai.vespa.airlift.zstd.Constants.RLE_LITERALS_BLOCK;
import static ai.vespa.airlift.zstd.Constants.SIZE_OF_BLOCK_HEADER;
import static ai.vespa.airlift.zstd.Constants.SIZE_OF_INT;
import static ai.vespa.airlift.zstd.Constants.SIZE_OF_SHORT;
import static ai.vespa.airlift.zstd.Constants.TREELESS_LITERALS_BLOCK;
import static ai.vespa.airlift.zstd.Huffman.MAX_SYMBOL;
import static ai.vespa.airlift.zstd.Huffman.MAX_SYMBOL_COUNT;
import static ai.vespa.airlift.zstd.UnsafeUtil.UNSAFE;
import static ai.vespa.airlift.zstd.Util.checkArgument;
import static ai.vespa.airlift.zstd.Util.put24BitLittleEndian;
import static sun.misc.Unsafe.ARRAY_BYTE_BASE_OFFSET;

class ZstdFrameCompressor
{
    static final int MAX_FRAME_HEADER_SIZE = 14;

    private static final int CHECKSUM_FLAG = 0b100;
    private static final int SINGLE_SEGMENT_FLAG = 0b100000;

    private static final int MINIMUM_LITERALS_SIZE = 63;

    // the maximum table log allowed for literal encoding per RFC 8478, section 4.2.1
    private static final int MAX_HUFFMAN_TABLE_LOG = 11;

    private ZstdFrameCompressor()
    {
    }

    // visible for testing
    static int writeMagic(final Object outputBase, final long outputAddress, final long outputLimit)
    {
        checkArgument(outputLimit - outputAddress >= SIZE_OF_INT, "Output buffer too small");

        UNSAFE.putInt(outputBase, outputAddress, MAGIC_NUMBER);
        return SIZE_OF_INT;
    }

    // visible for testing
    static int writeFrameHeader(final Object outputBase, final long outputAddress, final long outputLimit, int inputSize, int windowSize)
    {
        checkArgument(outputLimit - outputAddress >= MAX_FRAME_HEADER_SIZE, "Output buffer too small");

        long output = outputAddress;

        int contentSizeDescriptor = (inputSize >= 256 ? 1 : 0) + (inputSize >= 65536 + 256 ? 1 : 0);
        int frameHeaderDescriptor = (contentSizeDescriptor << 6) | CHECKSUM_FLAG; // dictionary ID missing

        boolean singleSegment = windowSize >= inputSize;
        if (singleSegment) {
            frameHeaderDescriptor |= SINGLE_SEGMENT_FLAG;
        }

        UNSAFE.putByte(outputBase, output, (byte) frameHeaderDescriptor);
        output++;

        if (!singleSegment) {
            int base = Integer.highestOneBit(windowSize);

            int exponent = 32 - Integer.numberOfLeadingZeros(base) - 1;
            if (exponent < MIN_WINDOW_LOG) {
                throw new IllegalArgumentException("Minimum window size is " + (1 << MIN_WINDOW_LOG));
            }

            int remainder = windowSize - base;
            if (remainder % (base / 8) != 0) {
                throw new IllegalArgumentException("Window size of magnitude 2^" + exponent + " must be multiple of " + (base / 8));
            }

            // mantissa is guaranteed to be between 0-7
            int mantissa = remainder / (base / 8);
            int encoded = ((exponent - MIN_WINDOW_LOG) << 3) | mantissa;

            UNSAFE.putByte(outputBase, output, (byte) encoded);
            output++;
        }

        switch (contentSizeDescriptor) {
            case 0:
                if (singleSegment) {
                    UNSAFE.putByte(outputBase, output++, (byte) inputSize);
                }
                break;
            case 1:
                UNSAFE.putShort(outputBase, output, (short) (inputSize - 256));
                output += SIZE_OF_SHORT;
                break;
            case 2:
                UNSAFE.putInt(outputBase, output, inputSize);
                output += SIZE_OF_INT;
                break;
            default:
                throw new AssertionError();
        }

        return (int) (output - outputAddress);
    }

    // visible for testing
    static int writeChecksum(Object outputBase, long outputAddress, long outputLimit, Object inputBase, long inputAddress, long inputLimit)
    {
        checkArgument(outputLimit - outputAddress >= SIZE_OF_INT, "Output buffer too small");

        int inputSize = (int) (inputLimit - inputAddress);

        long hash = XxHash64.hash(0, inputBase, inputAddress, inputSize);

        UNSAFE.putInt(outputBase, outputAddress, (int) hash);

        return SIZE_OF_INT;
    }

    public static int compress(Object inputBase, long inputAddress, long inputLimit, Object outputBase, long outputAddress, long outputLimit, int compressionLevel)
    {
        int inputSize = (int) (inputLimit - inputAddress);

        CompressionParameters parameters = CompressionParameters.compute(compressionLevel, inputSize);

        long output = outputAddress;

        output += writeMagic(outputBase, output, outputLimit);
        output += writeFrameHeader(outputBase, output, outputLimit, inputSize, 1 << parameters.getWindowLog());
        output += compressFrame(inputBase, inputAddress, inputLimit, outputBase, output, outputLimit, parameters);
        output += writeChecksum(outputBase, output, outputLimit, inputBase, inputAddress, inputLimit);

        return (int) (output - outputAddress);
    }

    private static int compressFrame(Object inputBase, long inputAddress, long inputLimit, Object outputBase, long outputAddress, long outputLimit, CompressionParameters parameters)
    {
        int windowSize = 1 << parameters.getWindowLog(); // TODO: store window size in parameters directly?
        int blockSize = Math.min(MAX_BLOCK_SIZE, windowSize);

        int outputSize = (int) (outputLimit - outputAddress);
        int remaining = (int) (inputLimit - inputAddress);

        long output = outputAddress;
        long input = inputAddress;

        CompressionContext context = new CompressionContext(parameters, inputAddress, remaining);

        do {
            checkArgument(outputSize >= SIZE_OF_BLOCK_HEADER + MIN_BLOCK_SIZE, "Output buffer too small");

            int lastBlockFlag = blockSize >= remaining ? 1 : 0;
            blockSize = Math.min(blockSize, remaining);

            int compressedSize = 0;
            if (remaining > 0) {
                compressedSize = compressBlock(inputBase, input, blockSize, outputBase, output + SIZE_OF_BLOCK_HEADER, outputSize - SIZE_OF_BLOCK_HEADER, context, parameters);
            }

            if (compressedSize == 0) { // block is not compressible
                checkArgument(blockSize + SIZE_OF_BLOCK_HEADER <= outputSize, "Output size too small");

                int blockHeader = lastBlockFlag | (RAW_BLOCK << 1) | (blockSize << 3);
                put24BitLittleEndian(outputBase, output, blockHeader);
                UNSAFE.copyMemory(inputBase, input, outputBase, output + SIZE_OF_BLOCK_HEADER, blockSize);
                compressedSize = SIZE_OF_BLOCK_HEADER + blockSize;
            }
            else {
                int blockHeader = lastBlockFlag | (COMPRESSED_BLOCK << 1) | (compressedSize << 3);
                put24BitLittleEndian(outputBase, output, blockHeader);
                compressedSize += SIZE_OF_BLOCK_HEADER;
            }

            input += blockSize;
            remaining -= blockSize;
            output += compressedSize;
            outputSize -= compressedSize;
        }
        while (remaining > 0);

        return (int) (output - outputAddress);
    }

    private static int compressBlock(Object inputBase, long inputAddress, int inputSize, Object outputBase, long outputAddress, int outputSize, CompressionContext context, CompressionParameters parameters)
    {
        if (inputSize < MIN_BLOCK_SIZE + SIZE_OF_BLOCK_HEADER + 1) {
            //  don't even attempt compression below a certain input size
            return 0;
        }

        context.blockCompressionState.enforceMaxDistance(inputAddress + inputSize, 1 << parameters.getWindowLog());
        context.sequenceStore.reset();

        int lastLiteralsSize = parameters.getStrategy()
                .getCompressor()
                .compressBlock(inputBase, inputAddress, inputSize, context.sequenceStore, context.blockCompressionState, context.offsets, parameters);

        long lastLiteralsAddress = inputAddress + inputSize - lastLiteralsSize;

        // append [lastLiteralsAddress .. lastLiteralsSize] to sequenceStore literals buffer
        context.sequenceStore.appendLiterals(inputBase, lastLiteralsAddress, lastLiteralsSize);

        // convert length/offsets into codes
        context.sequenceStore.generateCodes();

        long outputLimit = outputAddress + outputSize;
        long output = outputAddress;

        int compressedLiteralsSize = encodeLiterals(
                context.huffmanContext,
                parameters,
                outputBase,
                output,
                (int) (outputLimit - output),
                context.sequenceStore.literalsBuffer,
                context.sequenceStore.literalsLength);
        output += compressedLiteralsSize;

        int compressedSequencesSize = SequenceEncoder.compressSequences(outputBase, output, (int) (outputLimit - output), context.sequenceStore, parameters.getStrategy(), context.sequenceEncodingContext);

        int compressedSize = compressedLiteralsSize + compressedSequencesSize;
        if (compressedSize == 0) {
            // not compressible
            return compressedSize;
        }

        // Check compressibility
        int maxCompressedSize = inputSize - calculateMinimumGain(inputSize, parameters.getStrategy());
        if (compressedSize > maxCompressedSize) {
            return 0; // not compressed
        }

        // confirm repeated offsets and entropy tables
        context.commit();

        return compressedSize;
    }

    private static int encodeLiterals(
            HuffmanCompressionContext context,
            CompressionParameters parameters,
            Object outputBase,
            long outputAddress,
            int outputSize,
            byte[] literals,
            int literalsSize)
    {
        // TODO: move this to Strategy
        boolean bypassCompression = (parameters.getStrategy() == CompressionParameters.Strategy.FAST) && (parameters.getTargetLength() > 0);
        if (bypassCompression || literalsSize <= MINIMUM_LITERALS_SIZE) {
            return rawLiterals(outputBase, outputAddress, outputSize, literals, ARRAY_BYTE_BASE_OFFSET, literalsSize);
        }

        int headerSize = 3 + (literalsSize >= 1024 ? 1 : 0) + (literalsSize >= 16384 ? 1 : 0);

        checkArgument(headerSize + 1 <= outputSize, "Output buffer too small");

        int[] counts = new int[MAX_SYMBOL_COUNT]; // TODO: preallocate
        Histogram.count(literals, literalsSize, counts);
        int maxSymbol = Histogram.findMaxSymbol(counts, MAX_SYMBOL);
        int largestCount = Histogram.findLargestCount(counts, maxSymbol);

        long literalsAddress = ARRAY_BYTE_BASE_OFFSET;
        if (largestCount == literalsSize) {
            // all bytes in input are equal
            return rleLiterals(outputBase, outputAddress, outputSize, literals, ARRAY_BYTE_BASE_OFFSET, literalsSize);
        }
        else if (largestCount <= (literalsSize >>> 7) + 4) {
            // heuristic: probably not compressible enough
            return rawLiterals(outputBase, outputAddress, outputSize, literals, ARRAY_BYTE_BASE_OFFSET, literalsSize);
        }

        HuffmanCompressionTable previousTable = context.getPreviousTable();
        HuffmanCompressionTable table;
        int serializedTableSize;
        boolean reuseTable;

        boolean canReuse = previousTable.isValid(counts, maxSymbol);

        // heuristic: use existing table for small inputs if valid
        // TODO: move to Strategy
        boolean preferReuse = parameters.getStrategy().ordinal() < CompressionParameters.Strategy.LAZY.ordinal() && literalsSize <= 1024;
        if (preferReuse && canReuse) {
            table = previousTable;
            reuseTable = true;
            serializedTableSize = 0;
        }
        else {
            HuffmanCompressionTable newTable = context.borrowTemporaryTable();

            newTable.initialize(
                    counts,
                    maxSymbol,
                    HuffmanCompressionTable.optimalNumberOfBits(MAX_HUFFMAN_TABLE_LOG, literalsSize, maxSymbol),
                    context.getCompressionTableWorkspace());

            serializedTableSize = newTable.write(outputBase, outputAddress + headerSize, outputSize - headerSize, context.getTableWriterWorkspace());

            // Check if using previous huffman table is beneficial
            if (canReuse && previousTable.estimateCompressedSize(counts, maxSymbol) <= serializedTableSize + newTable.estimateCompressedSize(counts, maxSymbol)) {
                table = previousTable;
                reuseTable = true;
                serializedTableSize = 0;
                context.discardTemporaryTable();
            }
            else {
                table = newTable;
                reuseTable = false;
            }
        }

        int compressedSize;
        boolean singleStream = literalsSize < 256;
        if (singleStream) {
            compressedSize = HuffmanCompressor.compressSingleStream(outputBase, outputAddress + headerSize + serializedTableSize, outputSize - headerSize - serializedTableSize, literals, literalsAddress, literalsSize, table);
        }
        else {
            compressedSize = HuffmanCompressor.compress4streams(outputBase, outputAddress + headerSize + serializedTableSize, outputSize - headerSize - serializedTableSize, literals, literalsAddress, literalsSize, table);
        }

        int totalSize = serializedTableSize + compressedSize;
        int minimumGain = calculateMinimumGain(literalsSize, parameters.getStrategy());

        if (compressedSize == 0 || totalSize >= literalsSize - minimumGain) {
            // incompressible or no savings

            // discard any temporary table we might have borrowed above
            context.discardTemporaryTable();

            return rawLiterals(outputBase, outputAddress, outputSize, literals, ARRAY_BYTE_BASE_OFFSET, literalsSize);
        }

        int encodingType = reuseTable ? TREELESS_LITERALS_BLOCK : COMPRESSED_LITERALS_BLOCK;

        // Build header
        switch (headerSize) {
            case 3: { // 2 - 2 - 10 - 10
                int header = encodingType | ((singleStream ? 0 : 1) << 2) | (literalsSize << 4) | (totalSize << 14);
                put24BitLittleEndian(outputBase, outputAddress, header);
                break;
            }
            case 4: { // 2 - 2 - 14 - 14
                int header = encodingType | (2 << 2) | (literalsSize << 4) | (totalSize << 18);
                UNSAFE.putInt(outputBase, outputAddress, header);
                break;
            }
            case 5: { // 2 - 2 - 18 - 18
                int header = encodingType | (3 << 2) | (literalsSize << 4) | (totalSize << 22);
                UNSAFE.putInt(outputBase, outputAddress, header);
                UNSAFE.putByte(outputBase, outputAddress + SIZE_OF_INT, (byte) (totalSize >>> 10));
                break;
            }
            default:  // not possible : headerSize is {3,4,5}
                throw new IllegalStateException();
        }

        return headerSize + totalSize;
    }

    private static int rleLiterals(Object outputBase, long outputAddress, int outputSize, Object inputBase, long inputAddress, int inputSize)
    {
        int headerSize = 1 + (inputSize > 31 ? 1 : 0) + (inputSize > 4095 ? 1 : 0);

        switch (headerSize) {
            case 1: // 2 - 1 - 5
                UNSAFE.putByte(outputBase, outputAddress, (byte) (RLE_LITERALS_BLOCK | (inputSize << 3)));
                break;
            case 2: // 2 - 2 - 12
                UNSAFE.putShort(outputBase, outputAddress, (short) (RLE_LITERALS_BLOCK | (1 << 2) | (inputSize << 4)));
                break;
            case 3: // 2 - 2 - 20
                UNSAFE.putInt(outputBase, outputAddress, RLE_LITERALS_BLOCK | 3 << 2 | inputSize << 4);
                break;
            default:   // impossible. headerSize is {1,2,3}
                throw new IllegalStateException();
        }

        UNSAFE.putByte(outputBase, outputAddress + headerSize, UNSAFE.getByte(inputBase, inputAddress));

        return headerSize + 1;
    }

    private static int calculateMinimumGain(int inputSize, CompressionParameters.Strategy strategy)
    {
        // TODO: move this to Strategy to avoid hardcoding a specific strategy here
        int minLog = strategy == CompressionParameters.Strategy.BTULTRA ? 7 : 6;
        return (inputSize >>> minLog) + 2;
    }

    private static int rawLiterals(Object outputBase, long outputAddress, int outputSize, Object inputBase, long inputAddress, int inputSize)
    {
        int headerSize = 1;
        if (inputSize >= 32) {
            headerSize++;
        }
        if (inputSize >= 4096) {
            headerSize++;
        }

        checkArgument(inputSize + headerSize <= outputSize, "Output buffer too small");

        switch (headerSize) {
            case 1:
                UNSAFE.putByte(outputBase, outputAddress, (byte) (RAW_LITERALS_BLOCK | (inputSize << 3)));
                break;
            case 2:
                UNSAFE.putShort(outputBase, outputAddress, (short) (RAW_LITERALS_BLOCK | (1 << 2) | (inputSize << 4)));
                break;
            case 3:
                put24BitLittleEndian(outputBase, outputAddress, RAW_LITERALS_BLOCK | (3 << 2) | (inputSize << 4));
                break;
            default:
                throw new AssertionError();
        }

        // TODO: ensure this test is correct
        checkArgument(inputSize + 1 <= outputSize, "Output buffer too small");

        UNSAFE.copyMemory(inputBase, inputAddress, outputBase, outputAddress + headerSize, inputSize);

        return headerSize + inputSize;
    }
}
