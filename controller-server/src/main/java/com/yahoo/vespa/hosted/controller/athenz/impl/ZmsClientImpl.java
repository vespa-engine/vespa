// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.impl;

import com.yahoo.athenz.auth.util.Crypto;
import com.yahoo.athenz.zms.DomainList;
import com.yahoo.athenz.zms.ProviderResourceGroupRoles;
import com.yahoo.athenz.zms.PublicKeyEntry;
import com.yahoo.athenz.zms.ServiceIdentity;
import com.yahoo.athenz.zms.Tenancy;
import com.yahoo.athenz.zms.TenantRoleAction;
import com.yahoo.athenz.zms.ZMSClient;
import com.yahoo.athenz.zms.ZMSClientException;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.identifiers.AthenzDomain;
import com.yahoo.vespa.hosted.controller.athenz.ApplicationAction;
import com.yahoo.vespa.hosted.controller.athenz.AthenzPrincipal;
import com.yahoo.vespa.hosted.controller.athenz.AthenzPublicKey;
import com.yahoo.vespa.hosted.controller.athenz.AthenzService;
import com.yahoo.vespa.hosted.controller.athenz.ZmsClient;
import com.yahoo.vespa.hosted.controller.athenz.ZmsException;
import com.yahoo.vespa.hosted.controller.athenz.config.AthenzConfig;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toList;

/**
 * @author bjorncs
 */
public class ZmsClientImpl implements ZmsClient {

    private static final Logger log = Logger.getLogger(ZmsClientImpl.class.getName());
    private final ZMSClient zmsClient;
    private final AthenzService service;

    ZmsClientImpl(ZMSClient zmsClient, AthenzConfig config) {
        this.zmsClient = zmsClient;
        this.service = new AthenzService(config.domain(), config.service().name());
    }

    @Override
    public void createTenant(AthenzDomain tenantDomain) {
        log("putTenancy(tenantDomain=%s, service=%s)", tenantDomain, service);
        runOrThrow(() -> {
            Tenancy tenancy = new Tenancy()
                    .setDomain(tenantDomain.id())
                    .setService(service.toFullServiceName())
                    .setResourceGroups(Collections.emptyList());
            zmsClient.putTenancy(tenantDomain.id(), service.toFullServiceName(), /*auditref*/null, tenancy);
        });
    }

    @Override
    public void deleteTenant(AthenzDomain tenantDomain) {
        log("deleteTenancy(tenantDomain=%s, service=%s)", tenantDomain, service);
        runOrThrow(() -> zmsClient.deleteTenancy(tenantDomain.id(), service.toFullServiceName(), /*auditref*/null));
    }

    @Override
    public void addApplication(AthenzDomain tenantDomain, ApplicationId applicationName) {
        List<TenantRoleAction> tenantRoleActions = createTenantRoleActions();
        log("putProviderResourceGroupRoles(" +
                        "tenantDomain=%s, providerDomain=%s, service=%s, resourceGroup=%s, roleActions=%s)",
                tenantDomain, service.getDomain().id(), service.getServiceName(), applicationName, tenantRoleActions);
        runOrThrow(() -> {
            ProviderResourceGroupRoles resourceGroupRoles = new ProviderResourceGroupRoles()
                    .setDomain(service.getDomain().id())
                    .setService(service.getServiceName())
                    .setTenant(tenantDomain.id())
                    .setResourceGroup(applicationName.id())
                    .setRoles(tenantRoleActions);
            zmsClient.putProviderResourceGroupRoles(
                    tenantDomain.id(), service.getDomain().id(), service.getServiceName(),
                    applicationName.id(), /*auditref*/null, resourceGroupRoles);
        });
    }

    @Override
    public void deleteApplication(AthenzDomain tenantDomain, ApplicationId applicationName) {
        log("deleteProviderResourceGroupRoles(tenantDomain=%s, providerDomain=%s, service=%s, resourceGroup=%s)",
                tenantDomain, service.getDomain().id(), service.getServiceName(), applicationName);
        runOrThrow(() -> {
            zmsClient.deleteProviderResourceGroupRoles(
                    tenantDomain.id(), service.getDomain().id(), service.getServiceName(), applicationName.id(), /*auditref*/null);
        });
    }

    @Override
    public boolean hasApplicationAccess(
            AthenzPrincipal principal, ApplicationAction action, AthenzDomain tenantDomain, ApplicationId applicationName) {
        return hasAccess(
                action.name(), applicationResourceString(tenantDomain, applicationName), principal);
    }

    @Override
    public boolean hasTenantAdminAccess(AthenzPrincipal principal, AthenzDomain tenantDomain) {
        return hasAccess(TenantAction._modify_.name(), tenantResourceString(tenantDomain), principal);
    }

    /**
     * Used when creating tenancies. As there are no tenancy policies at this point,
     * we cannot use {@link #hasTenantAdminAccess(AthenzPrincipal, AthenzDomain)}
     */
    @Override
    public boolean isDomainAdmin(AthenzPrincipal principal, AthenzDomain domain) {
        log("getMembership(domain=%s, role=%s, principal=%s)", domain, "admin", principal);
        return getOrThrow(
                () -> zmsClient.getMembership(domain.id(), "admin", principal.toYRN()).getIsMember());
    }

    @Override
    public List<AthenzDomain> getDomainList(String prefix) {
        log.log(LogLevel.DEBUG, String.format("getDomainList(prefix=%s)", prefix));
        return getOrThrow(
                () -> {
                    DomainList domainList = zmsClient.getDomainList(
                            /*limit*/null, /*skip*/null, prefix, /*depth*/null, /*domain*/null,
                            /*productId*/ null, /*modifiedSince*/null);
                    return toAthenzDomains(domainList.getNames());
                });
    }

    @Override
    public AthenzPublicKey getPublicKey(AthenzService service, String keyId) {
        log("getPublicKeyEntry(domain=%s, service=%s, keyId=%s)", service.getDomain().id(), service.getServiceName(), keyId);
        return getOrThrow(() -> {
            PublicKeyEntry entry = zmsClient.getPublicKeyEntry(service.getDomain().id(), service.getServiceName(), keyId);
            return fromYbase64EncodedKey(entry.getKey(), keyId);
        });
    }

    @Override
    public List<AthenzPublicKey> getPublicKeys(AthenzService service) {
        log("getServiceIdentity(domain=%s, service=%s)", service.getDomain().id(), service.getServiceName());
        return getOrThrow(() -> {
            ServiceIdentity serviceIdentity = zmsClient.getServiceIdentity(service.getDomain().id(), service.getServiceName());
            return toAthenzPublicKeys(serviceIdentity.getPublicKeys());
        });
    }

    private static AthenzPublicKey fromYbase64EncodedKey(String encodedKey, String keyId) {
        return new AthenzPublicKey(Crypto.loadPublicKey(Crypto.ybase64DecodeString(encodedKey)), keyId);
    }

    private static List<TenantRoleAction> createTenantRoleActions() {
        return Arrays.stream(ApplicationAction.values())
                .map(action -> new TenantRoleAction().setAction(action.name()).setRole(action.roleName))
                .collect(toList());
    }

    private static List<AthenzDomain> toAthenzDomains(List<String> domains) {
        return domains.stream().map(AthenzDomain::new).collect(toList());
    }

    private static List<AthenzPublicKey> toAthenzPublicKeys(List<PublicKeyEntry> publicKeys) {
        return publicKeys.stream()
                .map(entry -> fromYbase64EncodedKey(entry.getKey(), entry.getId()))
                .collect(toList());
    }

    private boolean hasAccess(String action, String resource, AthenzPrincipal principal) {
        log("getAccess(action=%s, resource=%s, principal=%s)", action, resource, principal);
        return getOrThrow(
                () -> zmsClient.getAccess(action, resource, /*trustDomain*/null, principal.toYRN()).getGranted());
    }

    private static void log(String format, Object... args) {
        log.log(LogLevel.DEBUG, String.format(format, args));
    }

    private static void runOrThrow(Runnable wrappedCode) {
        try {
            wrappedCode.run();
        } catch (ZMSClientException e) {
            logWarning(e);
            throw new ZmsException(e);
        }
    }

    private static <T> T getOrThrow(Supplier<T> wrappedCode) {
        try {
            return wrappedCode.get();
        } catch (ZMSClientException e) {
            logWarning(e);
            throw new ZmsException(e);
        }
    }

    private static void logWarning(ZMSClientException e) {
        log.warning("Error from Athenz: " + e.getMessage());
    }

    private String resourceStringPrefix(AthenzDomain tenantDomain) {
        return String.format("%s:service.%s.tenant.%s",
                             service.getDomain().id(), service.getServiceName(), tenantDomain.id());
    }

    private String tenantResourceString(AthenzDomain tenantDomain) {
        return resourceStringPrefix(tenantDomain) + ".wildcard";
    }

    private String applicationResourceString(AthenzDomain tenantDomain, ApplicationId applicationName) {
        return resourceStringPrefix(tenantDomain) + "." + "res_group" + "." + applicationName.id() + ".wildcard";
    }

    private enum TenantAction {
        // This is meant to match only the '*' action of the 'admin' role.
        // If needed, we can replace it with 'create', 'delete' etc. later.
        _modify_
    }

}
