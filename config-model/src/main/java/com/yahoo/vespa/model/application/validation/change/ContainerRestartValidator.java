// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.container.QrConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.vespa.model.container.Container;

import java.time.Instant;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Returns a restart action for each container that has turned on {@link QrConfig#restartOnDeploy}.
 *
 * @author bjorncs
 */
public class ContainerRestartValidator implements ChangeValidator {

    @Override
    public List<ConfigChangeAction> validate(VespaModel currentModel, VespaModel nextModel, ValidationOverrides ignored, 
                                             Instant now) {
        return nextModel.getContainerClusters().values().stream()
                .flatMap(cluster -> cluster.getContainers().stream())
                .filter(container -> isExistingContainer(container, currentModel))
                .filter(container -> shouldContainerRestartOnDeploy(container, nextModel))
                .map(ContainerRestartValidator::createConfigChangeAction)
                .collect(toList());
    }

    private static ConfigChangeAction createConfigChangeAction(Container container) {
        return new VespaRestartAction(createMessage(container), container.getServiceInfo());
    }

    private static String createMessage(Container container) {
        return String.format("Container '%s' is configured to always restart on deploy.", container.getConfigId());
    }

    private static boolean shouldContainerRestartOnDeploy(Container container, VespaModel nextModel) {
        QrConfig config = nextModel.getConfig(QrConfig.class, container.getConfigId());
        return config.restartOnDeploy();
    }

    private static boolean isExistingContainer(Container container, VespaModel currentModel) {
        return currentModel.getService(container.getConfigId()).isPresent();
    }

}
