// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.container.QrConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.vespa.model.container.ApplicationContainer;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.ContainerCluster;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Returns a restart action for each container that has turned on {@link QrConfig#restartOnDeploy()},
 * and for all application containers when switching major versions. The latter is detected by comparing
 * the Vespa version the models are built for, which is the current node version, to the wanted node version
 * in the models. When these do not match, current config for the node is triggering a major version switch,
 * and the application package contained in the new config is incompatible with the current node version.
 *
 * @author bjorncs
 * @author jonmv
 */
public class ContainerRestartValidator implements ChangeValidator {

    @Override
    public List<ConfigChangeAction> validate(VespaModel currentModel, VespaModel nextModel, ValidationOverrides ignored, Instant now) {
        boolean switchingMajor = nextModel.version().getMajor() != nextModel.wantedNodeVersion().getMajor();
        List<ConfigChangeAction> actions = new ArrayList<>();
        for (ApplicationContainerCluster cluster : nextModel.getContainerClusters().values()) {
            for (Container container : cluster.getContainers()) {
                if (isExistingContainer(container, currentModel)) {
                    String reason = shouldContainerRestartOnDeploy(container, nextModel)
                                    ? "configured to always restart on deploy"
                                    : switchingMajor ? "switching Vespa major version" : null;
                    if (reason != null) actions.add(createConfigChangeAction(cluster.id(), container, reason));
                }
            }
        }
        return actions;
    }

    private static ConfigChangeAction createConfigChangeAction(ClusterSpec.Id id, Container container, String reason) {
        return new VespaRestartAction(id, createMessage(container, reason), container.getServiceInfo(), true);
    }

    private static String createMessage(Container container, String reason) {
        return String.format("Container '%s' is %s.", container.getConfigId(), reason);
    }

    private static boolean shouldContainerRestartOnDeploy(Container container, VespaModel nextModel) {
        QrConfig config = nextModel.getConfig(QrConfig.class, container.getConfigId());
        return config.restartOnDeploy();
    }

    private static boolean isExistingContainer(Container container, VespaModel currentModel) {
        return currentModel.getService(container.getConfigId()).isPresent();
    }

}
