// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.file;

import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.hamcrest.collection.IsMapContaining;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.yahoo.yolean.Exceptions.uncheck;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author hakonhall
 */
public class FlagDbFileTest {
    private final FileSystem fileSystem = TestFileSystem.create();
    private final FlagDbFile flagDb = new FlagDbFile(fileSystem);

    @Test
    void test() {
        Map<FlagId, FlagData> dataMap = new HashMap<>();
        FlagId id1 = new FlagId("id1");
        FlagData data1 = new FlagData(id1, new FetchVector());
        dataMap.put(id1, data1);
        FlagId id2 = new FlagId("id2");
        FlagData data2 = new FlagData(id2, new FetchVector());
        dataMap.put(id2, data2);

        // Non-existing directory => empty map
        assertTrue(flagDb.read().isEmpty());

        // sync() will create directory with map content
        assertThat(flagDb.sync(dataMap), equalTo(true));
        Map<FlagId, FlagData> readDataMap = flagDb.read();
        assertEquals(2, readDataMap.size());
        assertThat(readDataMap, IsMapContaining.hasKey(id1));
        assertThat(readDataMap, IsMapContaining.hasKey(id2));

        assertThat(getDbContent(), equalTo("{\"flags\":[{\"id\":\"id1\"},{\"id\":\"id2\"}]}"));

        // another sync with the same data is a no-op
        assertThat(flagDb.sync(dataMap), equalTo(false));

        // Changing value of id1, removing id2, adding id3
        dataMap.remove(id2);
        FlagData newData1 = new FlagData(id1, new FetchVector().with(FetchVector.Dimension.HOSTNAME, "h1"));
        dataMap.put(id1, newData1);
        FlagId id3 = new FlagId("id3");
        FlagData data3 = new FlagData(id3, new FetchVector());
        dataMap.put(id3, data3);
        assertThat(flagDb.sync(dataMap), equalTo(true));
        Map<FlagId, FlagData> anotherReadDataMap = flagDb.read();
        assertEquals(2, anotherReadDataMap.size());
        assertThat(anotherReadDataMap, IsMapContaining.hasKey(id1));
        assertThat(anotherReadDataMap, IsMapContaining.hasKey(id3));
        assertThat(anotherReadDataMap.get(id1).serializeToJson(), equalTo("{\"id\":\"id1\",\"attributes\":{\"hostname\":\"h1\"}}"));

        assertThat(flagDb.sync(Collections.emptyMap()), equalTo(true));
        assertThat(getDbContent(), equalTo("{\"flags\":[]}"));
    }

    public String getDbContent() {
        return uncheck(() -> new String(Files.readAllBytes(flagDb.getPath()), StandardCharsets.UTF_8));
    }
}