package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.admin.monitoring.MetricsConsumer;

import java.util.List;

import static java.util.stream.Collectors.toList;

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
                .collect(toList());

        if (! offendingConsumers.isEmpty()) {
            throw new IllegalArgumentException("CloudWatch cannot be set up for non-public hosted Vespa and must " +
                                                       "be removed for consumers: " + consumerIds(offendingConsumers));
        }
    }

    private List<String> consumerIds(List<MetricsConsumer> offendingConsumers) {
        return offendingConsumers.stream().map(MetricsConsumer::getId).collect(toList());
    }

}
