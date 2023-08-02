// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
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

    @TempDir
    public File temporaryFolder;

    private static final FlagsTarget mainControllerTarget = createControllerTarget(SYSTEM);
    private static final FlagsTarget cdControllerTarget = createControllerTarget(SystemName.cd);
    private static final FlagsTarget prodUsWestCfgTarget = createConfigserverTarget(Environment.prod, "us-west-1");
    private static final FlagsTarget prodUsEast3CfgTarget = createConfigserverTarget(Environment.prod, "us-east-3");
    private static final FlagsTarget devUsEast1CfgTarget = createConfigserverTarget(Environment.dev, "us-east-1");

    private static FlagsTarget createControllerTarget(SystemName system) {
        return new ControllerFlagsTarget(system, CloudName.YAHOO, ZoneId.from(Environment.prod, RegionName.from("us-east-1")));
    }

    private static FlagsTarget createConfigserverTarget(Environment environment, String region) {
        return new ConfigServerFlagsTarget(
                SYSTEM,
                CloudName.YAHOO,
                ZoneId.from(environment, RegionName.from(region)),
                URI.create("https://cfg-" + region),
                new AthenzService("vespa.cfg-" + region));
    }

    @Test
    void can_serialize_and_deserialize_archive() throws IOException {
        File tempFile = File.createTempFile("serialized-flags-archive", null, temporaryFolder);
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
    void retrieves_correct_flag_data_for_target() {
        var archive = SystemFlagsDataArchive.fromDirectory(Paths.get("src/test/resources/system-flags/"));
        assertArchiveReturnsCorrectTestFlagDataForTarget(archive);
    }

    @Test
    void supports_multi_level_flags_directory() {
        var archive = SystemFlagsDataArchive.fromDirectory(Paths.get("src/test/resources/system-flags-multi-level/"));
        assertFlagDataHasValue(archive, MY_TEST_FLAG, mainControllerTarget, "default");
    }

    @Test
    void duplicated_flagdata_is_detected() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            var archive = SystemFlagsDataArchive.fromDirectory(Paths.get("src/test/resources/system-flags-multi-level-with-duplicated-flagdata/"));
        });
        assertTrue(exception.getMessage().contains("contains redundant flag data for id 'my-test-flag' already set in another directory!"));
    }

    @Test
    void empty_files_are_handled_as_no_flag_data_for_target() {
        var archive = SystemFlagsDataArchive.fromDirectory(Paths.get("src/test/resources/system-flags/"));
        assertNoFlagData(archive, FLAG_WITH_EMPTY_DATA, mainControllerTarget);
        assertFlagDataHasValue(archive, FLAG_WITH_EMPTY_DATA, prodUsWestCfgTarget, "main.prod.us-west-1");
        assertNoFlagData(archive, FLAG_WITH_EMPTY_DATA, prodUsEast3CfgTarget);
        assertFlagDataHasValue(archive, FLAG_WITH_EMPTY_DATA, devUsEast1CfgTarget, "main");
    }

    @Test
    void throws_exception_on_non_json_file() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            SystemFlagsDataArchive.fromDirectory(Paths.get("src/test/resources/system-flags-with-invalid-file-name/"));
        });
        assertTrue(exception.getMessage().contains("Only JSON files are allowed in 'flags/' directory (found 'flags/my-test-flag/file-name-without-dot-json')"));
    }

    @Test
    void throws_exception_on_unknown_file() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            SystemFlagsDataArchive archive = SystemFlagsDataArchive.fromDirectory(Paths.get("src/test/resources/system-flags-with-unknown-file-name/"));
            archive.validateAllFilesAreForTargets(SystemName.main, Set.of(mainControllerTarget, prodUsWestCfgTarget));
        });
        assertTrue(exception.getMessage().contains("Unknown flag file: flags/my-test-flag/main.prod.unknown-region.json"));
    }

    @Test
    void throws_exception_on_unknown_region() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            Path directory = Paths.get("src/test/resources/system-flags-with-unknown-file-name/");
            SystemFlagsDataArchive.fromDirectoryAndSystem(directory, createZoneRegistryMock());
        });
        assertTrue(exception.getMessage().contains("Environment or zone in filename 'main.prod.unknown-region.json' does not exist"));
    }

    @Test
    void throws_on_unknown_field() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            SystemFlagsDataArchive.fromDirectory(Paths.get("src/test/resources/system-flags-with-unknown-field-name/"));
        });
        assertEquals("""
                     flags/my-test-flag/main.prod.us-west-1.json contains unknown non-comment fields or rules with null values: after removing any comment fields the JSON is:
                       {"id":"my-test-flag","rules":[{"condition":[{"type":"whitelist","dimension":"hostname","values":["foo.com"]}],"value":"default"}]}
                     but deserializing this ended up with:
                       {"id":"my-test-flag","rules":[{"value":"default"}]}
                     These fields may be spelled wrong, or remove them?
                     See https://git.ouroath.com/vespa/hosted-feature-flags for more info on the JSON syntax
                     """,
                     exception.getMessage());
    }

    @Test
    void handles_absent_rule_value() {
        SystemFlagsDataArchive archive = SystemFlagsDataArchive.fromDirectory(Paths.get("src/test/resources/system-flags-with-null-value/"));

        // west has null value on first rule
        List<FlagData> westFlagData = archive.flagData(prodUsWestCfgTarget);
        assertEquals(1, westFlagData.size());
        assertEquals(2, westFlagData.get(0).rules().size());
        assertEquals(Optional.empty(), westFlagData.get(0).rules().get(0).getValueToApply());

        // east has no value on first rule
        List<FlagData> eastFlagData = archive.flagData(prodUsEast3CfgTarget);
        assertEquals(1, eastFlagData.size());
        assertEquals(2, eastFlagData.get(0).rules().size());
        assertEquals(Optional.empty(), eastFlagData.get(0).rules().get(0).getValueToApply());
    }

    @Test
    void remove_comments_and_null_value_in_rules() {
        assertTrue(JSON.equals("""
                               {
                                 "rules": [
                                   {
                                     "conditions": [
                                       {
                                         "type": "whitelist",
                                         "dimension": "hostname",
                                         "values": [ "foo.com" ]
                                       }
                                     ]
                                   },
                                   {
                                     "conditions": [
                                       {
                                         "type": "whitelist",
                                         "dimension": "zone",
                                         "values": [ "prod.us-west-1" ]
                                       }
                                     ]
                                   },
                                   {
                                     "conditions": [
                                       {
                                         "type": "whitelist",
                                         "dimension": "application",
                                         "values": [ "f:o:o" ]
                                       }
                                     ],
                                     "value": true
                                   }
                                 ]
                               }""",
                               SystemFlagsDataArchive.normalizeJson("""
                               {
                                 "comment": "bar",
                                 "rules": [
                                   {
                                     "comment": "bar",
                                     "conditions": [
                                       {
                                         "comment": "bar",
                                         "type": "whitelist",
                                         "dimension": "hostname",
                                         "values": [ "foo.com" ]
                                       }
                                     ],
                                     "value": null
                                   },
                                   {
                                     "comment": "bar",
                                     "conditions": [
                                       {
                                         "comment": "bar",
                                         "type": "whitelist",
                                         "dimension": "zone",
                                         "values": [ "prod.us-west-1" ]
                                       }
                                     ]
                                   },
                                   {
                                     "comment": "bar",
                                     "conditions": [
                                       {
                                         "comment": "bar",
                                         "type": "whitelist",
                                         "dimension": "application",
                                         "values": [ "f:o:o" ]
                                       }
                                     ],
                                     "value": true
                                   }
                                 ]
                               }""", Set.of(ZoneId.from("prod.us-west-1")))));
    }

    @Test
    void normalize_json_succeed_on_valid_values() {
        normalizeJson("application", "\"a:b:c\"");
        normalizeJson("cloud", "\"yahoo\"");
        normalizeJson("cloud", "\"aws\"");
        normalizeJson("cloud", "\"gcp\"");
        normalizeJson("cluster-id", "\"some-id\"");
        normalizeJson("cluster-type", "\"admin\"");
        normalizeJson("cluster-type", "\"container\"");
        normalizeJson("cluster-type", "\"content\"");
        normalizeJson("console-user-email", "\"name@domain.com\"");
        normalizeJson("environment", "\"prod\"");
        normalizeJson("environment", "\"staging\"");
        normalizeJson("environment", "\"test\"");
        normalizeJson("hostname", "\"2080046-v6-11.ostk.bm2.prod.gq1.yahoo.com\"");
        normalizeJson("node-type", "\"tenant\"");
        normalizeJson("node-type", "\"host\"");
        normalizeJson("node-type", "\"config\"");
        normalizeJson("node-type", "\"host\"");
        normalizeJson("system", "\"main\"");
        normalizeJson("system", "\"public\"");
        normalizeJson("tenant", "\"vespa\"");
        normalizeJson("vespa-version", "\"8.201.13\"");
        normalizeJson("zone", "\"prod.us-west-1\"", Set.of(ZoneId.from("prod.us-west-1")));
    }

    private void normalizeJson(String dimension, String jsonValue) {
        normalizeJson(dimension, jsonValue, Set.of());
    }

    private void normalizeJson(String dimension, String jsonValue, Set<ZoneId> zones) {
        SystemFlagsDataArchive.normalizeJson("""
                                             {
                                                 "id": "foo",
                                                 "rules": [
                                                     {
                                                         "conditions": [
                                                             {
                                                                 "type": "whitelist",
                                                                 "dimension": "%s",
                                                                 "values": [ %s ]
                                                             }
                                                         ],
                                                         "value": true
                                                     }
                                                 ]
                                             }
                                             """.formatted(dimension, jsonValue), zones);
    }

    @Test
    void normalize_json_fail_on_invalid_values() {
        failNormalizeJson("application", "\"a.b.c\"", "Application ids must be on the form tenant:application:instance, but was a.b.c");
        failNormalizeJson("cloud", "\"foo\"", "Unknown cloud: foo");
        // failNormalizeJson("cluster-id", ... any String is valid
        failNormalizeJson("cluster-type", "\"foo\"", "Illegal cluster type 'foo'");
        failNormalizeJson("console-user-email", "123", "Non-string value in console-user-email whitelist condition: 123");
        failNormalizeJson("environment", "\"foo\"", "'foo' is not a valid environment identifier");
        failNormalizeJson("hostname", "\"not:a:hostname\"", "hostname must match '(([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9-]{0,61}[A-Za-z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9-]{0,61}[A-Za-z0-9])\\.?', but got: 'not:a:hostname'");
        failNormalizeJson("node-type", "\"footype\"", "No enum constant com.yahoo.config.provision.NodeType.footype");
        failNormalizeJson("system", "\"bar\"", "'bar' is not a valid system");
        failNormalizeJson("tenant", "123", "Non-string value in tenant whitelist condition: 123");
        failNormalizeJson("vespa-version", "\"not-a-version\"", "Invalid version component in 'not-a-version'");
        failNormalizeJson("zone", "\"dev.non-existing-zone\"", Set.of(ZoneId.from("prod.example-region")), "Unknown zone: dev.non-existing-zone");
    }

    private void failNormalizeJson(String dimension, String jsonValue, String expectedExceptionMessage) {
        failNormalizeJson(dimension, jsonValue, Set.of(), expectedExceptionMessage);
    }

    private void failNormalizeJson(String dimension, String jsonValue, Set<ZoneId> zones, String expectedExceptionMessage) {
        try {
            normalizeJson(dimension, jsonValue, zones);
            fail();
        } catch (RuntimeException e) {
            assertEquals(expectedExceptionMessage, e.getMessage());
        }
    }

    @Test
    void ignores_files_not_related_to_specified_system_definition() {
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
        ZoneApi zoneApi = mock(ZoneApi.class);
        when(zoneApi.getSystemName()).thenReturn(SystemName.main);
        when(zoneApi.getCloudName()).thenReturn(CloudName.YAHOO);
        when(zoneApi.getVirtualId()).thenReturn(ZoneId.ofVirtualControllerZone());
        when(registryMock.systemZone()).thenReturn(zoneApi);
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
                .toList();
    }

    private static class SimpleZone implements ZoneApi {
        final ZoneId zoneId;
        SimpleZone(String zoneId) { this.zoneId = ZoneId.from(zoneId); }

        @Override public SystemName getSystemName() { return  SystemName.main; }
        @Override public ZoneId getId() { return zoneId; }
        @Override public CloudName getCloudName() { return CloudName.YAHOO; }
        @Override public String getCloudNativeRegionName() { throw new UnsupportedOperationException(); }
    }

}
