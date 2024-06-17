// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.Validation.ChangeContext;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.stream.Collectors.toUnmodifiableSet;

/**
 * If using local LLMs, this validator will make sure that restartOnDeploy is set for
 * configs for this cluster.
 *
 * @author lesters
 */
public class RestartOnDeployForLocalLLMValidator implements ChangeValidator {

    public static final String LOCAL_LLM_COMPONENT = ai.vespa.llm.clients.LocalLLM.class.getName();

    private static final Logger log = Logger.getLogger(RestartOnDeployForLocalLLMValidator.class.getName());

    @Override
    public void validate(ChangeContext context) {
        var previousClustersWithLocalLLM = findClustersWithLocalLLMs(context.previousModel());
        var nextClustersWithLocalLLM = findClustersWithLocalLLMs(context.model());

        // Only restart services if we use a local LLM in both the next and previous generation
        for (var clusterId : intersect(previousClustersWithLocalLLM, nextClustersWithLocalLLM)) {
            String message = "Need to restart services in %s due to use of local LLM".formatted(clusterId);
            context.require(new VespaRestartAction(clusterId, message,
                                                   context.model().getContainerClusters().get(clusterId.value()).getContainers()
                                                          .stream().map(AbstractService::getServiceInfo).toList()));
            log.log(INFO, message);
        }
    }

    private Set<ClusterSpec.Id> findClustersWithLocalLLMs(VespaModel model) {
        return model.getContainerClusters().values().stream()
                .filter(cluster -> cluster.getAllComponents().stream()
                    .anyMatch(component -> component.getClassId().getName().equals(LOCAL_LLM_COMPONENT)))
                .map(ApplicationContainerCluster::id)
                .collect(toUnmodifiableSet());
    }

    private Set<ClusterSpec.Id> intersect(Set<ClusterSpec.Id> a, Set<ClusterSpec.Id> b) {
        Set<ClusterSpec.Id> result = new HashSet<>(a);
        result.retainAll(b);
        return result;
    }

}
