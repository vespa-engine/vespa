// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice;

import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;

/**
 * @author bjorncs
 */
public class TestUtils {

    public static AthenzProviderServiceConfig getAthenzProviderConfig(String domain,
                                                                      String service,
                                                                      String dnsSuffix) {
        AthenzProviderServiceConfig.Builder zoneConfig =
                new AthenzProviderServiceConfig.Builder()
                        .serviceName(service)
                        .secretVersion(0)
                        .domain(domain)
                        .certDnsSuffix(dnsSuffix)
                        .ztsUrl("localhost/zts")
                        .secretName("s3cr3t")
                        .caCertSecretName(domain + ".ca.cert");
        return new AthenzProviderServiceConfig(
                zoneConfig.athenzCaTrustStore("/dummy/path/to/athenz-ca.jks"));
    }

}
