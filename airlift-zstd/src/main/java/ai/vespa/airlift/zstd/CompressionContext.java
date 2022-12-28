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

import static ai.vespa.airlift.zstd.Constants.MAX_BLOCK_SIZE;

class CompressionContext
{
    public final RepeatedOffsets offsets = new RepeatedOffsets();
    public final BlockCompressionState blockCompressionState;
    public final SequenceStore sequenceStore;

    public final SequenceEncodingContext sequenceEncodingContext = new SequenceEncodingContext();

    public final HuffmanCompressionContext huffmanContext = new HuffmanCompressionContext();

    public CompressionContext(CompressionParameters parameters, long baseAddress, int inputSize)
    {
        int windowSize = Math.max(1, Math.min(1 << parameters.getWindowLog(), inputSize));
        int blockSize = Math.min(MAX_BLOCK_SIZE, windowSize);
        int divider = (parameters.getSearchLength() == 3) ? 3 : 4;

        int maxSequences = blockSize / divider;

        sequenceStore = new SequenceStore(blockSize, maxSequences);

        blockCompressionState = new BlockCompressionState(parameters, baseAddress);
    }

    public void commit()
    {
        offsets.commit();
        huffmanContext.saveChanges();
    }
}
