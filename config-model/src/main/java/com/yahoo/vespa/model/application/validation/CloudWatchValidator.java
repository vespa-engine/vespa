// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.admin.monitoring.MetricsConsumer;

import java.util.List;

/**
 * @author gjoranv
 */
public class CloudWatchValidator extends Validator {

    @Override
    public void validate(VespaModel model, DeployState deployState) {
        if (!deployState.isHosted()) return;
        if (deployState.zone().system().isPublic()) return;
        if (model.getAdmin().getApplicationType() != ConfigModelContext.ApplicationType.DEFAULT) return;

        var offendingConsumers = model.getAdmin().getUserMetrics().getConsumers().values().stream()
                .filter(consumer -> !consumer.cloudWatches().isEmpty())
                .toList();

        if (! offendingConsumers.isEmpty()) {
            throw new IllegalArgumentException("CloudWatch cannot be set up for non-public hosted Vespa and must " +
                                                       "be removed for consumers: " + consumerIds(offendingConsumers));
        }
    }

    private List<String> consumerIds(List<MetricsConsumer> offendingConsumers) {
        return offendingConsumers.stream().map(MetricsConsumer::id).toList();
    }

}
