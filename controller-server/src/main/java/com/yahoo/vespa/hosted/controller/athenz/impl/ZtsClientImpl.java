// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.impl;

import com.yahoo.athenz.zts.TenantDomains;
import com.yahoo.athenz.zts.ZTSClient;
import com.yahoo.athenz.zts.ZTSClientException;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.controller.api.identifiers.AthenzDomain;
import com.yahoo.vespa.hosted.controller.athenz.AthenzPrincipal;
import com.yahoo.vespa.hosted.controller.athenz.AthenzService;
import com.yahoo.vespa.hosted.controller.athenz.ZtsClient;
import com.yahoo.vespa.hosted.controller.athenz.ZtsException;
import com.yahoo.vespa.hosted.controller.athenz.config.AthenzConfig;

import java.util.List;
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
    public List<AthenzDomain> getTenantDomainsForUser(AthenzPrincipal principal) {
        log.log(LogLevel.DEBUG, String.format(
                "getTenantDomains(domain=%s, username=%s, rolename=admin, service=%s)",
                service.getDomain().id(), principal, service.getServiceName()));
        try {
            TenantDomains domains = ztsClient.getTenantDomains(
                    service.getDomain().id(), principal.toYRN(), "admin", service.getServiceName());
            return domains.getTenantDomainNames().stream()
                    .map(AthenzDomain::new)
                    .collect(toList());
        } catch (ZTSClientException e) {
            throw new ZtsException(e);
        }
    }

}
