// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.application.ApplicationRotation;
import com.yahoo.vespa.hosted.controller.rotation.Rotation;
import com.yahoo.vespa.hosted.controller.rotation.RotationId;
import com.yahoo.vespa.hosted.controller.rotation.RotationLock;
import com.yahoo.vespa.hosted.controller.rotation.RotationRepository;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Performs DNS maintenance tasks such as removing DNS aliases for unassigned rotations.
 *
 * @author mpolden
 */
public class DnsMaintainer extends Maintainer {

    private static final Logger log = Logger.getLogger(DnsMaintainer.class.getName());

    private final NameService nameService;

    public DnsMaintainer(Controller controller, Duration interval, JobControl jobControl,
                         NameService nameService) {
        super(controller, interval, jobControl);
        this.nameService = nameService;
    }

    private RotationRepository rotationRepository() {
        return controller().applications().rotationRepository();
    }

    @Override
    protected void maintain() {
        try (RotationLock lock = rotationRepository().lock()) {
            Map<RotationId, Rotation> unassignedRotations = rotationRepository().availableRotations(lock);
            unassignedRotations.values().forEach(this::removeDnsAlias);
        }
    }

    /** Remove DNS alias for unassigned rotation */
    private void removeDnsAlias(Rotation rotation) {
        // When looking up CNAME by data, the data must be a FQDN
        Optional<Record> record = nameService.findRecord(Record.Type.CNAME, RecordData.fqdn(rotation.name()));
        record.filter(this::canUpdate)
              .ifPresent(r -> {
                  log.warning(String.format("Want to remove DNS record %s (%s) because it points to the unassigned " +
                                            "rotation %s (%s)", record.get().id().asString(),
                                            record.get().name().asString(), rotation.id().asString(), rotation.name()));
                  // TODO: Actually remove the record
                  //nameService.removeRecord(r.id());
              });
    }

    /** Returns whether we can update the given record */
    private boolean canUpdate(Record record) {
        return record.name().asString().endsWith(ApplicationRotation.DNS_SUFFIX);
    }

}
