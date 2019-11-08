// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.api.systemflags.v1;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.RawFlag;
import com.yahoo.vespa.flags.json.FlagData;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author bjorncs
 */
public class SystemFlagsDataArchiveTest {

    private static final SystemName SYSTEM = SystemName.main;

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static FlagsTarget mainControllerTarget = FlagsTarget.forController(SYSTEM);
    private static FlagsTarget prodUsWestCfgTarget = createConfigserverTarget(Environment.prod, "us-west-1");
    private static FlagsTarget prodUsEast3CfgTarget = createConfigserverTarget(Environment.prod, "us-east-3");
    private static FlagsTarget devUsEast1CfgTarget = createConfigserverTarget(Environment.dev, "us-east-1");

    private static FlagsTarget createConfigserverTarget(Environment environment, String region) {
        return new ConfigServerFlagsTarget(
                SYSTEM,
                ZoneId.from(environment, RegionName.from(region)),
                URI.create("https://cfg-" + region),
                new AthenzService("vespa.cfg-" + region));
    }

    @Test
    public void can_serialize_and_deserialize_archive() throws IOException {
        File tempFile = temporaryFolder.newFile("serialized-flags-archive");
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(tempFile))) {
            var archive = SystemFlagsDataArchive.fromDirectory(Paths.get("src/test/resources/system-flags/"));
            archive.toZip(out);
        }
        try (InputStream in = new BufferedInputStream(new FileInputStream(tempFile))) {
            SystemFlagsDataArchive archive = SystemFlagsDataArchive.fromZip(in);
            assertArchiveReturnsCorrectDataForTarget(archive);
        }
    }

    @Test
    public void retrieves_correct_flag_data_for_target() {
        var archive = SystemFlagsDataArchive.fromDirectory(Paths.get("src/test/resources/system-flags/"));
        assertArchiveReturnsCorrectDataForTarget(archive);
    }

    private static void assertArchiveReturnsCorrectDataForTarget(SystemFlagsDataArchive archive) {
        assertFlagDataHasValue(archive, mainControllerTarget, "main.controller");
        assertFlagDataHasValue(archive, prodUsWestCfgTarget, "main.prod.us-west-1.json");
        assertFlagDataHasValue(archive, prodUsEast3CfgTarget, "main.prod");
        assertFlagDataHasValue(archive, devUsEast1CfgTarget, "main");
    }

    private static void assertFlagDataHasValue(SystemFlagsDataArchive archive, FlagsTarget target, String value) {
        Set<FlagData> data = archive.flagData(target);
        assertThat(data).hasSize(1);
        FlagData flagData = data.iterator().next();
        RawFlag rawFlag = flagData.resolve(FetchVector.fromMap(Map.of())).get();
        assertThat(rawFlag.asJson()).isEqualTo(String.format("\"%s\"", value));
    }

}