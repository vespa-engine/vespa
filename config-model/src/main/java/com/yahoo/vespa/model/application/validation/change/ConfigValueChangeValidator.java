// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.ChangesRequiringRestart;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducerRoot;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.model.Service;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.RestartConfigs;
import com.yahoo.vespa.model.utils.internal.ReflectionUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

/**
 * Compares the config instances in the current and next Vespa model to determine if some services will require restart.
 * The configs used by a given service is deduced from the
 * {@link com.yahoo.vespa.model.application.validation.RestartConfigs} annotation.
 *
 * @author bjorncs
 */
public class ConfigValueChangeValidator implements ChangeValidator {

    /** Inspects the configuration in the new and old Vespa model to determine which services that require restart */
    @Override
    public List<ConfigChangeAction> validate(VespaModel currentModel, VespaModel nextModel, DeployState deployState) {
        return findConfigChangesFromModels(currentModel, nextModel, deployState.getDeployLogger()).toList();
    }

    public Stream<ConfigChangeAction> findConfigChangesFromModels(AbstractConfigProducerRoot currentModel,
                                                                  AbstractConfigProducerRoot nextModel,
                                                                  DeployLogger logger) {
        return nextModel.getDescendantServices().stream()
                .map(service -> findConfigChangeActionForService(service, currentModel, nextModel, logger))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    private Optional<ConfigChangeAction> findConfigChangeActionForService(Service service,
                                                                          AbstractConfigProducerRoot currentModel,
                                                                          AbstractConfigProducerRoot nextModel,
                                                                          DeployLogger logger) {
        List<ChangesRequiringRestart> changes = findConfigChangesForService(service, currentModel, nextModel, logger)
            .toList();
        if (changes.isEmpty()) {
            return Optional.empty();
        }
        String description = createDescriptionOfConfigChanges(changes.stream());
        ClusterSpec.Id id = service.getHost().spec().membership().isPresent() ?
                            service.getHost().spec().membership().get().cluster().id() :
                            ClusterSpec.Id.from(service.getConfigId());
        return Optional.of(new VespaRestartAction(id, description, service.getServiceInfo()));
    }

    private Stream<ChangesRequiringRestart> findConfigChangesForService(Service service,
                                                                        AbstractConfigProducerRoot currentModel,
                                                                        AbstractConfigProducerRoot nextModel,
                                                                        DeployLogger logger) {
        Class<? extends Service> serviceClass = service.getClass();
        if (!currentModel.getService(service.getConfigId()).isPresent()) {
            // Service does not exist in the current model.
            return Stream.empty();
        }
        return getConfigInstancesFromServiceAnnotations(serviceClass)
                .map(configClass -> compareConfigFromCurrentAndNextModel(service, configClass, currentModel, nextModel, logger))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(ChangesRequiringRestart::needsRestart);
    }

    private static String createDescriptionOfConfigChanges(Stream<ChangesRequiringRestart> changesStream) {
        return changesStream
                .map(changes -> changes.toString(""))
                .collect(joining("\n"));
    }

    /**
     * Returns the ConfigInstances classes from the annotation on the given Service class ,
     * including the ConfigInstances annotated on any of its super classes.
     * NOTE: Only the super classes that are subclass of Service are inspected.
     */
    private static Stream<Class<? extends ConfigInstance>> getConfigInstancesFromServiceAnnotations(Class<? extends Service> serviceClass) {
        List<Class<?>> classHierarchy = ReflectionUtil.getAllSuperclasses(serviceClass);
        classHierarchy.add(serviceClass);
        return classHierarchy.stream()
                .filter(Service.class::isAssignableFrom)
                .filter(clazz -> clazz.isAnnotationPresent(RestartConfigs.class))
                .map(clazz -> {
                    RestartConfigs annotation = clazz.getDeclaredAnnotation(RestartConfigs.class);
                    if (annotation.value().length == 0) {
                        throw new IllegalStateException(String.format(
                                "%s has a %s annotation with no ConfigInstances given as argument.",
                                clazz.getSimpleName(), RestartConfigs.class.getSimpleName()));
                    }
                    return annotation;
                })
                .map(RestartConfigs::value)
                .flatMap(Arrays::stream)
                .distinct();
    }

    private Optional<ChangesRequiringRestart> compareConfigFromCurrentAndNextModel(Service service,
                                                                                   Class<? extends ConfigInstance> configClass,
                                                                                   AbstractConfigProducerRoot currentModel,
                                                                                   AbstractConfigProducerRoot nextModel,
                                                                                   DeployLogger logger) {

        if (!hasConfigFieldsFlaggedWithRestart(configClass, service.getClass())) {
            logger.logApplicationPackage(Level.FINE, String.format("%s is listed in the annotation for %s, " +
                            "but does not have any restart flags in its config definition.",
                    configClass.getSimpleName(), service.getClass().getSimpleName()));
            return Optional.empty();
        }

        Optional<ConfigInstance> nextConfig = getConfigFromModel(nextModel, configClass, service.getConfigId());
        if (!nextConfig.isPresent()) {
            logger.logApplicationPackage(Level.FINE, String.format(
                    "%s is listed as restart config for %s, but the config does not exist in the new model.",
                    configClass.getSimpleName(), service.getClass().getSimpleName()));
            return Optional.empty();
        }

        Optional<ConfigInstance> currentConfig = getConfigFromModel(currentModel, configClass, service.getConfigId());
        if (!currentConfig.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(ReflectionUtil.getChangesRequiringRestart(currentConfig.get(), nextConfig.get()));
    }

    private static boolean hasConfigFieldsFlaggedWithRestart(
            Class<? extends ConfigInstance> configClass, Class<? extends Service> serviceClass) {
        if (!ReflectionUtil.hasRestartMethods(configClass)) {
            throw new IllegalStateException(String.format(
                    "%s is listed as restart config for %s but does not contain any restart inspection methods.",
                    configClass.getSimpleName(), serviceClass.getSimpleName()));
        }
        return ReflectionUtil.containsFieldsFlaggedWithRestart(configClass);
    }

    private static Optional<ConfigInstance> getConfigFromModel(
            AbstractConfigProducerRoot configModel, Class<? extends ConfigInstance> configClass, String configKey) {
        try {
            return Optional.ofNullable(configModel.getConfig(configClass, configKey));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
