// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import ai.vespa.llm.clients.LlmLocalClientConfig;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.text.Text;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.Validation.ChangeContext;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.component.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.stream.Collectors.toUnmodifiableMap;
import static java.util.stream.Collectors.toUnmodifiableSet;

/**
 * If using local LLMs, this validator will make sure that restartOnDeploy is set for
 * configs for this cluster.
 *
 * @author Lester Solbakken
 * @author glabashnik
 */
public class RestartOnDeployForLocalLLMValidator implements ChangeValidator {

    public static final String LOCAL_LLM_COMPONENT = ai.vespa.llm.clients.LocalLLM.class.getName();

    private static final Logger log = Logger.getLogger(RestartOnDeployForLocalLLMValidator.class.getName());

    @Override
    public void validate(ChangeContext context) {
        Set<ClusterSpec.Id> previousClustersWithLocalLLM = findClustersWithLocalLLMs(context.previousModel());
        Set<ClusterSpec.Id> nextClustersWithLocalLLM = findClustersWithLocalLLMs(context.model());

        // For clusters with local LLM in both generations: always restart on external redeploy,
        // but only restart on internal redeploy if the local LLM config changed.
        for (ClusterSpec.Id clusterId : intersect(previousClustersWithLocalLLM, nextClustersWithLocalLLM)) {
            ApplicationContainerCluster previousCluster = context.previousModel().getContainerClusters().get(
                    clusterId.value());
            ApplicationContainerCluster nextCluster = context.model().getContainerClusters().get(clusterId.value());
            boolean llmConfigChanged = localLLMConfigChanged(
                    context.previousModel(), previousCluster,
                    context.model(), nextCluster
            );

            String message = Text.format("Need to restart services in %s due to use of local LLM", clusterId);
            context.require(new VespaRestartAction(
                    clusterId,
                    message,
                    nextCluster.getContainers().stream().map(AbstractService::getServiceInfo).toList(),
                    !llmConfigChanged,
                    VespaRestartAction.ConfigChange.DEFER_UNTIL_RESTART
            ));
            log.log(INFO, message);
        }
    }

    private boolean localLLMConfigChanged(VespaModel previousModel, ApplicationContainerCluster previousCluster,
                                          VespaModel nextModel, ApplicationContainerCluster nextCluster) {
        Map<String, Component<?, ?>> previousLLMs = findLocalLLMComponents(previousCluster);
        Map<String, Component<?, ?>> nextLLMs = findLocalLLMComponents(nextCluster);

        if (!previousLLMs.keySet().equals(nextLLMs.keySet())) {
            return true;
        }

        for (String id : nextLLMs.keySet()) {
            LlmLocalClientConfig previousConfig = previousModel.getConfig(
                    LlmLocalClientConfig.class, previousLLMs.get(id).getConfigId());
            LlmLocalClientConfig nextConfig = nextModel.getConfig(
                    LlmLocalClientConfig.class, nextLLMs.get(id).getConfigId());

            if (configChanged(previousConfig, nextConfig)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Custom field-by-field comparison is needed because LlmLocalClientConfig.equals() delegates to
     * LeafNode.equals(), which compares the resolved Path value. For model references that have not yet
     * been resolved (e.g. during deployment validation), the resolved value is null regardless of the
     * configured path, so two components with different model paths would incorrectly appear equal.
     * ModelReference.equals() compares the configured path directly and is correct for unresolved refs.
     */
    private boolean configChanged(LlmLocalClientConfig previous, LlmLocalClientConfig next) {
        return !previous.modelReference().equals(next.modelReference())
                || previous.parallelRequests() != next.parallelRequests()
                || previous.maxQueueSize() != next.maxQueueSize()
                || previous.maxQueueWait() != next.maxQueueWait()
                || previous.maxEnqueueWait() != next.maxEnqueueWait()
                || previous.useGpu() != next.useGpu()
                || previous.gpuLayers() != next.gpuLayers()
                || previous.threads() != next.threads()
                || previous.contextSize() != next.contextSize()
                || previous.maxTokens() != next.maxTokens()
                || previous.maxPromptTokens() != next.maxPromptTokens()
                || previous.contextOverflowPolicy() != next.contextOverflowPolicy()
                || previous.seed() != next.seed();
    }

    private Map<String, Component<?, ?>> findLocalLLMComponents(ApplicationContainerCluster cluster) {
        return cluster.getAllComponents().stream()
                .filter(c -> c.getClassId().getName().equals(LOCAL_LLM_COMPONENT))
                .collect(toUnmodifiableMap(c -> c.getComponentId().stringValue(), c -> c));
    }

    private Set<ClusterSpec.Id> findClustersWithLocalLLMs(VespaModel model) {
        return model.getContainerClusters().values().stream()
                .filter(cluster -> !findLocalLLMComponents(cluster).isEmpty())
                .map(ApplicationContainerCluster::id)
                .collect(toUnmodifiableSet());
    }

    private Set<ClusterSpec.Id> intersect(Set<ClusterSpec.Id> a, Set<ClusterSpec.Id> b) {
        Set<ClusterSpec.Id> result = new HashSet<>(a);
        result.retainAll(b);
        return result;
    }

}
