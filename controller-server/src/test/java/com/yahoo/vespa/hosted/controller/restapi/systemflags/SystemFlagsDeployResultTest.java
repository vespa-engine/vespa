// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.systemflags;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.hosted.controller.api.systemflags.v1.FlagsTarget;
import com.yahoo.vespa.hosted.controller.api.systemflags.v1.wire.WireSystemFlagsDeployResult;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneRegistryMock;
import org.junit.Test;

import java.util.List;

import static com.yahoo.vespa.hosted.controller.restapi.systemflags.SystemFlagsDeployResult.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author bjorncs
 */
public class SystemFlagsDeployResultTest {
    @Test
    public void changes_and_errors_are_present_in_wire_format() {
        FlagsTarget controllerTarget = FlagsTarget.forController(SystemName.cd);
        FlagId flagOne = new FlagId("flagone");
        FlagId flagTwo = new FlagId("flagtwo");
        SystemFlagsDeployResult result = new SystemFlagsDeployResult(
                List.of(
                        FlagDataChange.deleted(flagOne, controllerTarget)),
                List.of(
                        OperationError.deleteFailed("delete failed", controllerTarget, flagTwo)));
        WireSystemFlagsDeployResult wire = result.toWire();

        assertThat(wire.changes).hasSize(1);
        assertThat(wire.changes.get(0).flagId).isEqualTo(flagOne.toString());
        assertThat(wire.errors).hasSize(1);
        assertThat(wire.errors.get(0).flagId).isEqualTo(flagTwo.toString());
    }

    @Test
    public void identical_errors_and_changes_from_multiple_targets_are_merged() {
        ZoneApiMock prodUsWest1Zone = ZoneApiMock.fromId("prod.us-west-1");
        ZoneRegistryMock registry = new ZoneRegistryMock(SystemName.cd).setZones(prodUsWest1Zone);
        FlagsTarget prodUsWest1Target = FlagsTarget.forConfigServer(registry, prodUsWest1Zone.getId());
        FlagsTarget controllerTarget = FlagsTarget.forController(SystemName.cd);

        FlagId flagOne = new FlagId("flagone");
        FlagId flagTwo = new FlagId("flagtwo");

        SystemFlagsDeployResult resultController =
                new SystemFlagsDeployResult(
                        List.of(FlagDataChange.deleted(flagOne, controllerTarget)),
                        List.of(OperationError.deleteFailed("message", controllerTarget, flagTwo)));
        SystemFlagsDeployResult resultProdUsWest1 =
                new SystemFlagsDeployResult(
                        List.of(FlagDataChange.deleted(flagOne, prodUsWest1Target)),
                        List.of(OperationError.deleteFailed("message", prodUsWest1Target, flagTwo)));

        var results = List.of(resultController, resultProdUsWest1);
        SystemFlagsDeployResult mergedResult = merge(results);

        List<FlagDataChange> changes = mergedResult.flagChanges();
        assertThat(changes).hasSize(1);
        FlagDataChange change = changes.get(0);
        assertThat(change.targets()).containsOnly(controllerTarget, prodUsWest1Target);

        List<OperationError> errors = mergedResult.errors();
        assertThat(errors).hasSize(1);
        OperationError error = errors.get(0);
        assertThat(error.targets()).containsOnly(controllerTarget, prodUsWest1Target);
    }
}