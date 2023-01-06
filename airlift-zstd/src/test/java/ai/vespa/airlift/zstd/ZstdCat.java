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

import java.io.IOException;
import java.io.InputStream;

/*
 * Simple test implementation of "zstdcat".
 * @author arnej27959
 */
public class ZstdCat
{
    private ZstdCat() {}

    public static void main(String[] args)
    {
        try {
            InputStream i = new ZstdInputStream(System.in);
            byte[] buf = new byte[100 * 1024];
            int rl = 0;
            do {
                rl = i.read(buf);
                if (rl > 0) {
                    System.out.write(buf, 0, rl);
                }
            } while (rl > 0);
        }
        catch (IOException e) {
            System.err.println("IO failed" + e);
        }
    }
}
