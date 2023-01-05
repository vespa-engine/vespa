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
package ai.vespa.airlift.compress;

import ai.vespa.airlift.compress.benchmark.DataSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TestingModule
{
    public static List<DataSet> dataSets()
    {
        String[] testNames = DataSet.knownDataSets;

        List<DataSet> result = new ArrayList<>();
        for (String testName : testNames) {
            DataSet entry = new DataSet(testName);
            try {
                entry.loadFile();
            } catch (java.io.IOException ex) {
                throw new IllegalStateException("could not load dataset " + testName, ex);
            }
            result.add(entry);
        }

        return result;
    }
}
