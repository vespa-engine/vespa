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

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import ai.vespa.airlift.compress.benchmark.DataSet;
import org.openjdk.jmh.annotations.Param;

import javax.inject.Singleton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TestingModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
    }

    @Provides
    @Singleton
    public List<DataSet> dataSets()
            throws NoSuchFieldException, IOException
    {
        String[] testNames = DataSet.class
                .getDeclaredField("name")
                .getAnnotation(Param.class)
                .value();

        List<DataSet> result = new ArrayList<>();
        for (String testName : testNames) {
            DataSet entry = new DataSet(testName);
            entry.loadFile();
            result.add(entry);
        }

        return result;
    }
}
