// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Condition that checks if a container environment for testcontainers is available.
 *
 * @author bjorncs
 */
class ContainerEnvironmentAvailableCondition implements ExecutionCondition {
    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        try {
            org.testcontainers.DockerClientFactory.instance().client();
            return ConditionEvaluationResult.enabled("Container environment is available");
        } catch (Exception e) {
            return ConditionEvaluationResult.disabled("Container environment is not available: " + e.getMessage());
        }
    }
}

