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

class RepeatedOffsets
{
    private int offset0 = 1;
    private int offset1 = 4;

    private int tempOffset0;
    private int tempOffset1;

    public int getOffset0()
    {
        return offset0;
    }

    public int getOffset1()
    {
        return offset1;
    }

    public void saveOffset0(int offset)
    {
        tempOffset0 = offset;
    }

    public void saveOffset1(int offset)
    {
        tempOffset1 = offset;
    }

    public void commit()
    {
        offset0 = tempOffset0;
        offset1 = tempOffset1;
    }
}
