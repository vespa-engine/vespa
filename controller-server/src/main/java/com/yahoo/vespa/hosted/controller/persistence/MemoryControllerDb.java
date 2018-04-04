// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.NotExistsException;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.tenant.UserTenant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A controller db implementation backed by in-memory structures. Useful for testing.
 * 
 * @author Stian Kristoffersen
 */
public class MemoryControllerDb implements ControllerDb {

    private final Map<TenantName, Tenant> tenants = new HashMap<>();
    private final Map<String, Application> applications = new HashMap<>();

    private void createTenant(Tenant tenant) {
        if (tenants.containsKey(tenant.name())) {
            throw new IllegalArgumentException("Tenant '" + tenant + "' already exists");
        }
        tenants.put(tenant.name(), tenant);
    }

    private void updateTenant(Tenant tenant) {
        if (!tenants.containsKey(tenant.name())) {
            throw new NotExistsException(tenant.name().value());
        }
        tenants.put(tenant.name(), tenant);
    }

    @Override
    public void createTenant(UserTenant tenant) {
        createTenant((Tenant) tenant);
    }

    @Override
    public void createTenant(AthenzTenant tenant) {
        createTenant((Tenant) tenant);
    }

    @Override
    public void updateTenant(AthenzTenant tenant) {
        updateTenant((Tenant) tenant);
    }

    @Override
    public void deleteTenant(TenantName name) {
        if (tenants.remove(name) == null) {
            throw new NotExistsException(name.value());
        }
    }

    @Override
    public Optional<Tenant> getTenant(TenantName name) {
        return getTenant(name, Tenant.class);
    }

    @Override
    public Optional<AthenzTenant> getAthenzTenant(TenantName name) {
        return getTenant(name, AthenzTenant.class);
    }

    private <T extends Tenant> Optional<T> getTenant(TenantName name, Class<T> type) {
        return Optional.ofNullable(tenants.get(name)).map(type::cast);
    }

    @Override
    public List<Tenant> listTenants() {
        return new ArrayList<>(tenants.values());
    }

    @Override
    public void store(Application application) {
        applications.put(path(application.id()), application);
    }

    @Override
    public void deleteApplication(ApplicationId applicationId) {
        applications.remove(path(applicationId));
    }

    @Override
    public Optional<Application> getApplication(ApplicationId applicationId) {
        return Optional.ofNullable(applications.get(path(applicationId)));
    }

    @Override
    public List<Application> listApplications() {
        return new ArrayList<>(applications.values());
    }

    @Override
    public List<Application> listApplications(TenantName name) {
        return applications.values().stream()
                                    .filter(a -> a.id().tenant().equals(name))
                                    .collect(Collectors.toList());
    }

}
