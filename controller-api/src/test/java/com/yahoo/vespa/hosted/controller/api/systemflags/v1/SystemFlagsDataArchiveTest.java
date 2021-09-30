// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.api.systemflags.v1;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.config.provision.zone.ZoneList;
import com.yahoo.text.JSON;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.RawFlag;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    private static final FlagsTarget mainControllerTarget = FlagsTarget.forController(SYSTEM);
    private static final FlagsTarget cdControllerTarget = FlagsTarget.forController(SystemName.cd);
    private static final FlagsTarget prodUsWestCfgTarget = createConfigserverTarget(Environment.prod, "us-west-1");
    private static final FlagsTarget prodUsEast3CfgTarget = createConfigserverTarget(Environment.prod, "us-east-3");
    private static final FlagsTarget devUsEast1CfgTarget = createConfigserverTarget(Environment.dev, "us-east-1");

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
    public void supports_multi_level_flags_directory() {
        var archive = SystemFlagsDataArchive.fromDirectory(Paths.get("src/test/resources/system-flags-multi-level/"));
        assertFlagDataHasValue(archive, MY_TEST_FLAG, mainControllerTarget, "default");
    }

    @Test
    public void duplicated_flagdata_is_detected() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("contains redundant flag data for id 'my-test-flag' already set in another directory!");
        var archive = SystemFlagsDataArchive.fromDirectory(Paths.get("src/test/resources/system-flags-multi-level-with-duplicated-flagdata/"));
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

    @Test
    public void throws_on_unknown_field() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(
                "flags/my-test-flag/main.prod.us-west-1.json contains unknown non-comment fields: after removing any comment fields the JSON is:\n" +
                "  {\"id\":\"my-test-flag\",\"rules\":[{\"condition\":[{\"type\":\"whitelist\",\"dimension\":\"hostname\",\"values\":[\"foo.com\"]}],\"value\":\"default\"}]}\n" +
                "but deserializing this ended up with a JSON that are missing some of the fields:\n" +
                "  {\"id\":\"my-test-flag\",\"rules\":[{\"value\":\"default\"}]}\n" +
                "See https://git.ouroath.com/vespa/hosted-feature-flags for more info on the JSON syntax");
        SystemFlagsDataArchive.fromDirectory(Paths.get("src/test/resources/system-flags-with-unknown-field-name/"));
    }

    @Test
    public void remove_comments() {
        assertTrue(JSON.equals("{\n" +
                "    \"a\": {\n" +
                "        \"b\": 1\n" +
                "    },\n" +
                "    \"list\": [\n" +
                "        {\n" +
                "            \"c\": 2\n" +
                "        },\n" +
                "        {\n" +
                "        }\n" +
                "    ]\n" +
                "}",
                SystemFlagsDataArchive.normalizeJson("{\n" +
                "    \"comment\": \"comment a\",\n" +
                "    \"a\": {\n" +
                "        \"comment\": \"comment b\",\n" +
                "        \"b\": 1\n" +
                "    },\n" +
                "    \"list\": [\n" +
                "        {\n" +
                "            \"comment\": \"comment c\",\n" +
                "            \"c\": 2\n" +
                "        },\n" +
                "        {\n" +
                "            \"comment\": \"comment d\"\n" +
                "        }\n" +
                "    ]\n" +
                "}")));
    }

    @Test
    public void normalize_json_fail_on_invalid_application() {
        try {
            SystemFlagsDataArchive.normalizeJson("{\n" +
                    "    \"id\": \"foo\",\n" +
                    "    \"rules\": [\n" +
                    "        {\n" +
                    "            \"conditions\": [\n" +
                    "                {\n" +
                    "                    \"type\": \"whitelist\",\n" +
                    "                    \"dimension\": \"application\",\n" +
                    "                    \"values\": [ \"a.b.c\" ]\n" +
                    "                }\n" +
                    "            ],\n" +
                    "            \"value\": true\n" +
                    "        }\n" +
                    "    ]\n" +
                    "}\n");
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Application ids must be on the form tenant:application:instance, but was a.b.c", e.getMessage());
        }
    }

    @Test
    public void normalize_json_fail_on_invalid_node_type() {
        try {
            SystemFlagsDataArchive.normalizeJson("{\n" +
                    "    \"id\": \"foo\",\n" +
                    "    \"rules\": [\n" +
                    "        {\n" +
                    "            \"conditions\": [\n" +
                    "                {\n" +
                    "                    \"type\": \"whitelist\",\n" +
                    "                    \"dimension\": \"node-type\",\n" +
                    "                    \"values\": [ \"footype\" ]\n" +
                    "                }\n" +
                    "            ],\n" +
                    "            \"value\": true\n" +
                    "        }\n" +
                    "    ]\n" +
                    "}\n");
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("No enum constant com.yahoo.config.provision.NodeType.footype", e.getMessage());
        }
    }

    @Test
    public void normalize_json_fail_on_invalid_email() {
        try {
            SystemFlagsDataArchive.normalizeJson("{\n" +
                    "    \"id\": \"foo\",\n" +
                    "    \"rules\": [\n" +
                    "        {\n" +
                    "            \"conditions\": [\n" +
                    "                {\n" +
                    "                    \"type\": \"whitelist\",\n" +
                    "                    \"dimension\": \"console-user-email\",\n" +
                    "                    \"values\": [ 123 ]\n" +
                    "                }\n" +
                    "            ],\n" +
                    "            \"value\": true\n" +
                    "        }\n" +
                    "    ]\n" +
                    "}\n");
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Non-string email address: 123", e.getMessage());
        }
    }

    @Test
    public void normalize_json_fail_on_invalid_tenant_id() {
        try {
            SystemFlagsDataArchive.normalizeJson("{\n" +
                    "    \"id\": \"foo\",\n" +
                    "    \"rules\": [\n" +
                    "        {\n" +
                    "            \"conditions\": [\n" +
                    "                {\n" +
                    "                    \"type\": \"whitelist\",\n" +
                    "                    \"dimension\": \"tenant\",\n" +
                    "                    \"values\": [ 123 ]\n" +
                    "                }\n" +
                    "            ],\n" +
                    "            \"value\": true\n" +
                    "        }\n" +
                    "    ]\n" +
                    "}\n");
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Non-string tenant ID: 123", e.getMessage());
        }
    }

    @Test
    public void ignores_files_not_related_to_specified_system_definition() {
        ZoneRegistry registry = createZoneRegistryMock();
        Path testDirectory = Paths.get("src/test/resources/system-flags-for-multiple-systems/");
        var archive = SystemFlagsDataArchive.fromDirectoryAndSystem(testDirectory, registry);
        assertFlagDataHasValue(archive, MY_TEST_FLAG, cdControllerTarget, "default"); // Would be 'cd.controller' if files for CD system were included
        assertFlagDataHasValue(archive, MY_TEST_FLAG, mainControllerTarget, "default");
        assertFlagDataHasValue(archive, MY_TEST_FLAG, prodUsWestCfgTarget, "main.prod.us-west-1");
    }

    @SuppressWarnings("unchecked") // workaround for mocking a method for generic return type
    private static ZoneRegistry createZoneRegistryMock() {
        // Cannot use the standard registry mock as it's located in controller-server module
        ZoneRegistry registryMock = mock(ZoneRegistry.class);
        when(registryMock.system()).thenReturn(SystemName.main);
        when(registryMock.getConfigServerVipUri(any())).thenReturn(URI.create("http://localhost:8080/"));
        when(registryMock.getConfigServerHttpsIdentity(any())).thenReturn(new AthenzService("domain", "servicename"));
        ZoneList zoneListMock = mock(ZoneList.class);
        when(zoneListMock.reachable()).thenReturn(zoneListMock);
        when(zoneListMock.all()).thenReturn(zoneListMock);
        when(zoneListMock.zones()).thenReturn((List)List.of(new SimpleZone("prod.us-west-1"), new SimpleZone("prod.us-east-3")));
        when(registryMock.zones()).thenReturn(zoneListMock);
        return registryMock;
    }

    private static void assertArchiveReturnsCorrectTestFlagDataForTarget(SystemFlagsDataArchive archive) {
        assertFlagDataHasValue(archive, MY_TEST_FLAG, mainControllerTarget, "main.controller");
        assertFlagDataHasValue(archive, MY_TEST_FLAG, prodUsWestCfgTarget, "main.prod.us-west-1");
        assertFlagDataHasValue(archive, MY_TEST_FLAG, prodUsEast3CfgTarget, "main.prod");
        assertFlagDataHasValue(archive, MY_TEST_FLAG, devUsEast1CfgTarget, "main");
    }

    private static void assertFlagDataHasValue(SystemFlagsDataArchive archive, FlagId flagId, FlagsTarget target, String value) {
        List<FlagData> data = getData(archive, flagId, target);
        assertEquals(1, data.size());
        FlagData flagData = data.get(0);
        RawFlag rawFlag = flagData.resolve(FetchVector.fromMap(Map.of())).get();
        assertEquals(String.format("\"%s\"", value), rawFlag.asJson());
    }

    private static void assertNoFlagData(SystemFlagsDataArchive archive, FlagId flagId, FlagsTarget target) {
        List<FlagData> data = getData(archive, flagId, target);
        assertTrue(data.isEmpty());
    }

    private static List<FlagData> getData(SystemFlagsDataArchive archive, FlagId flagId, FlagsTarget target) {
        return archive.flagData(target).stream()
                .filter(d -> d.id().equals(flagId))
                .collect(toList());
    }

    private static class SimpleZone implements ZoneApi {
        final ZoneId zoneId;
        SimpleZone(String zoneId) { this.zoneId = ZoneId.from(zoneId); }

        @Override public SystemName getSystemName() { return  SystemName.main; }
        @Override public ZoneId getId() { return zoneId; }
        @Override public CloudName getCloudName() { throw new UnsupportedOperationException(); }
        @Override public String getCloudNativeRegionName() { throw new UnsupportedOperationException(); }
    }

}