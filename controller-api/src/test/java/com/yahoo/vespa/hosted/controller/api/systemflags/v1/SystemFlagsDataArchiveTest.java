// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.api.systemflags.v1;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.RawFlag;
import com.yahoo.vespa.flags.json.FlagData;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author bjorncs
 */
public class SystemFlagsDataArchiveTest {

    private static final SystemName SYSTEM = SystemName.main;
    private static final FlagId MY_TEST_FLAG = new FlagId("my-test-flag");
    private static final FlagId FLAG_WITH_EMPTY_DATA = new FlagId("flag-with-empty-data");

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

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
            assertArchiveReturnsCorrectTestFlagDataForTarget(archive);
        }
    }

    @Test
    public void retrieves_correct_flag_data_for_target() {
        var archive = SystemFlagsDataArchive.fromDirectory(Paths.get("src/test/resources/system-flags/"));
        assertArchiveReturnsCorrectTestFlagDataForTarget(archive);
    }

    @Test
    public void empty_files_are_handled_as_no_flag_data_for_target() {
        var archive = SystemFlagsDataArchive.fromDirectory(Paths.get("src/test/resources/system-flags/"));
        assertNoFlagData(archive, FLAG_WITH_EMPTY_DATA, mainControllerTarget);
        assertFlagDataHasValue(archive, FLAG_WITH_EMPTY_DATA, prodUsWestCfgTarget, "main.prod.us-west-1");
        assertNoFlagData(archive, FLAG_WITH_EMPTY_DATA, prodUsEast3CfgTarget);
        assertFlagDataHasValue(archive, FLAG_WITH_EMPTY_DATA, devUsEast1CfgTarget, "main");
    }

    @Test
    public void throws_exception_on_non_json_file() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Only JSON files are allowed in 'flags/' directory (found 'flags/my-test-flag/file-name-without-dot-json')");
        SystemFlagsDataArchive.fromDirectory(Paths.get("src/test/resources/system-flags-with-invalid-file-name/"));
    }

    @Test
    public void throws_exception_on_unknown_file() {
        SystemFlagsDataArchive archive = SystemFlagsDataArchive.fromDirectory(Paths.get("src/test/resources/system-flags-with-unknown-file-name/"));
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Unknown flag file: flags/my-test-flag/main.prod.unknown-region.json");
        archive.validateAllFilesAreForTargets(SystemName.main, Set.of(mainControllerTarget, prodUsWestCfgTarget));
    }

    private static void assertArchiveReturnsCorrectTestFlagDataForTarget(SystemFlagsDataArchive archive) {
        assertFlagDataHasValue(archive, MY_TEST_FLAG, mainControllerTarget, "main.controller");
        assertFlagDataHasValue(archive, MY_TEST_FLAG, prodUsWestCfgTarget, "main.prod.us-west-1");
        assertFlagDataHasValue(archive, MY_TEST_FLAG, prodUsEast3CfgTarget, "main.prod");
        assertFlagDataHasValue(archive, MY_TEST_FLAG, devUsEast1CfgTarget, "main");
    }

    private static void assertFlagDataHasValue(SystemFlagsDataArchive archive, FlagId flagId, FlagsTarget target, String value) {
        List<FlagData> data = getData(archive, flagId, target);
        assertThat(data).hasSize(1);
        FlagData flagData = data.get(0);
        RawFlag rawFlag = flagData.resolve(FetchVector.fromMap(Map.of())).get();
        assertThat(rawFlag.asJson()).isEqualTo(String.format("\"%s\"", value));
    }

    private static void assertNoFlagData(SystemFlagsDataArchive archive, FlagId flagId, FlagsTarget target) {
        List<FlagData> data = getData(archive, flagId, target);
        assertThat(data).isEmpty();
    }

    private static List<FlagData> getData(SystemFlagsDataArchive archive, FlagId flagId, FlagsTarget target) {
        return archive.flagData(target).stream()
                .filter(d -> d.id().equals(flagId))
                .collect(toList());
    }

}