// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.file;

import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.hamcrest.collection.IsMapContaining;
import org.hamcrest.collection.IsMapWithSize;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static com.yahoo.yolean.Exceptions.uncheck;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * @author hakonhall
 */
public class FlagDirectoryTest {
    private final FileSystem fileSystem = TestFileSystem.create();
    private final FlagDirectory flagDirectory = new FlagDirectory(fileSystem);

    @Test
    public void testReadingOnly() {
        Map<FlagId, FlagData> data = flagDirectory.read();
        assertThat(data, IsMapWithSize.anEmptyMap());

        FlagId id1 = new FlagId("id1");
        String json1 = "{\"id\":\"id1\"}";
        writeUtf8FlagFile(id1.toString(), json1);
        data = flagDirectory.read();
        assertThat(data, IsMapWithSize.aMapWithSize(1));
        assertThat(data, IsMapContaining.hasKey(id1));
        assertThat(data.get(id1).id(), equalTo(id1));
        assertThat(data.get(id1).serializeToJson(), equalTo(json1));
    }

    @Test
    public void testSync() {
        Map<FlagId, FlagData> dataMap = new HashMap<>();
        FlagId id1 = new FlagId("id1");
        FlagData data1 = new FlagData(id1, new FetchVector());
        dataMap.put(id1, data1);
        FlagId id2 = new FlagId("id2");
        FlagData data2 = new FlagData(id2, new FetchVector());
        dataMap.put(id2, data2);

        // Non-existing directory => empty map
        assertThat(flagDirectory.read(), IsMapWithSize.anEmptyMap());

        // sync() will create directory with map content
        assertThat(flagDirectory.sync(dataMap), equalTo(true));
        Map<FlagId, FlagData> readDataMap = flagDirectory.read();
        assertThat(readDataMap, IsMapWithSize.aMapWithSize(2));
        assertThat(readDataMap, IsMapContaining.hasKey(id1));
        assertThat(readDataMap, IsMapContaining.hasKey(id2));

        // another sync with the same data is a no-op
        assertThat(flagDirectory.sync(dataMap), equalTo(false));

        // Changing value of id1, removing id2, adding id3
        dataMap.remove(id2);
        FlagData newData1 = new FlagData(id1, new FetchVector().with(FetchVector.Dimension.HOSTNAME, "h1"));
        dataMap.put(id1, newData1);
        FlagId id3 = new FlagId("id3");
        FlagData data3 = new FlagData(id3, new FetchVector());
        dataMap.put(id3, data3);
        assertThat(flagDirectory.sync(dataMap), equalTo(true));
        Map<FlagId, FlagData> anotherReadDataMap = flagDirectory.read();
        assertThat(anotherReadDataMap, IsMapWithSize.aMapWithSize(2));
        assertThat(anotherReadDataMap, IsMapContaining.hasKey(id1));
        assertThat(anotherReadDataMap, IsMapContaining.hasKey(id3));
        assertThat(anotherReadDataMap.get(id1).serializeToJson(), equalTo("{\"id\":\"id1\",\"attributes\":{\"hostname\":\"h1\"}}"));
    }

    private void writeUtf8FlagFile(String flagIdAkaFilename, String content) {
        uncheck(() -> Files.createDirectories(flagDirectory.getPath()));
        uncheck(() -> Files.write(flagDirectory.getPath().resolve(flagIdAkaFilename), content.getBytes(StandardCharsets.UTF_8)));
    }
}