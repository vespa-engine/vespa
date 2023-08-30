// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.systemflags;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.hosted.controller.api.systemflags.v1.FlagsTarget;
import com.yahoo.vespa.hosted.controller.api.systemflags.v1.wire.WireSystemFlagsDeployResult;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import com.yahoo.vespa.hosted.controller.integration.ZoneRegistryMock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.yahoo.vespa.hosted.controller.restapi.systemflags.SystemFlagsDeployResult.FlagDataChange;
import static com.yahoo.vespa.hosted.controller.restapi.systemflags.SystemFlagsDeployResult.OperationError;
import static com.yahoo.vespa.hosted.controller.restapi.systemflags.SystemFlagsDeployResult.merge;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bjorncs
 */
public class SystemFlagsDeployResultTest {
    private final ZoneApiMock prodUsWest1Zone = ZoneApiMock.fromId("prod.us-west-1");
    private final ZoneRegistryMock registry = new ZoneRegistryMock(SystemName.cd).setZones(prodUsWest1Zone);

    @Test
    void changes_and_errors_are_present_in_wire_format() {
        FlagsTarget controllerTarget = FlagsTarget.forController(registry.systemZone());
        FlagId flagOne = new FlagId("flagone");
        FlagId flagTwo = new FlagId("flagtwo");
        SystemFlagsDeployResult result = new SystemFlagsDeployResult(
                List.of(FlagDataChange.deleted(flagOne, controllerTarget)),
                List.of(OperationError.deleteFailed("delete failed", controllerTarget, flagTwo)),
                List.of());
        WireSystemFlagsDeployResult wire = result.toWire();

        assertEquals(1, wire.changes.size());
        assertEquals(wire.changes.get(0).flagId, flagOne.toString());
        assertEquals(1, wire.errors.size());
        assertEquals(wire.errors.get(0).flagId, flagTwo.toString());
    }

    @Test
    void identical_errors_and_changes_from_multiple_targets_are_merged() {
        FlagsTarget prodUsWest1Target = FlagsTarget.forConfigServer(registry, prodUsWest1Zone);
        FlagsTarget controllerTarget = FlagsTarget.forController(registry.systemZone());

        FlagId flagOne = new FlagId("flagone");
        FlagId flagTwo = new FlagId("flagtwo");

        SystemFlagsDeployResult resultController =
                new SystemFlagsDeployResult(
                        List.of(FlagDataChange.deleted(flagOne, controllerTarget)),
                        List.of(OperationError.deleteFailed("message", controllerTarget, flagTwo)),
                        List.of());
        SystemFlagsDeployResult resultProdUsWest1 =
                new SystemFlagsDeployResult(
                        List.of(FlagDataChange.deleted(flagOne, prodUsWest1Target)),
                        List.of(OperationError.deleteFailed("message", prodUsWest1Target, flagTwo)),
                        List.of());

        var results = List.of(resultController, resultProdUsWest1);
        SystemFlagsDeployResult mergedResult = merge(results);

        List<FlagDataChange> changes = mergedResult.flagChanges();
        assertEquals(1, changes.size());
        FlagDataChange change = changes.get(0);
        assertEquals(change.targets(), Set.of(controllerTarget, prodUsWest1Target));

        List<OperationError> errors = mergedResult.errors();
        assertEquals(1, errors.size());
        OperationError error = errors.get(0);
        assertEquals(error.targets(), Set.of(controllerTarget, prodUsWest1Target));
    }
}