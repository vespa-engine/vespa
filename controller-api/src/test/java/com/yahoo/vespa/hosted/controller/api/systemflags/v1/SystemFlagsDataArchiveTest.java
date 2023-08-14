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
import com.yahoo.vespa.flags.json.Condition;
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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

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
        can_serialize_and_deserialize_archive(false);
        can_serialize_and_deserialize_archive(true);
    }

    private void can_serialize_and_deserialize_archive(boolean simulateInController) throws IOException {
        File tempFile = File.createTempFile("serialized-flags-archive", null, temporaryFolder);
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(tempFile))) {
            var archive = fromDirectory("system-flags", simulateInController);
            if (simulateInController)
                archive.validateAllFilesAreForTargets(Set.of(mainControllerTarget, prodUsWestCfgTarget));
            archive.toZip(out);
        }
        try (InputStream in = new BufferedInputStream(new FileInputStream(tempFile))) {
            SystemFlagsDataArchive archive = SystemFlagsDataArchive.fromZip(in, createZoneRegistryMock());
            assertArchiveReturnsCorrectTestFlagDataForTarget(archive);
        }
    }

    @Test
    void retrieves_correct_flag_data_for_target() {
        retrieves_correct_flag_data_for_target(false);
        retrieves_correct_flag_data_for_target(true);
    }

    private void retrieves_correct_flag_data_for_target(boolean simulateInController) {
        var archive = fromDirectory("system-flags", simulateInController);
        if (simulateInController)
            archive.validateAllFilesAreForTargets(Set.of(mainControllerTarget, prodUsWestCfgTarget));
        assertArchiveReturnsCorrectTestFlagDataForTarget(archive);
    }

    @Test
    void supports_multi_level_flags_directory() {
        supports_multi_level_flags_directory(false);
        supports_multi_level_flags_directory(true);
    }

    private void supports_multi_level_flags_directory(boolean simulateInController) {
        var archive = fromDirectory("system-flags-multi-level", simulateInController);
        if (simulateInController)
            archive.validateAllFilesAreForTargets(Set.of(mainControllerTarget, prodUsWestCfgTarget));
        assertFlagDataHasValue(archive, MY_TEST_FLAG, mainControllerTarget, "default");
    }

    @Test
    void duplicated_flagdata_is_detected() {
        duplicated_flagdata_is_detected(false);
        duplicated_flagdata_is_detected(true);
    }

    private void duplicated_flagdata_is_detected(boolean simulateInController) {
        Throwable exception = assertThrows(FlagValidationException.class, () -> {
            fromDirectory("system-flags-multi-level-with-duplicated-flagdata", simulateInController);
       });
        assertTrue(exception.getMessage().contains("contains redundant flag data for id 'my-test-flag' already set in another directory!"));
    }

    @Test
    void empty_files_are_handled_as_no_flag_data_for_target() {
        empty_files_are_handled_as_no_flag_data_for_target(false);
        empty_files_are_handled_as_no_flag_data_for_target(true);
    }

    private void empty_files_are_handled_as_no_flag_data_for_target(boolean simulateInController) {
        var archive = fromDirectory("system-flags", simulateInController);
        if (simulateInController)
            archive.validateAllFilesAreForTargets(Set.of(mainControllerTarget, prodUsWestCfgTarget));
        assertNoFlagData(archive, FLAG_WITH_EMPTY_DATA, mainControllerTarget);
        assertFlagDataHasValue(archive, FLAG_WITH_EMPTY_DATA, prodUsWestCfgTarget, "main.prod.us-west-1");
        assertNoFlagData(archive, FLAG_WITH_EMPTY_DATA, prodUsEast3CfgTarget);
        assertFlagDataHasValue(archive, FLAG_WITH_EMPTY_DATA, devUsEast1CfgTarget, "main");
    }

    @Test
    void hv_throws_exception_on_non_json_file() {
        Throwable exception = assertThrows(FlagValidationException.class, () -> {
            fromDirectory("system-flags-with-invalid-file-name", false);
        });
        assertEquals("Invalid flag filename: file-name-without-dot-json",
                     exception.getMessage());
    }

    @Test
    void throws_exception_on_unknown_file() {
        Throwable exception = assertThrows(FlagValidationException.class, () -> {
            SystemFlagsDataArchive archive = fromDirectory("system-flags-with-unknown-file-name", true);
            archive.validateAllFilesAreForTargets(Set.of(mainControllerTarget, prodUsWestCfgTarget));
        });
        assertEquals("Unknown flag file: flags/my-test-flag/main.prod.unknown-region.json", exception.getMessage());
    }

    @Test
    void unknown_region_is_still_zipped() {
        // This is useful when the program zipping the files is on a different version than the controller
        var archive = fromDirectory("system-flags-with-unknown-file-name", false);
        assertTrue(archive.hasFlagData(MY_TEST_FLAG, "main.prod.unknown-region.json"));
    }

    @Test
    void throws_exception_on_unknown_region() {
        Throwable exception = assertThrows(FlagValidationException.class, () -> {
            var archive = fromDirectory("system-flags-with-unknown-file-name", true);
            archive.validateAllFilesAreForTargets(Set.of(mainControllerTarget, prodUsWestCfgTarget));
        });
        assertEquals("Unknown flag file: flags/my-test-flag/main.prod.unknown-region.json", exception.getMessage());
    }

    @Test
    void throws_on_unknown_field() {
        Throwable exception = assertThrows(FlagValidationException.class, () -> {
            fromDirectory("system-flags-with-unknown-field-name", true);
        });
        assertEquals("""
                     In file flags/my-test-flag/main.prod.us-west-1.json: Unknown non-comment fields or rules with null values: after removing any comment fields the JSON is:
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
        SystemFlagsDataArchive archive = fromDirectory("system-flags-with-null-value", true);

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
                                 "id": "foo",
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
                               normalizeJson("""
                               {
                                 "id": "foo",
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
                               }""")));
    }

    private static String normalizeJson(String json) {
        SystemFlagsDataArchive.Builder builder = new SystemFlagsDataArchive.Builder();
        assertTrue(builder.maybeAddFile(Path.of("flags/temporary/foo/default.json"), json, createZoneRegistryMock(), true));
        List<FlagData> flagData = builder.build().flagData(prodUsWestCfgTarget);
        assertEquals(1, flagData.size());
        return JSON.canonical(flagData.get(0).serializeToJson());
    }

    @Test
    void normalize_json_succeed_on_valid_values() {
        addFile(Condition.Type.WHITELIST, "application", "a:b:c");
        addFile(Condition.Type.WHITELIST, "cloud", "yahoo");
        addFile(Condition.Type.WHITELIST, "cloud", "aws");
        addFile(Condition.Type.WHITELIST, "cloud", "gcp");
        addFile(Condition.Type.WHITELIST, "cluster-id", "some-id");
        addFile(Condition.Type.WHITELIST, "cluster-type", "admin");
        addFile(Condition.Type.WHITELIST, "cluster-type", "container");
        addFile(Condition.Type.WHITELIST, "cluster-type", "content");
        addFile(Condition.Type.WHITELIST, "console-user-email", "name@domain.com");
        addFile(Condition.Type.WHITELIST, "environment", "prod");
        addFile(Condition.Type.WHITELIST, "environment", "staging");
        addFile(Condition.Type.WHITELIST, "environment", "test");
        addFile(Condition.Type.WHITELIST, "hostname", "2080046-v6-11.ostk.bm2.prod.gq1.yahoo.com");
        addFile(Condition.Type.WHITELIST, "node-type", "tenant");
        addFile(Condition.Type.WHITELIST, "node-type", "host");
        addFile(Condition.Type.WHITELIST, "node-type", "config");
        addFile(Condition.Type.WHITELIST, "node-type", "host");
        addFile(Condition.Type.WHITELIST, "system", "main");
        addFile(Condition.Type.WHITELIST, "system", "public");
        addFile(Condition.Type.WHITELIST, "tenant", "vespa");
        addFile(Condition.Type.RELATIONAL, "vespa-version", ">=8.201.13");
        addFile(Condition.Type.WHITELIST, "zone", "prod.us-west-1");
    }

    private void addFile(Condition.Type type, String dimension, String jsonValue) {
        SystemFlagsDataArchive.Builder builder = new SystemFlagsDataArchive.Builder();

        String valuesField = type == Condition.Type.RELATIONAL ?
                             "\"predicate\": \"%s\"".formatted(jsonValue) :
                             "\"values\": [ \"%s\" ]".formatted(jsonValue);

        assertTrue(builder.maybeAddFile(Path.of("flags/temporary/foo/default.json"), """
                                             {
                                                 "id": "foo",
                                                 "rules": [
                                                     {
                                                         "conditions": [
                                                             {
                                                                 "type": "%s",
                                                                 "dimension": "%s",
                                                                 %s
                                                             }
                                                         ],
                                                         "value": true
                                                     }
                                                 ]
                                             }
                                             """.formatted(type.toWire(), dimension, valuesField),
                                        createZoneRegistryMock(),
                                        true));
    }

    @Test
    void normalize_json_fail_on_invalid_values() {
        failAddFile(Condition.Type.WHITELIST, "application", "a.b.c", "In file flags/temporary/foo/default.json: Invalid application 'a.b.c' in whitelist condition: Application ids must be on the form tenant:application:instance, but was a.b.c");
        failAddFile(Condition.Type.WHITELIST, "cloud", "foo", "In file flags/temporary/foo/default.json: Unknown cloud: foo");
        // cluster-id: any String is valid
        failAddFile(Condition.Type.WHITELIST, "cluster-type", "foo", "In file flags/temporary/foo/default.json: Invalid cluster-type 'foo' in whitelist condition: Illegal cluster type 'foo'");
        failAddFile(Condition.Type.WHITELIST, "console-user-email", "not-valid-email-address", "In file flags/temporary/foo/default.json: Invalid email address: not-valid-email-address");
        failAddFile(Condition.Type.WHITELIST, "environment", "foo", "In file flags/temporary/foo/default.json: Invalid environment 'foo' in whitelist condition: 'foo' is not a valid environment identifier");
        failAddFile(Condition.Type.WHITELIST, "hostname", "not:a:hostname", "In file flags/temporary/foo/default.json: Invalid hostname 'not:a:hostname' in whitelist condition: hostname must match '(([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9-]{0,61}[A-Za-z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9-]{0,61}[A-Za-z0-9])\\.?', but got: 'not:a:hostname'");
        failAddFile(Condition.Type.WHITELIST, "node-type", "footype", "In file flags/temporary/foo/default.json: Invalid node-type 'footype' in whitelist condition: No enum constant com.yahoo.config.provision.NodeType.footype");
        failAddFile(Condition.Type.WHITELIST, "system", "bar", "In file flags/temporary/foo/default.json: Invalid system 'bar' in whitelist condition: 'bar' is not a valid system");
        failAddFile(Condition.Type.WHITELIST, "tenant", "a tenant", "In file flags/temporary/foo/default.json: Invalid tenant 'a tenant' in whitelist condition: tenant name must match '[a-zA-Z0-9_-]{1,256}', but got: 'a tenant'");
        failAddFile(Condition.Type.WHITELIST, "vespa-version", "not-a-version", "In file flags/temporary/foo/default.json: whitelist vespa-version condition is not supported");
        failAddFile(Condition.Type.RELATIONAL, "vespa-version", ">7.1.2", "In file flags/temporary/foo/default.json: Major Vespa version must be at least 8: 7.1.2");
        failAddFile(Condition.Type.WHITELIST, "zone", "dev.%illegal", "In file flags/temporary/foo/default.json: Invalid zone 'dev.%illegal' in whitelist condition: region name must match '[a-z]([a-z0-9-]*[a-z0-9])*', but got: '%illegal'");
    }

    private void failAddFile(Condition.Type type, String dimension, String jsonValue, String expectedExceptionMessage) {
        try {
            addFile(type, dimension, jsonValue);
            fail();
        } catch (RuntimeException e) {
            assertEquals(expectedExceptionMessage, e.getMessage());
        }
    }

    @Test
    void ignores_files_not_related_to_specified_system_definition() {
        var archive = fromDirectory("system-flags-for-multiple-systems", false);
        assertFlagDataHasValue(archive, MY_TEST_FLAG, cdControllerTarget, "default"); // Would be 'cd.controller' if files for CD system were included
        assertFlagDataHasValue(archive, MY_TEST_FLAG, mainControllerTarget, "default");
        assertFlagDataHasValue(archive, MY_TEST_FLAG, prodUsWestCfgTarget, "main.prod.us-west-1");
    }

    private SystemFlagsDataArchive fromDirectory(String testDirectory, boolean simulateInController) {
        return SystemFlagsDataArchive.fromDirectory(Paths.get("src/test/resources/" + testDirectory), createZoneRegistryMock(), simulateInController);
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
        ZoneList zones = mockZoneList("prod.us-west-1", "prod.us-east-3");
        when(registryMock.zones()).thenReturn(zones);
        ZoneList zonesIncludingSystem = mockZoneList("prod.us-west-1", "prod.us-east-3", "prod.controller");
        when(registryMock.zonesIncludingSystem()).thenReturn(zonesIncludingSystem);
        return registryMock;
    }

    @SuppressWarnings("unchecked") // workaround for mocking a method for generic return type
    private static ZoneList mockZoneList(String... zones) {
        ZoneList zoneListMock = mock(ZoneList.class);
        when(zoneListMock.reachable()).thenReturn(zoneListMock);
        when(zoneListMock.all()).thenReturn(zoneListMock);
        List<? extends ZoneApi> zoneList = Stream.of(zones).map(SimpleZone::new).toList();
        when(zoneListMock.zones()).thenReturn((List) zoneList);
        return zoneListMock;
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
        RawFlag rawFlag = flagData.resolve(new FetchVector()).get();
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
