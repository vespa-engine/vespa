// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.systemflags;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.hosted.controller.api.systemflags.v1.FlagsTarget;
import com.yahoo.vespa.hosted.controller.api.systemflags.v1.wire.WireSystemFlagsDeployResult;
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
}