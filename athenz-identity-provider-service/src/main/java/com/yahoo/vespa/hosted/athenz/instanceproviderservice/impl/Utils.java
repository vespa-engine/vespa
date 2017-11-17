// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;

/**
 * @author bjorncs
 */
public class Utils {

    private static final ObjectMapper mapper = createObjectMapper();

    public static ObjectMapper getMapper() {
        return mapper;
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    public static AthenzProviderServiceConfig.Zones getZoneConfig(AthenzProviderServiceConfig config, Zone zone) {
        String key = zone.environment().value() + "." + zone.region().value();
        return config.zones(key);
    }

}
