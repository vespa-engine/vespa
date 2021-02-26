// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.systemflags;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.vespa.hosted.controller.api.systemflags.v1.FlagsTarget;
import com.yahoo.vespa.hosted.controller.api.systemflags.v1.SystemFlagsDataArchive;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneRegistryMock;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static com.yahoo.vespa.hosted.controller.restapi.systemflags.SystemFlagsDeployResult.FlagDataChange;
import static com.yahoo.vespa.hosted.controller.restapi.systemflags.SystemFlagsDeployResult.OperationError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author bjorncs
 */
public class SystemFlagsDeployerTest {

    private static final SystemName SYSTEM = SystemName.main;
    private static final FlagId FLAG_ID = new FlagId("my-flag");

    private final ZoneApiMock prodUsWest1Zone = ZoneApiMock.fromId("prod.us-west-1");
    private final ZoneApiMock prodUsEast3Zone = ZoneApiMock.fromId("prod.us-east-3");
    private final ZoneRegistryMock registry = new ZoneRegistryMock(SYSTEM).setZones(prodUsWest1Zone, prodUsEast3Zone);

    private final FlagsTarget controllerTarget = FlagsTarget.forController(SYSTEM);
    private final FlagsTarget prodUsWest1Target = FlagsTarget.forConfigServer(registry, prodUsWest1Zone.getId());
    private final FlagsTarget prodUsEast3Target = FlagsTarget.forConfigServer(registry, prodUsEast3Zone.getId());

    @Test
    public void deploys_flag_data_to_targets() throws IOException {
        FlagsClient flagsClient = mock(FlagsClient.class);
        when(flagsClient.listFlagData(controllerTarget)).thenReturn(List.of());
        when(flagsClient.listFlagData(prodUsWest1Target)).thenReturn(List.of(flagData("existing-prod.us-west-1.json")));
        FlagData existingProdUsEast3Data = flagData("existing-prod.us-east-3.json");
        when(flagsClient.listFlagData(prodUsEast3Target)).thenReturn(List.of(existingProdUsEast3Data));

        FlagData defaultData = flagData("flags/my-flag/main.json");
        FlagData prodUsEast3Data = flagData("flags/my-flag/main.prod.us-east-3.json");
        SystemFlagsDataArchive archive = new SystemFlagsDataArchive.Builder()
                .addFile("main.json", defaultData)
                .addFile("main.prod.us-east-3.json", prodUsEast3Data)
                .build();

        SystemFlagsDeployer deployer =
                new SystemFlagsDeployer(flagsClient, SYSTEM, Set.of(controllerTarget, prodUsWest1Target, prodUsEast3Target));


        SystemFlagsDeployResult result = deployer.deployFlags(archive, false);

        verify(flagsClient).putFlagData(controllerTarget, defaultData);
        verify(flagsClient).putFlagData(prodUsEast3Target, prodUsEast3Data);
        verify(flagsClient, never()).putFlagData(prodUsWest1Target, defaultData);
        List<FlagDataChange> changes = result.flagChanges();

        assertThat(changes).containsOnly(
                FlagDataChange.created(FLAG_ID, controllerTarget, defaultData),
                FlagDataChange.updated(FLAG_ID, prodUsEast3Target, prodUsEast3Data, existingProdUsEast3Data));
    }

    @Test
    public void dryrun_should_not_change_flags() throws IOException {
        FlagsClient flagsClient = mock(FlagsClient.class);
        when(flagsClient.listFlagData(controllerTarget)).thenReturn(List.of());
        when(flagsClient.listDefinedFlags(controllerTarget)).thenReturn(List.of(new FlagId("my-flag")));

        FlagData defaultData = flagData("flags/my-flag/main.json");
        SystemFlagsDataArchive archive = new SystemFlagsDataArchive.Builder()
                .addFile("main.json", defaultData)
                .build();

        SystemFlagsDeployer deployer = new SystemFlagsDeployer(flagsClient, SYSTEM, Set.of(controllerTarget));
        SystemFlagsDeployResult result = deployer.deployFlags(archive, true);

        verify(flagsClient, times(1)).listFlagData(controllerTarget);
        verify(flagsClient, never()).putFlagData(controllerTarget, defaultData);
        verify(flagsClient, never()).deleteFlagData(controllerTarget, FLAG_ID);

        assertThat(result.flagChanges()).containsOnly(
                FlagDataChange.created(FLAG_ID, controllerTarget, defaultData));
        assertThat(result.errors()).isEmpty();
    }

    @Test
    public void creates_error_entries_in_result_if_flag_data_operations_fail() throws IOException {
        FlagsClient flagsClient = mock(FlagsClient.class);
        UncheckedIOException exception = new UncheckedIOException(new IOException("I/O error message"));
        when(flagsClient.listFlagData(prodUsWest1Target)).thenThrow(exception);
        when(flagsClient.listFlagData(prodUsEast3Target)).thenReturn(List.of());
        when(flagsClient.listDefinedFlags(prodUsEast3Target)).thenReturn(List.of(new FlagId("my-flag")));

        FlagData defaultData = flagData("flags/my-flag/main.json");
        SystemFlagsDataArchive archive = new SystemFlagsDataArchive.Builder()
                .addFile("main.json", defaultData)
                .build();

        SystemFlagsDeployer deployer = new SystemFlagsDeployer(flagsClient, SYSTEM, Set.of(prodUsWest1Target, prodUsEast3Target));

        SystemFlagsDeployResult result = deployer.deployFlags(archive, false);

        assertThat(result.errors()).containsOnly(
                OperationError.listFailed(exception.getMessage(), prodUsWest1Target));
        assertThat(result.flagChanges()).containsOnly(
                FlagDataChange.created(FLAG_ID, prodUsEast3Target, defaultData));
    }

    @Test
    public void creates_error_entry_for_invalid_flag_archive() throws IOException {
        FlagsClient flagsClient = mock(FlagsClient.class);
        FlagData defaultData = flagData("flags/my-flag/main.json");
        SystemFlagsDataArchive archive = new SystemFlagsDataArchive.Builder()
                .addFile("main.prod.unknown-region.json", defaultData)
                .build();
        SystemFlagsDeployer deployer = new SystemFlagsDeployer(flagsClient, SYSTEM, Set.of(controllerTarget));
        SystemFlagsDeployResult result = deployer.deployFlags(archive, false);
        assertThat(result.flagChanges())
                .isEmpty();
        assertThat(result.errors())
                .containsOnly(OperationError.archiveValidationFailed("Unknown flag file: flags/my-flag/main.prod.unknown-region.json"));
    }

    @Test
    public void creates_error_entry_for_flag_data_of_undefined_flag() throws IOException {
        FlagData prodUsEast3Data = flagData("flags/my-flag/main.prod.us-east-3.json");
        FlagsClient flagsClient = mock(FlagsClient.class);
        when(flagsClient.listFlagData(prodUsEast3Target))
                .thenReturn(List.of());
        when(flagsClient.listDefinedFlags(prodUsEast3Target))
                .thenReturn(List.of());
        SystemFlagsDataArchive archive = new SystemFlagsDataArchive.Builder()
                .addFile("main.prod.us-east-3.json", prodUsEast3Data)
                .build();
        SystemFlagsDeployer deployer = new SystemFlagsDeployer(flagsClient, SYSTEM, Set.of(prodUsEast3Target));
        SystemFlagsDeployResult result = deployer.deployFlags(archive, true);
        String expectedErrorMessage = "Flag not defined in target zone. If zone/configserver cluster is new, " +
                "add an empty flag data file for this zone as a temporary measure until the stale flag data files are removed.";
        assertThat(result.errors())
                .containsOnly(SystemFlagsDeployResult.OperationError.createFailed(expectedErrorMessage, prodUsEast3Target, prodUsEast3Data));
    }

    @Test
    public void creates_warning_entry_for_existing_flag_data_for_undefined_flag() throws IOException {
        FlagData prodUsEast3Data = flagData("flags/my-flag/main.prod.us-east-3.json");
        FlagsClient flagsClient = mock(FlagsClient.class);
        when(flagsClient.listFlagData(prodUsEast3Target))
                .thenReturn(List.of(prodUsEast3Data));
        when(flagsClient.listDefinedFlags(prodUsEast3Target))
                .thenReturn(List.of());
        SystemFlagsDataArchive archive = new SystemFlagsDataArchive.Builder()
                .addFile("main.prod.us-east-3.json", prodUsEast3Data)
                .build();
        SystemFlagsDeployer deployer = new SystemFlagsDeployer(flagsClient, SYSTEM, Set.of(prodUsEast3Target));
        SystemFlagsDeployResult result = deployer.deployFlags(archive, true);
        assertThat(result.warnings())
                .containsOnly(SystemFlagsDeployResult.Warning.dataForUndefinedFlag(prodUsEast3Target, new FlagId("my-flag")));
    }

    private static FlagData flagData(String filename) throws IOException {
        return FlagData.deserializeUtf8Json(Files.readAllBytes(Paths.get("src/test/resources/system-flags/" + filename)));
    }

}
