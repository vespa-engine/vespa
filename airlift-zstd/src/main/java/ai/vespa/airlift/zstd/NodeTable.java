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

class NodeTable
{
    int[] count;
    short[] parents;
    int[] symbols;
    byte[] numberOfBits;

    public NodeTable(int size)
    {
        count = new int[size];
        parents = new short[size];
        symbols = new int[size];
        numberOfBits = new byte[size];
    }

    public void reset()
    {
        Arrays.fill(count, 0);
        Arrays.fill(parents, (short) 0);
        Arrays.fill(symbols, 0);
        Arrays.fill(numberOfBits, (byte) 0);
    }

    public void copyNode(int from, int to)
    {
        count[to] = count[from];
        parents[to] = parents[from];
        symbols[to] = symbols[from];
        numberOfBits[to] = numberOfBits[from];
    }
}
