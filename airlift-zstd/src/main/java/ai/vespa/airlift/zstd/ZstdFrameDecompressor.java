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

import static ai.vespa.airlift.zstd.Constants.COMPRESSED_BLOCK;
import static ai.vespa.airlift.zstd.Constants.MAGIC_NUMBER;
import static ai.vespa.airlift.zstd.Constants.MIN_WINDOW_LOG;
import static ai.vespa.airlift.zstd.Constants.RAW_BLOCK;
import static ai.vespa.airlift.zstd.Constants.RLE_BLOCK;
import static ai.vespa.airlift.zstd.Constants.SIZE_OF_BLOCK_HEADER;
import static ai.vespa.airlift.zstd.Constants.SIZE_OF_BYTE;
import static ai.vespa.airlift.zstd.Constants.SIZE_OF_INT;
import static ai.vespa.airlift.zstd.Constants.SIZE_OF_LONG;
import static ai.vespa.airlift.zstd.Constants.SIZE_OF_SHORT;
import static ai.vespa.airlift.zstd.UnsafeUtil.UNSAFE;
import static ai.vespa.airlift.zstd.Util.fail;
import static ai.vespa.airlift.zstd.Util.verify;

class ZstdFrameDecompressor
{
    private static final int V07_MAGIC_NUMBER = 0xFD2FB527;

    public int decompress(
            final Object inputBase,
            final long inputAddress,
            final long inputLimit,
            final Object outputBase,
            final long outputAddress,
            final long outputLimit)
    {
        if (outputAddress == outputLimit) {
            return 0;
        }
        long input = inputAddress;
        long output = outputAddress;

        while (input < inputLimit) {
            long outputStart = output;
            input += verifyMagic(inputBase, input, inputLimit);

            FrameHeader frameHeader = readFrameHeader(inputBase, input, inputLimit);
            input += frameHeader.headerSize;

            ZstdBlockDecompressor blockDecompressor = new ZstdBlockDecompressor(frameHeader);
            boolean lastBlock;
            do {
                verify(input + SIZE_OF_BLOCK_HEADER <= inputLimit, input, "Not enough input bytes");

                // read block header
                int header = UNSAFE.getInt(inputBase, input) & 0xFF_FFFF;
                input += SIZE_OF_BLOCK_HEADER;

                lastBlock = (header & 1) != 0;
                int blockType = (header >>> 1) & 0b11;
                int blockSize = (header >>> 3) & 0x1F_FFFF; // 21 bits

                int decodedSize;
                switch (blockType) {
                    case RAW_BLOCK:
                        verify(inputAddress + blockSize <= inputLimit, input, "Not enough input bytes");
                        decodedSize = ZstdBlockDecompressor.decodeRawBlock(inputBase, input, blockSize, outputBase, output, outputLimit);
                        input += blockSize;
                        break;
                    case RLE_BLOCK:
                        verify(inputAddress + 1 <= inputLimit, input, "Not enough input bytes");
                        decodedSize = ZstdBlockDecompressor.decodeRleBlock(blockSize, inputBase, input, outputBase, output, outputLimit);
                        input += 1;
                        break;
                    case COMPRESSED_BLOCK:
                        verify(inputAddress + blockSize <= inputLimit, input, "Not enough input bytes");
                        decodedSize = blockDecompressor.decodeCompressedBlock(inputBase, input, blockSize, outputBase, output, outputLimit, frameHeader.windowSize, outputAddress);
                        input += blockSize;
                        break;
                    default:
                        throw fail(input, "Invalid block type");
                }
                output += decodedSize;
            }
            while (!lastBlock);

            if (frameHeader.hasChecksum) {
                int decodedFrameSize = (int) (output - outputStart);

                long hash = XxHash64.hash(0, outputBase, outputStart, decodedFrameSize);

                int checksum = UNSAFE.getInt(inputBase, input);
                if (checksum != (int) hash) {
                    throw new MalformedInputException(input, String.format("Bad checksum. Expected: %s, actual: %s", Integer.toHexString(checksum), Integer.toHexString((int) hash)));
                }

                input += SIZE_OF_INT;
            }
        }

        return (int) (output - outputAddress);
    }

    static FrameHeader readFrameHeader(final Object inputBase, final long inputAddress, final long inputLimit)
    {
        long input = inputAddress;
        verify(input < inputLimit, input, "Not enough input bytes");

        int frameHeaderDescriptor = UNSAFE.getByte(inputBase, input++) & 0xFF;
        boolean singleSegment = (frameHeaderDescriptor & 0b100000) != 0;
        int dictionaryDescriptor = frameHeaderDescriptor & 0b11;
        int contentSizeDescriptor = frameHeaderDescriptor >>> 6;

        int headerSize = 1 +
                (singleSegment ? 0 : 1) +
                (dictionaryDescriptor == 0 ? 0 : (1 << (dictionaryDescriptor - 1))) +
                (contentSizeDescriptor == 0 ? (singleSegment ? 1 : 0) : (1 << contentSizeDescriptor));

        verify(headerSize <= inputLimit - inputAddress, input, "Not enough input bytes");

        // decode window size
        int windowSize = -1;
        if (!singleSegment) {
            int windowDescriptor = UNSAFE.getByte(inputBase, input++) & 0xFF;
            int exponent = windowDescriptor >>> 3;
            int mantissa = windowDescriptor & 0b111;

            int base = 1 << (MIN_WINDOW_LOG + exponent);
            windowSize = base + (base / 8) * mantissa;
        }

        // decode dictionary id
        long dictionaryId = -1;
        switch (dictionaryDescriptor) {
            case 1:
                dictionaryId = UNSAFE.getByte(inputBase, input) & 0xFF;
                input += SIZE_OF_BYTE;
                break;
            case 2:
                dictionaryId = UNSAFE.getShort(inputBase, input) & 0xFFFF;
                input += SIZE_OF_SHORT;
                break;
            case 3:
                dictionaryId = UNSAFE.getInt(inputBase, input) & 0xFFFF_FFFFL;
                input += SIZE_OF_INT;
                break;
        }
        verify(dictionaryId == -1, input, "Custom dictionaries not supported");

        // decode content size
        long contentSize = -1;
        switch (contentSizeDescriptor) {
            case 0:
                if (singleSegment) {
                    contentSize = UNSAFE.getByte(inputBase, input) & 0xFF;
                    input += SIZE_OF_BYTE;
                }
                break;
            case 1:
                contentSize = UNSAFE.getShort(inputBase, input) & 0xFFFF;
                contentSize += 256;
                input += SIZE_OF_SHORT;
                break;
            case 2:
                contentSize = UNSAFE.getInt(inputBase, input) & 0xFFFF_FFFFL;
                input += SIZE_OF_INT;
                break;
            case 3:
                contentSize = UNSAFE.getLong(inputBase, input);
                input += SIZE_OF_LONG;
                break;
        }

        boolean hasChecksum = (frameHeaderDescriptor & 0b100) != 0;

        return new FrameHeader(
                input - inputAddress,
                windowSize,
                contentSize,
                dictionaryId,
                hasChecksum);
    }

    public static long getDecompressedSize(final Object inputBase, final long inputAddress, final long inputLimit)
    {
        long input = inputAddress;
        input += verifyMagic(inputBase, input, inputLimit);
        return readFrameHeader(inputBase, input, inputLimit).contentSize;
    }

    static int verifyMagic(Object inputBase, long inputAddress, long inputLimit)
    {
        verify(inputLimit - inputAddress >= 4, inputAddress, "Not enough input bytes");

        int magic = UNSAFE.getInt(inputBase, inputAddress);
        if (magic != MAGIC_NUMBER) {
            if (magic == V07_MAGIC_NUMBER) {
                throw new MalformedInputException(inputAddress, "Data encoded in unsupported ZSTD v0.7 format");
            }
            throw new MalformedInputException(inputAddress, "Invalid magic prefix: " + Integer.toHexString(magic));
        }

        return SIZE_OF_INT;
    }
}
