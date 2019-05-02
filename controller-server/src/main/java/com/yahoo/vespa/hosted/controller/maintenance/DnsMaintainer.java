// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.dns.NameServiceQueue.Priority;
import com.yahoo.vespa.hosted.controller.rotation.Rotation;
import com.yahoo.vespa.hosted.controller.rotation.RotationId;
import com.yahoo.vespa.hosted.controller.rotation.RotationLock;
import com.yahoo.vespa.hosted.controller.rotation.RotationRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Performs DNS maintenance tasks such as removing DNS aliases for unassigned rotations.
 *
 * @author mpolden
 */
public class DnsMaintainer extends Maintainer {

    private final AtomicInteger rotationIndex = new AtomicInteger(0);

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
            rotationToCheckOf(unassignedRotations.values()).ifPresent(this::removeCname);
        }
    }

    /** Remove CNAME(s) for unassigned rotation */
    private void removeCname(Rotation rotation) {
        // When looking up CNAME by data, the data must be a FQDN
        controller().nameServiceForwarder().removeRecords(Record.Type.CNAME, RecordData.fqdn(rotation.name()), Priority.normal);
    }

    /**
     * Returns the rotation that should be checked in this run. We check only one rotation per run to avoid running into
     * rate limits that may be imposed by the {@link NameService} implementation.
     */
    private Optional<Rotation> rotationToCheckOf(Collection<Rotation> rotations) {
        if (rotations.isEmpty()) return Optional.empty();
        List<Rotation> rotationList = new ArrayList<>(rotations);
        int index = rotationIndex.getAndUpdate((i) -> {
            if (i < rotationList.size() - 1) {
                return ++i;
            }
            return 0;
        });
        return Optional.of(rotationList.get(index));
    }

}
