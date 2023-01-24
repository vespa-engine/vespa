// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducerRoot;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.model.Service;
import com.yahoo.vespa.model.VespaModel;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Compares the startup command for the services in the next model with the ones in the current model.
 * If the startup command has changes, a change entry is created and reported back.
 *
 * @author bjorncs
 */
public class StartupCommandChangeValidator implements ChangeValidator {

    @Override
    public List<ConfigChangeAction> validate(VespaModel currentModel, VespaModel nextModel, DeployState deployState) {
        return findServicesWithChangedStartupCommand(currentModel, nextModel).toList();
    }

    public Stream<ConfigChangeAction> findServicesWithChangedStartupCommand(AbstractConfigProducerRoot currentModel,
                                                                            AbstractConfigProducerRoot nextModel) {
        return nextModel.getDescendantServices().stream()
                .map(nextService -> currentModel.getService(nextService.getConfigId())
                                                .flatMap(currentService -> compareStartupCommand(currentService, nextService)))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    private Optional<ConfigChangeAction> compareStartupCommand(Service currentService, Service nextService) {
        String currentCommand = currentService.getStartupCommand().orElse("");
        String nextCommand = nextService.getStartupCommand().orElse("");

        if (Objects.equals(currentCommand, nextCommand)) return Optional.empty();

        String message = String.format("Startup command for '%s' has changed.\nNew command: %s\nCurrent command: %s",
                                       currentService.getServiceName(), nextCommand, currentCommand);
        return Optional.of(new VespaRestartAction(ClusterSpec.Id.from(currentService.getConfigId()),
                                                  message,
                                                  currentService.getServiceInfo()));
    }

}
