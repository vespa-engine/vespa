// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeFilter;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.api.integration.entity.EntityService;
import com.yahoo.vespa.hosted.controller.api.integration.entity.NodeEntity;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Ensures that the host information for all hosts is up to date.
 *
 * @author mpolden
 * @author bjormel
 */
public class HostInfoUpdater extends ControllerMaintainer {

    private static final Logger LOG = Logger.getLogger(HostInfoUpdater.class.getName());
    private static final Pattern HOST_PATTERN = Pattern.compile("^(proxy|cfg|controller)host(.+)$");

    private final NodeRepository nodeRepository;

    public HostInfoUpdater(Controller controller, Duration interval) {
        super(controller, interval, null, EnumSet.of(SystemName.cd, SystemName.main));
        this.nodeRepository = controller.serviceRegistry().configServer().nodeRepository();
    }

    @Override
    protected double maintain() {
        Map<String, NodeEntity> nodeEntities = controller().serviceRegistry().entityService().listNodes().stream()
                                                           .collect(Collectors.toMap(NodeEntity::hostname,
                                                                                     Function.identity()));
        int hostsUpdated = 0;
        try {
            for (var zone : controller().zoneRegistry().zones().controllerUpgraded().all().ids()) {
                for (var node : nodeRepository.list(zone, NodeFilter.all())) {
                    if (!node.type().isHost()) continue;
                    NodeEntity nodeEntity = nodeEntities.get(registeredHostnameOf(node));
                    if (nodeEntity == null) continue;

                    boolean updatedHost = false;
                    Optional<String> modelName = modelNameOf(nodeEntity);
                    if (modelName.isPresent() && !modelName.equals(node.modelName())) {
                        nodeRepository.updateModel(zone, node.hostname().value(), modelName.get());
                        updatedHost = true;
                    }

                    Optional<String> switchHostname = nodeEntity.switchHostname();
                    if (switchHostname.isPresent() && !switchHostname.equals(node.switchHostname())) {
                        nodeRepository.updateSwitchHostname(zone, node.hostname().value(), switchHostname.get());
                        updatedHost = true;
                    }

                    if (updatedHost) {
                        hostsUpdated++;
                    }
                }
            }
        } finally {
            if (hostsUpdated > 0) {
                LOG.info("Updated information for " + hostsUpdated + " hosts(s)");
            }
        }
        return 0.0;
    }

    private static Optional<String> modelNameOf(NodeEntity nodeEntity) {
        if (nodeEntity.manufacturer().isEmpty() || nodeEntity.model().isEmpty()) return Optional.empty();
        return Optional.of(nodeEntity.manufacturer().get() + " " + nodeEntity.model().get());
    }

    /** Returns the hostname that given host is registered under in the {@link EntityService} */
    private static String registeredHostnameOf(Node host) {
        String hostname = host.hostname().value();
        if (!host.type().isHost()) return hostname;
        Matcher matcher = HOST_PATTERN.matcher(hostname);
        if (!matcher.matches()) return hostname;
        return matcher.replaceFirst("$1$2");
    }

}
