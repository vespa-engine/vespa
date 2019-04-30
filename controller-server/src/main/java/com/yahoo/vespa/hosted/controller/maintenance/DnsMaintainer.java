// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.dns.NameServiceQueue.Priority;
import com.yahoo.vespa.hosted.controller.rotation.Rotation;
import com.yahoo.vespa.hosted.controller.rotation.RotationId;
import com.yahoo.vespa.hosted.controller.rotation.RotationLock;
import com.yahoo.vespa.hosted.controller.rotation.RotationRepository;

import java.time.Duration;
import java.util.Map;

/**
 * Performs DNS maintenance tasks such as removing DNS aliases for unassigned rotations.
 *
 * @author mpolden
 */
public class DnsMaintainer extends Maintainer {

    public DnsMaintainer(Controller controller, Duration interval, JobControl jobControl) {
        super(controller, interval, jobControl);
    }

    private RotationRepository rotationRepository() {
        return controller().applications().rotationRepository();
    }

    @Override
    protected void maintain() {
        try (RotationLock lock = rotationRepository().lock()) {
            Map<RotationId, Rotation> unassignedRotations = rotationRepository().availableRotations(lock);
            unassignedRotations.values().forEach(this::removeCname);
        }
    }

    /** Remove CNAME(s) for unassigned rotation */
    private void removeCname(Rotation rotation) {
        // When looking up CNAME by data, the data must be a FQDN
        controller().nameServiceForwarder().removeRecords(Record.Type.CNAME, RecordData.fqdn(rotation.name()), Priority.normal);
    }

}
