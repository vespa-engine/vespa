// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athens.mock;

import com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.identifiers.AthensDomain;
import com.yahoo.vespa.hosted.controller.api.integration.athens.ApplicationAction;
import com.yahoo.vespa.hosted.controller.api.integration.athens.AthensPrincipal;
import com.yahoo.vespa.hosted.controller.api.integration.athens.AthensPublicKey;
import com.yahoo.vespa.hosted.controller.api.integration.athens.AthensService;
import com.yahoo.vespa.hosted.controller.api.integration.athens.ZmsClient;
import com.yahoo.vespa.hosted.controller.api.integration.athens.ZmsException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toList;

/**
 * @author bjorncs
 */
public class ZmsClientMock implements ZmsClient {

    private static final Logger log = Logger.getLogger(ZmsClientMock.class.getName());

    private final AthensDbMock athens;

    public ZmsClientMock(AthensDbMock athens) {
        this.athens = athens;
    }

    @Override
    public void createTenant(AthensDomain tenantDomain) {
        log("createTenant(tenantDomain='%s')", tenantDomain);
        getDomainOrThrow(tenantDomain, false).isVespaTenant = true;
    }

    @Override
    public void deleteTenant(AthensDomain tenantDomain) {
        log("deleteTenant(tenantDomain='%s')", tenantDomain);
        AthensDbMock.Domain domain = getDomainOrThrow(tenantDomain, false);
        domain.isVespaTenant = false;
        domain.applications.clear();
        domain.tenantAdmins.clear();
    }

    @Override
    public void addApplication(AthensDomain tenantDomain, ApplicationId applicationName) {
        log("addApplication(tenantDomain='%s', applicationName='%s')", tenantDomain, applicationName);
        AthensDbMock.Domain domain = getDomainOrThrow(tenantDomain, true);
        if (!domain.applications.containsKey(applicationName)) {
            domain.applications.put(applicationName, new AthensDbMock.Application());
        }
    }

    @Override
    public void deleteApplication(AthensDomain tenantDomain, ApplicationId applicationName) {
        log("addApplication(tenantDomain='%s', applicationName='%s')", tenantDomain, applicationName);
        getDomainOrThrow(tenantDomain, true).applications.remove(applicationName);
    }

    @Override
    public boolean hasApplicationAccess(AthensPrincipal principal, ApplicationAction action, AthensDomain tenantDomain, ApplicationId applicationName) {
        log("hasApplicationAccess(principal='%s', action='%s', tenantDomain='%s', applicationName='%s')",
                 principal, action, tenantDomain, applicationName);
        AthensDbMock.Domain domain = getDomainOrThrow(tenantDomain, true);
        AthensDbMock.Application application = domain.applications.get(applicationName);
        if (application == null) {
            throw zmsException(400, "Application '%s' not found", applicationName);
        }
        return domain.admins.contains(principal) || application.acl.get(action).contains(principal);
    }

    @Override
    public boolean hasTenantAdminAccess(AthensPrincipal principal, AthensDomain tenantDomain) {
        log("hasTenantAdminAccess(principal='%s', tenantDomain='%s')", principal, tenantDomain);
        return isDomainAdmin(principal, tenantDomain) ||
                getDomainOrThrow(tenantDomain, true).tenantAdmins.contains(principal);
    }

    @Override
    public boolean isDomainAdmin(AthensPrincipal principal, AthensDomain domain) {
        log("isDomainAdmin(principal='%s', domain='%s')", principal, domain);
        return getDomainOrThrow(domain, false).admins.contains(principal);
    }

    @Override
    public List<AthensDomain> getDomainList(String prefix) {
        log("getDomainList()");
        return new ArrayList<>(athens.domains.keySet());
    }

    @Override
    public List<AthensDomain> getTenantDomainsForUser(AthensPrincipal principal) {
        log("getTenantDomainsForUser(principal='%s')", principal);
        return athens.domains.values().stream()
                .filter(domain -> domain.tenantAdmins.contains(principal) || domain.admins.contains(principal))
                .map(domain -> domain.name)
                .collect(toList());
    }

    @Override
    public AthensPublicKey getPublicKey(AthensService service, String keyId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<AthensPublicKey> getPublicKeys(AthensService service) {
        throw new UnsupportedOperationException();
    }

    private AthensDbMock.Domain getDomainOrThrow(AthensDomain domainName, boolean verifyVespaTenant) {
        AthensDbMock.Domain domain = Optional.ofNullable(athens.domains.get(domainName))
                .orElseThrow(() -> zmsException(400, "Domain '%s' not found", domainName));
        if (verifyVespaTenant && !domain.isVespaTenant) {
            throw zmsException(400, "Domain not a Vespa tenant: '%s'", domainName);
        }
        return domain;
    }

    private static ZmsException zmsException(int code, String message, Object... args) {
        return new ZmsException(new RuntimeException(String.format(message, args)), code);
    }

    private static void log(String format, Object... args) {
        log.log(Level.INFO, String.format(format, args));
    }

}
