// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.service.model;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.service.monitor.DuperModelListener;
import com.yahoo.vespa.service.monitor.ServiceHostListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Allows a {@link ServiceHostListener} to register with the duper model as a {@link DuperModelListener}.
 *
 * <p>This class is not thread-safe: As with the DuperModelListener, events from the duper model
 * happens within an exclusive duper model lock.</p>
 */
public class ServiceHostListenerAdapter implements DuperModelListener {
    private final ServiceHostListener listener;
    private final ModelGenerator modelGenerator;

    private final Map<ApplicationInstanceReference, Set<HostName>> hostnamesByReference = new HashMap<>();

    public static ServiceHostListenerAdapter asDuperModelListener(ServiceHostListener listener,
                                                                  ModelGenerator generator) {
        return new ServiceHostListenerAdapter(listener, generator);
    }

    ServiceHostListenerAdapter(ServiceHostListener listener, ModelGenerator generator) {
        this.listener = listener;
        this.modelGenerator = generator;
    }

    @Override
    public void applicationActivated(ApplicationInfo application) {
        Set<HostName> newHostnames = application.getModel().getHosts().stream()
                .map(HostInfo::getHostname)
                .map(HostName::new)
                .collect(Collectors.toSet());

        var reference = toApplicationInstanceReference(application.getApplicationId());

        Set<HostName> oldHostnames = hostnamesByReference.get(reference);
        if (!Objects.equals(newHostnames, oldHostnames)) {
            hostnamesByReference.put(reference, newHostnames);
            listener.onApplicationActivate(reference, newHostnames);
        }
    }

    @Override
    public void applicationRemoved(ApplicationId applicationId) {
        var reference = toApplicationInstanceReference(applicationId);

        if (hostnamesByReference.remove(reference) != null) {
            listener.onApplicationRemove(reference);
        }
    }

    @Override
    public void bootstrapComplete() {
    }

    private ApplicationInstanceReference toApplicationInstanceReference(ApplicationId applicationId) {
        return modelGenerator.toApplicationInstanceReference(applicationId);
    }
}
