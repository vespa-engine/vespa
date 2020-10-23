// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.api.integration.repair.RepairTicketReport;
import com.yahoo.vespa.hosted.controller.api.integration.repair.HostRepairClient;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 *
 * Responsible for keeping track of hosts under repair.
 *
 * @author olaa
 */
public class HostRepairMaintainer extends ControllerMaintainer {

    private final NodeRepository nodeRepository;
    private final HostRepairClient repairClient;

    private static final Logger log = Logger.getLogger(HostRepairMaintainer.class.getName());


    public HostRepairMaintainer(Controller controller, Duration interval) {
        super(controller, interval, null, SystemName.allOf(Predicate.not(SystemName::isPublic)));
        this.nodeRepository = controller.serviceRegistry().configServer().nodeRepository();
        this.repairClient = controller.serviceRegistry().hostRepairClient();
    }


    @Override
    protected boolean maintain() {
        AtomicInteger exceptions = new AtomicInteger(0);

        controller().zoneRegistry().zones()
                .reachable().zones().stream()
                .forEach(zoneApi -> {
                            var nodeTicketMap = nodeRepository.list((zoneApi).getId())
                                    .stream()
                                    .filter(this::hasOpenTicket)
                                    .collect(Collectors.toMap(
                                            node -> node,
                                            this::getTicketReport)
                                    );
                            try {
                                repairClient.updateRepairStatus(zoneApi, nodeTicketMap);
                            } catch (Exception e) {
                                log.warning("Failed to update repair status; " + Exceptions.toMessageString(e));
                                exceptions.incrementAndGet();
                            }
                        }
                );

        return exceptions.get() == 0;
    }


    private boolean hasOpenTicket(Node node) {
        var reports = node.reports();
        if (!reports.containsKey(RepairTicketReport.getReportId())) {
            return false;
        }
        return "OPEN".equals(getTicketReport(node).getStatus());
    }

    private RepairTicketReport getTicketReport(Node node) {
        return uncheck(() -> RepairTicketReport.fromJsonNode(node.reports().get(RepairTicketReport.getReportId())));
    }
}
