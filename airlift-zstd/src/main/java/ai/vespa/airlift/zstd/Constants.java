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

class Constants
{
    public static final int SIZE_OF_BYTE = 1;
    public static final int SIZE_OF_SHORT = 2;
    public static final int SIZE_OF_INT = 4;
    public static final int SIZE_OF_LONG = 8;

    public static final int MAGIC_NUMBER = 0xFD2FB528;
    public static final int MAGIC_SKIPFRAME_MIN = 0x184D2A50;
    public static final int MAGIC_SKIPFRAME_MAX = 0x184D2A5F;

    public static final int MIN_WINDOW_LOG = 10;
    public static final int MAX_WINDOW_LOG = 31;

    public static final int SIZE_OF_BLOCK_HEADER = 3;

    public static final int MIN_SEQUENCES_SIZE = 1;
    public static final int MIN_BLOCK_SIZE = 1 // block type tag
            + 1 // min size of raw or rle length header
            + MIN_SEQUENCES_SIZE;
    public static final int MAX_BLOCK_SIZE = 128 * 1024;

    public static final int REPEATED_OFFSET_COUNT = 3;

    // block types
    public static final int RAW_BLOCK = 0;
    public static final int RLE_BLOCK = 1;
    public static final int COMPRESSED_BLOCK = 2;

    // sequence encoding types
    public static final int SEQUENCE_ENCODING_BASIC = 0;
    public static final int SEQUENCE_ENCODING_RLE = 1;
    public static final int SEQUENCE_ENCODING_COMPRESSED = 2;
    public static final int SEQUENCE_ENCODING_REPEAT = 3;

    public static final int MAX_LITERALS_LENGTH_SYMBOL = 35;
    public static final int MAX_MATCH_LENGTH_SYMBOL = 52;
    public static final int MAX_OFFSET_CODE_SYMBOL = 31;
    public static final int DEFAULT_MAX_OFFSET_CODE_SYMBOL = 28;

    public static final int LITERAL_LENGTH_TABLE_LOG = 9;
    public static final int MATCH_LENGTH_TABLE_LOG = 9;
    public static final int OFFSET_TABLE_LOG = 8;

    // literal block types
    public static final int RAW_LITERALS_BLOCK = 0;
    public static final int RLE_LITERALS_BLOCK = 1;
    public static final int COMPRESSED_LITERALS_BLOCK = 2;
    public static final int TREELESS_LITERALS_BLOCK = 3;

    public static final int LONG_NUMBER_OF_SEQUENCES = 0x7F00;

    public static final int[] LITERALS_LENGTH_BITS = {0, 0, 0, 0, 0, 0, 0, 0,
                                                      0, 0, 0, 0, 0, 0, 0, 0,
                                                      1, 1, 1, 1, 2, 2, 3, 3,
                                                      4, 6, 7, 8, 9, 10, 11, 12,
                                                      13, 14, 15, 16};

    public static final int[] MATCH_LENGTH_BITS = {0, 0, 0, 0, 0, 0, 0, 0,
                                                   0, 0, 0, 0, 0, 0, 0, 0,
                                                   0, 0, 0, 0, 0, 0, 0, 0,
                                                   0, 0, 0, 0, 0, 0, 0, 0,
                                                   1, 1, 1, 1, 2, 2, 3, 3,
                                                   4, 4, 5, 7, 8, 9, 10, 11,
                                                   12, 13, 14, 15, 16};

    private Constants()
    {
    }
}
