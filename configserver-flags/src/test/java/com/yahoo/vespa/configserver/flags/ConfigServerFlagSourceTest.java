// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.configserver.flags;

import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.RawFlag;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author hakonhall
 */
public class ConfigServerFlagSourceTest {
    private final String vespaHome = Defaults.getDefaults().vespaHome();
    private final FileSystem fileSystem = TestFileSystem.create();
    private final FlagsDb flagsDb = mock(FlagsDb.class);
    private final FlagId flagId = new FlagId("fid");

    private Flags.Replacer flagsReplacer;
    private ConfigServerFlagSource flagSource;
    private BooleanFlag flag;

    @BeforeEach
    public void setUp() {
        flagsReplacer = Flags.clearFlagsForTesting();
    }

    @AfterEach
    public void tearDown() {
        flagsReplacer.close();
    }

    private void initialize() {
        flagSource = new ConfigServerFlagSource(fileSystem, flagsDb);
        flag = Flags.defineFeatureFlag(flagId.toString(), false, List.of("joe"), "2010-01-01", "2030-01-01", "", "").bindTo(flagSource);
    }

    @Test
    void testAbsentInFileSystemForwardsToFlagsDb() {
        initialize();

        when(flagsDb.getValue(flagId)).thenReturn(Optional.empty());
        Optional<RawFlag> rawFlag = flagSource.fetch(flagId, new FetchVector());
        assertFalse(rawFlag.isPresent());
        verify(flagsDb, times(1)).getValue(flagId);
    }

    @Test
    void testAvoidingZooKeeperWhenOverridingInFile() throws IOException {
        // Here is how to set the value of a flag, such that ZooKeeper will NOT be queried when getting that value:
        //  - Make a flag.db file with the override
        Path flagPath = fileSystem.getPath(vespaHome + "/var/vespa/flag.db");
        String json = "{\n" +
                "    \"flags\": [\n" +
                "        {\n" +
                "            \"id\" : \"fid\",\n" +
                "            \"rules\" : [\n" +
                "                {\n" +
                "                    \"value\" : true\n" +
                "                }\n" +
                "            ]\n" +
                "        }\n" +
                "    ]\n" +
                "}";
        Files.createDirectories(flagPath.getParent());
        Files.write(flagPath, json.getBytes(StandardCharsets.UTF_8));

        // Alright, verify we have accomplished overriding that flag and avoid flagDb/ZooKeeper

        initialize();

        Optional<RawFlag> rawFlag = flagSource.fetch(flagId, new FetchVector());
        assertTrue(rawFlag.isPresent());
        assertTrue(flag.value());

        verify(flagsDb, times(0)).getValue(any());

        // Other flags DO hit flagDb/ZooKeeper

        FlagId flagId2 = new FlagId("fooId");
        when(flagsDb.getValue(flagId2)).thenReturn(Optional.empty());
        Optional<RawFlag> rawFlag2 = flagSource.fetch(flagId2, new FetchVector());
        assertFalse(rawFlag2.isPresent());
        verify(flagsDb, times(1)).getValue(flagId2);
    }
}
