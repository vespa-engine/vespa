// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice;

import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;

/**
 * @author bjorncs
 */
public class TestUtils {

    public static AthenzProviderServiceConfig getAthenzProviderConfig(String domain,
                                                                      String service,
                                                                      String dnsSuffix,
                                                                      Zone zone) {
        AthenzProviderServiceConfig.Builder zoneConfig =
                new AthenzProviderServiceConfig.Builder()
                        .serviceName(service)
                        .secretVersion(0)
                        .domain(domain)
                        .certDnsSuffix(dnsSuffix)
                        .ztsUrl("localhost/zts")
                        .secretName("s3cr3t");
        return new AthenzProviderServiceConfig(
                new AthenzProviderServiceConfig.Builder()
                        .athenzCaTrustStore("/dummy/path/to/athenz-ca.jks"));
    }

}
