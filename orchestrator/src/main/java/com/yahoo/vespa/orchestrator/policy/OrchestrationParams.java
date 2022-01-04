// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.InfrastructureApplication;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Parameters controlling orchestration.
 *
 * @author hakonhall
 */
public class OrchestrationParams {
    private final Map<ApplicationId, ApplicationParams> applicationParams;

    public static class Builder {
        private final Map<ApplicationId, ApplicationParams> applicationParams = new HashMap<>();

        public Builder() {}

        public Builder addApplicationParams(InfrastructureApplication application, ApplicationParams params) {
            this.applicationParams.put(application.id(), params);
            return this;
        }

        public OrchestrationParams build() {
            return new OrchestrationParams(applicationParams);
        }
    }

    private OrchestrationParams(Map<ApplicationId, ApplicationParams> applicationParams) {
        this.applicationParams = Map.copyOf(applicationParams);
    }

    public ApplicationParams getApplicationParams(ApplicationId applicationId) {
        return applicationParams.getOrDefault(applicationId, ApplicationParams.getDefault());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrchestrationParams that = (OrchestrationParams) o;
        return applicationParams.equals(that.applicationParams);
    }

    @Override
    public int hashCode() {
        return Objects.hash(applicationParams);
    }
}
