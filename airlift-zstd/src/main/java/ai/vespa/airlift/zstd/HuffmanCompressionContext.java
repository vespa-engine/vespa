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

class HuffmanCompressionContext
{
    private final HuffmanTableWriterWorkspace tableWriterWorkspace = new HuffmanTableWriterWorkspace();
    private final HuffmanCompressionTableWorkspace compressionTableWorkspace = new HuffmanCompressionTableWorkspace();

    private HuffmanCompressionTable previousTable = new HuffmanCompressionTable(Huffman.MAX_SYMBOL_COUNT);
    private HuffmanCompressionTable temporaryTable = new HuffmanCompressionTable(Huffman.MAX_SYMBOL_COUNT);

    private HuffmanCompressionTable previousCandidate = previousTable;
    private HuffmanCompressionTable temporaryCandidate = temporaryTable;

    public HuffmanCompressionTable getPreviousTable()
    {
        return previousTable;
    }

    public HuffmanCompressionTable borrowTemporaryTable()
    {
        previousCandidate = temporaryTable;
        temporaryCandidate = previousTable;

        return temporaryTable;
    }

    public void discardTemporaryTable()
    {
        previousCandidate = previousTable;
        temporaryCandidate = temporaryTable;
    }

    public void saveChanges()
    {
        temporaryTable = temporaryCandidate;
        previousTable = previousCandidate;
    }

    public HuffmanCompressionTableWorkspace getCompressionTableWorkspace()
    {
        return compressionTableWorkspace;
    }

    public HuffmanTableWriterWorkspace getTableWriterWorkspace()
    {
        return tableWriterWorkspace;
    }
}
