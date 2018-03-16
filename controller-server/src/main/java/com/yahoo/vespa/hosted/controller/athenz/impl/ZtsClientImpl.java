// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.impl;

import com.yahoo.athenz.zts.TenantDomains;
import com.yahoo.athenz.zts.ZTSClient;
import com.yahoo.athenz.zts.ZTSClientException;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ZtsClient;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ZtsException;
import com.yahoo.vespa.hosted.controller.athenz.config.AthenzConfig;

import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toList;

/**
 * @author bjorncs
 */
public class ZtsClientImpl implements ZtsClient {

    private static final Logger log = Logger.getLogger(ZtsClientImpl.class.getName());

    private final ZTSClient ztsClient;
    private final AthenzService service;

    public ZtsClientImpl(ZTSClient ztsClient, AthenzConfig config) {
        this.ztsClient = ztsClient;
        this.service = new AthenzService(config.domain(), config.service().name());
    }

    @Override
    public List<AthenzDomain> getTenantDomainsForUser(AthenzIdentity identity) {
        return getOrThrow(() -> {
            log.log(LogLevel.DEBUG, String.format(
                    "getTenantDomains(domain=%s, identity=%s, rolename=admin, service=%s)",
                    service.getDomain().getName(), identity.getFullName(), service.getFullName()));
            TenantDomains domains = ztsClient.getTenantDomains(
                    service.getDomain().getName(), identity.getFullName(), "admin", service.getName());
            return domains.getTenantDomainNames().stream()
                    .map(AthenzDomain::new)
                    .collect(toList());
        });
    }

    private static <T> T getOrThrow(Supplier<T> wrappedCode) {
        try {
            return wrappedCode.get();
        } catch (ZTSClientException e) {
            log.warning("Error from Athenz: " + e.getMessage());
            throw new ZtsException(e.getCode(), e);
        }
    }

}
