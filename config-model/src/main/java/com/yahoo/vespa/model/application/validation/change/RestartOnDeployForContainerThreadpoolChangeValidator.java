// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.container.handler.threadpool.ContainerThreadpoolConfig;
import com.yahoo.text.Text;
import com.yahoo.vespa.model.application.validation.Validation.ChangeContext;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.ContainerThreadpool;
import com.yahoo.vespa.model.utils.internal.ReflectionUtil;

import java.util.Map;

import static com.yahoo.config.model.api.ConfigChangeRestartAction.ConfigChange.DEFER_UNTIL_RESTART;
import static java.util.stream.Collectors.toMap;

/**
 * Sets restartOnDeploy for existing clusters when a container threadpool has changed,
 * e.g. {@code <search><threadpool>}. Threadpools are sized when constructed and are
 * not resized without restarting the container.
 *
 * @author glebashnik
 */
public class RestartOnDeployForContainerThreadpoolChangeValidator implements ChangeValidator {

    @Override
    public void validate(ChangeContext context) {
        // Restart is only triggered automatically for hosted Vespa,
        // for self-hosted a deferred restart would freeze all config changes until a manual restart.
        if ( ! context.deployState().isHosted()) return;

        // Validate existing clusters only, new clusters do not need restartOnDeploy.
        for (var previousCluster : context.previousModel().getContainerClusters().values()) {
            var nextCluster = context.model().getContainerClusters().get(previousCluster.name());
            if (nextCluster == null || nextCluster.getContainers().isEmpty()) continue;

            var previousPools = findThreadpools(previousCluster);
            var nextPools = findThreadpools(nextCluster);

            for (var configId : nextPools.keySet()) {
                // Threadpools that are new in the next model do not need restart.
                if ( ! previousPools.containsKey(configId)) continue;

                // Resolve configs through the models to include user <config> overrides.
                var previousConfig = context.previousModel().getConfig(ContainerThreadpoolConfig.class, configId);
                var nextConfig = context.model().getConfig(ContainerThreadpoolConfig.class, configId);

                var changes = ReflectionUtil.getChangesRequiringRestart(previousConfig, nextConfig);
                if (changes.needsRestart()) {
                    var message = Text.format("Container threadpool '%s' has changed, need to restart services in %s:\n%s",
                                              nextConfig.name(), nextCluster.id(), changes.toString(""));
                    context.require(VespaRestartAction.ofCluster(nextCluster, message, DEFER_UNTIL_RESTART));
                }
            }
        }
    }

    private static Map<String, ContainerThreadpool> findThreadpools(ApplicationContainerCluster cluster) {
        return cluster.getAllComponents().stream()
                      .filter(ContainerThreadpool.class::isInstance)
                      .map(ContainerThreadpool.class::cast)
                      .collect(toMap(ContainerThreadpool::getConfigId, threadpool -> threadpool));
    }

}
