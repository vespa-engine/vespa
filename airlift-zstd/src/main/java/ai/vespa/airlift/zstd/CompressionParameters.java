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

import static ai.vespa.airlift.zstd.Constants.MAX_WINDOW_LOG;
import static ai.vespa.airlift.zstd.Constants.MIN_WINDOW_LOG;
import static ai.vespa.airlift.zstd.Util.cycleLog;
import static ai.vespa.airlift.zstd.Util.highestBit;

class CompressionParameters
{
    private static final int MIN_HASH_LOG = 6;

    public static final int DEFAULT_COMPRESSION_LEVEL = 3;
    private static final int MAX_COMPRESSION_LEVEL = 22;

    private final int windowLog; // largest match distance : larger == more compression, more memory needed during decompression
    private final int chainLog;  // fully searched segment : larger == more compression, slower, more memory (useless for fast)
    private final int hashLog;   // dispatch table : larger == faster, more memory
    private final int searchLog; // nb of searches : larger == more compression, slower
    private final int searchLength; // match length searched : larger == faster decompression, sometimes less compression
    private final int targetLength; // acceptable match size for optimal parser (only) : larger == more compression, slower
    private final Strategy strategy;

    private static final CompressionParameters[][] DEFAULT_COMPRESSION_PARAMETERS = new CompressionParameters[][] {
            {
                // default
                new CompressionParameters(19, 12, 13, 1, 6, 1, Strategy.FAST),  /* base for negative levels */
                new CompressionParameters(19, 13, 14, 1, 7, 0, Strategy.FAST),  /* level  1 */
                new CompressionParameters(19, 15, 16, 1, 6, 0, Strategy.FAST),  /* level  2 */
                new CompressionParameters(20, 16, 17, 1, 5, 1, Strategy.DFAST),  /* level  3 */
                new CompressionParameters(20, 18, 18, 1, 5, 1, Strategy.DFAST),  /* level  4 */
                new CompressionParameters(20, 18, 18, 2, 5, 2, Strategy.GREEDY),  /* level  5 */
                new CompressionParameters(21, 18, 19, 2, 5, 4, Strategy.LAZY),  /* level  6 */
                new CompressionParameters(21, 18, 19, 3, 5, 8, Strategy.LAZY2),  /* level  7 */
                new CompressionParameters(21, 19, 19, 3, 5, 16, Strategy.LAZY2),  /* level  8 */
                new CompressionParameters(21, 19, 20, 4, 5, 16, Strategy.LAZY2),  /* level  9 */
                new CompressionParameters(21, 20, 21, 4, 5, 16, Strategy.LAZY2),  /* level 10 */
                new CompressionParameters(21, 21, 22, 4, 5, 16, Strategy.LAZY2),  /* level 11 */
                new CompressionParameters(22, 20, 22, 5, 5, 16, Strategy.LAZY2),  /* level 12 */
                new CompressionParameters(22, 21, 22, 4, 5, 32, Strategy.BTLAZY2),  /* level 13 */
                new CompressionParameters(22, 21, 22, 5, 5, 32, Strategy.BTLAZY2),  /* level 14 */
                new CompressionParameters(22, 22, 22, 6, 5, 32, Strategy.BTLAZY2),  /* level 15 */
                new CompressionParameters(22, 21, 22, 4, 5, 48, Strategy.BTOPT),  /* level 16 */
                new CompressionParameters(23, 22, 22, 4, 4, 64, Strategy.BTOPT),  /* level 17 */
                new CompressionParameters(23, 23, 22, 6, 3, 256, Strategy.BTOPT),  /* level 18 */
                new CompressionParameters(23, 24, 22, 7, 3, 256, Strategy.BTULTRA),  /* level 19 */
                new CompressionParameters(25, 25, 23, 7, 3, 256, Strategy.BTULTRA),  /* level 20 */
                new CompressionParameters(26, 26, 24, 7, 3, 512, Strategy.BTULTRA),  /* level 21 */
                new CompressionParameters(27, 27, 25, 9, 3, 999, Strategy.BTULTRA)  /* level 22 */
            },
            {
                // for size <= 256 KB
                new CompressionParameters(18, 12, 13, 1, 5, 1, Strategy.FAST),  /* base for negative levels */
                new CompressionParameters(18, 13, 14, 1, 6, 0, Strategy.FAST),  /* level  1 */
                new CompressionParameters(18, 14, 14, 1, 5, 1, Strategy.DFAST),  /* level  2 */
                new CompressionParameters(18, 16, 16, 1, 4, 1, Strategy.DFAST),  /* level  3 */
                new CompressionParameters(18, 16, 17, 2, 5, 2, Strategy.GREEDY),  /* level  4.*/
                new CompressionParameters(18, 18, 18, 3, 5, 2, Strategy.GREEDY),  /* level  5.*/
                new CompressionParameters(18, 18, 19, 3, 5, 4, Strategy.LAZY),  /* level  6.*/
                new CompressionParameters(18, 18, 19, 4, 4, 4, Strategy.LAZY),  /* level  7 */
                new CompressionParameters(18, 18, 19, 4, 4, 8, Strategy.LAZY2),  /* level  8 */
                new CompressionParameters(18, 18, 19, 5, 4, 8, Strategy.LAZY2),  /* level  9 */
                new CompressionParameters(18, 18, 19, 6, 4, 8, Strategy.LAZY2),  /* level 10 */
                new CompressionParameters(18, 18, 19, 5, 4, 16, Strategy.BTLAZY2),  /* level 11.*/
                new CompressionParameters(18, 19, 19, 6, 4, 16, Strategy.BTLAZY2),  /* level 12.*/
                new CompressionParameters(18, 19, 19, 8, 4, 16, Strategy.BTLAZY2),  /* level 13 */
                new CompressionParameters(18, 18, 19, 4, 4, 24, Strategy.BTOPT),  /* level 14.*/
                new CompressionParameters(18, 18, 19, 4, 3, 24, Strategy.BTOPT),  /* level 15.*/
                new CompressionParameters(18, 19, 19, 6, 3, 64, Strategy.BTOPT),  /* level 16.*/
                new CompressionParameters(18, 19, 19, 8, 3, 128, Strategy.BTOPT),  /* level 17.*/
                new CompressionParameters(18, 19, 19, 10, 3, 256, Strategy.BTOPT),  /* level 18.*/
                new CompressionParameters(18, 19, 19, 10, 3, 256, Strategy.BTULTRA),  /* level 19.*/
                new CompressionParameters(18, 19, 19, 11, 3, 512, Strategy.BTULTRA),  /* level 20.*/
                new CompressionParameters(18, 19, 19, 12, 3, 512, Strategy.BTULTRA),  /* level 21.*/
                new CompressionParameters(18, 19, 19, 13, 3, 999, Strategy.BTULTRA)  /* level 22.*/
            },
            {
                // for size <= 128 KB
                new CompressionParameters(17, 12, 12, 1, 5, 1, Strategy.FAST),  /* base for negative levels */
                new CompressionParameters(17, 12, 13, 1, 6, 0, Strategy.FAST),  /* level  1 */
                new CompressionParameters(17, 13, 15, 1, 5, 0, Strategy.FAST),  /* level  2 */
                new CompressionParameters(17, 15, 16, 2, 5, 1, Strategy.DFAST),  /* level  3 */
                new CompressionParameters(17, 17, 17, 2, 4, 1, Strategy.DFAST),  /* level  4 */
                new CompressionParameters(17, 16, 17, 3, 4, 2, Strategy.GREEDY),  /* level  5 */
                new CompressionParameters(17, 17, 17, 3, 4, 4, Strategy.LAZY),  /* level  6 */
                new CompressionParameters(17, 17, 17, 3, 4, 8, Strategy.LAZY2),  /* level  7 */
                new CompressionParameters(17, 17, 17, 4, 4, 8, Strategy.LAZY2),  /* level  8 */
                new CompressionParameters(17, 17, 17, 5, 4, 8, Strategy.LAZY2),  /* level  9 */
                new CompressionParameters(17, 17, 17, 6, 4, 8, Strategy.LAZY2),  /* level 10 */
                new CompressionParameters(17, 17, 17, 7, 4, 8, Strategy.LAZY2),  /* level 11 */
                new CompressionParameters(17, 18, 17, 6, 4, 16, Strategy.BTLAZY2),  /* level 12 */
                new CompressionParameters(17, 18, 17, 8, 4, 16, Strategy.BTLAZY2),  /* level 13.*/
                new CompressionParameters(17, 18, 17, 4, 4, 32, Strategy.BTOPT),  /* level 14.*/
                new CompressionParameters(17, 18, 17, 6, 3, 64, Strategy.BTOPT),  /* level 15.*/
                new CompressionParameters(17, 18, 17, 7, 3, 128, Strategy.BTOPT),  /* level 16.*/
                new CompressionParameters(17, 18, 17, 7, 3, 256, Strategy.BTOPT),  /* level 17.*/
                new CompressionParameters(17, 18, 17, 8, 3, 256, Strategy.BTOPT),  /* level 18.*/
                new CompressionParameters(17, 18, 17, 8, 3, 256, Strategy.BTULTRA),  /* level 19.*/
                new CompressionParameters(17, 18, 17, 9, 3, 256, Strategy.BTULTRA),  /* level 20.*/
                new CompressionParameters(17, 18, 17, 10, 3, 256, Strategy.BTULTRA),  /* level 21.*/
                new CompressionParameters(17, 18, 17, 11, 3, 512, Strategy.BTULTRA)  /* level 22.*/
            },
            {
                // for size <= 16 KB
                new CompressionParameters(14, 12, 13, 1, 5, 1, Strategy.FAST),  /* base for negative levels */
                new CompressionParameters(14, 14, 15, 1, 5, 0, Strategy.FAST),  /* level  1 */
                new CompressionParameters(14, 14, 15, 1, 4, 0, Strategy.FAST),  /* level  2 */
                new CompressionParameters(14, 14, 14, 2, 4, 1, Strategy.DFAST),  /* level  3.*/
                new CompressionParameters(14, 14, 14, 4, 4, 2, Strategy.GREEDY),  /* level  4.*/
                new CompressionParameters(14, 14, 14, 3, 4, 4, Strategy.LAZY),  /* level  5.*/
                new CompressionParameters(14, 14, 14, 4, 4, 8, Strategy.LAZY2),  /* level  6 */
                new CompressionParameters(14, 14, 14, 6, 4, 8, Strategy.LAZY2),  /* level  7 */
                new CompressionParameters(14, 14, 14, 8, 4, 8, Strategy.LAZY2),  /* level  8.*/
                new CompressionParameters(14, 15, 14, 5, 4, 8, Strategy.BTLAZY2),  /* level  9.*/
                new CompressionParameters(14, 15, 14, 9, 4, 8, Strategy.BTLAZY2),  /* level 10.*/
                new CompressionParameters(14, 15, 14, 3, 4, 12, Strategy.BTOPT),  /* level 11.*/
                new CompressionParameters(14, 15, 14, 6, 3, 16, Strategy.BTOPT),  /* level 12.*/
                new CompressionParameters(14, 15, 14, 6, 3, 24, Strategy.BTOPT),  /* level 13.*/
                new CompressionParameters(14, 15, 15, 6, 3, 48, Strategy.BTOPT),  /* level 14.*/
                new CompressionParameters(14, 15, 15, 6, 3, 64, Strategy.BTOPT),  /* level 15.*/
                new CompressionParameters(14, 15, 15, 6, 3, 96, Strategy.BTOPT),  /* level 16.*/
                new CompressionParameters(14, 15, 15, 6, 3, 128, Strategy.BTOPT),  /* level 17.*/
                new CompressionParameters(14, 15, 15, 8, 3, 256, Strategy.BTOPT),  /* level 18.*/
                new CompressionParameters(14, 15, 15, 6, 3, 256, Strategy.BTULTRA),  /* level 19.*/
                new CompressionParameters(14, 15, 15, 8, 3, 256, Strategy.BTULTRA),  /* level 20.*/
                new CompressionParameters(14, 15, 15, 9, 3, 256, Strategy.BTULTRA),  /* level 21.*/
                new CompressionParameters(14, 15, 15, 10, 3, 512, Strategy.BTULTRA)  /* level 22.*/
            }
    };

    public enum Strategy
    {
        // from faster to stronger

        // YC: fast is a "single probe" strategy : at every position, we attempt to find a match, and give up if we don't find any. similar to lz4.
        FAST(BlockCompressor.UNSUPPORTED),

        // YC: double_fast is a 2 attempts strategies. They are not symmetrical by the way. One attempt is "normal" while the second one looks for "long matches". It was
        // empirically found that this was the best trade off. As can be guessed, it's slower than single-attempt, but find more and better matches, so compresses better.
        DFAST(new DoubleFastBlockCompressor()),

        // YC: greedy uses a hash chain strategy. Every position is hashed, and all positions with same hash are chained. The algorithm goes through all candidates. There are
        // diminishing returns in going deeper and deeper, so after a nb of attempts (which can be selected), it abandons the search. The best (longest) match wins. If there is
        // one winner, it's immediately encoded.
        GREEDY(BlockCompressor.UNSUPPORTED),

        // YC: lazy will do something similar to greedy, but will not encode immediately. It will search again at next position, in case it would find something better.
        // It's actually fairly common to have a small match at position p hiding a more worthy one at position p+1. This obviously increases the search workload. But the
        // resulting compressed stream generally contains larger matches, hence compresses better.
        LAZY(BlockCompressor.UNSUPPORTED),

        // YC: lazy2 is same as lazy, but deeper. It will search at P, P+1 and then P+2 in case it would find something even better. More workload. Better matches.
        LAZY2(BlockCompressor.UNSUPPORTED),

        // YC: btlazy2 is like lazy2, but trades the hash chain for a binary tree. This becomes necessary, as the nb of attempts becomes prohibitively expensive. The binary tree
        // complexity increases with log of search depth, instead of proportionally with search depth. So searching deeper in history quickly becomes the dominant operation.
        // btlazy2 cuts into that. But it costs 2x more memory. It's also relatively "slow", even when trying to cut its parameters to make it perform faster. So it's really
        // a high compression strategy.
        BTLAZY2(BlockCompressor.UNSUPPORTED),

        // YC: btopt is, well, a hell of lot more complex.
        // It will compute and find multiple matches per position, will dynamically compare every path from point P to P+N, reverse the graph to find cheapest path, iterate on
        // batches of overlapping matches, etc. It's much more expensive. But the compression ratio is also much better.
        BTOPT(BlockCompressor.UNSUPPORTED),

        // YC: btultra is about the same, but doesn't cut as many corners (btopt "abandons" more quickly unpromising little gains). Slower, stronger.
        BTULTRA(BlockCompressor.UNSUPPORTED);

        private final BlockCompressor compressor;

        Strategy(BlockCompressor compressor)
        {
            this.compressor = compressor;
        }

        public BlockCompressor getCompressor()
        {
            return compressor;
        }
    }

    public CompressionParameters(int windowLog, int chainLog, int hashLog, int searchLog, int searchLength, int targetLength, Strategy strategy)
    {
        this.windowLog = windowLog;
        this.chainLog = chainLog;
        this.hashLog = hashLog;
        this.searchLog = searchLog;
        this.searchLength = searchLength;
        this.targetLength = targetLength;
        this.strategy = strategy;
    }

    public int getWindowLog()
    {
        return windowLog;
    }

    public int getSearchLength()
    {
        return searchLength;
    }

    public int getChainLog()
    {
        return chainLog;
    }

    public int getHashLog()
    {
        return hashLog;
    }

    public int getSearchLog()
    {
        return searchLog;
    }

    public int getTargetLength()
    {
        return targetLength;
    }

    public Strategy getStrategy()
    {
        return strategy;
    }

    public static CompressionParameters compute(int compressionLevel, int inputSize)
    {
        CompressionParameters defaultParameters = getDefaultParameters(compressionLevel, inputSize);

        int targetLength = defaultParameters.targetLength;
        int windowLog = defaultParameters.windowLog;
        int chainLog = defaultParameters.chainLog;
        int hashLog = defaultParameters.hashLog;
        int searchLog = defaultParameters.searchLog;
        int searchLength = defaultParameters.searchLength;
        Strategy strategy = defaultParameters.strategy;

        if (compressionLevel < 0) {
            targetLength = -compressionLevel;   // acceleration factor
        }

        // resize windowLog if input is small enough, to use less memory
        long maxWindowResize = 1L << (MAX_WINDOW_LOG - 1);
        if (inputSize < maxWindowResize) {
            int hashSizeMin = 1 << MIN_HASH_LOG;
            int inputSizeLog = (inputSize < hashSizeMin) ? MIN_HASH_LOG : highestBit(inputSize - 1) + 1;
            if (windowLog > inputSizeLog) {
                windowLog = inputSizeLog;
            }
        }

        if (hashLog > windowLog + 1) {
            hashLog = windowLog + 1;
        }

        int cycleLog = cycleLog(chainLog, strategy);
        if (cycleLog > windowLog) {
            chainLog -= (cycleLog - windowLog);
        }

        if (windowLog < MIN_WINDOW_LOG) {
            windowLog = MIN_WINDOW_LOG;
        }

        return new CompressionParameters(windowLog, chainLog, hashLog, searchLog, searchLength, targetLength, strategy);
    }

    private static CompressionParameters getDefaultParameters(int compressionLevel, long estimatedInputSize)
    {
        int table = 0;

        if (estimatedInputSize != 0) {
            if (estimatedInputSize <= 16 * 1024) {
                table = 3;
            }
            else if (estimatedInputSize <= 128 * 1024) {
                table = 2;
            }
            else if (estimatedInputSize <= 256 * 1024) {
                table = 1;
            }
        }

        int row = DEFAULT_COMPRESSION_LEVEL;

        if (compressionLevel != 0) { // TODO: figure out better way to indicate default compression level
            row = Math.min(Math.max(0, compressionLevel), MAX_COMPRESSION_LEVEL);
        }

        return DEFAULT_COMPRESSION_PARAMETERS[table][row];
    }
}
