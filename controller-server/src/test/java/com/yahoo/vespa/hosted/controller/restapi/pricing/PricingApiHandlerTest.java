// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.pricing;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerCloudTest;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author hmusum
 */
public class PricingApiHandlerTest extends ControllerContainerCloudTest {

    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/pricing/responses/";

    @Test
    void testPricingInfo() {
        ContainerTester tester = new ContainerTester(container, responseFiles);
        assertEquals(SystemName.Public, tester.controller().system());

        var request = request("/pricing/v1/pricing?" + urlEncodedPriceInformation());
        tester.assertResponse(request, """
                                      {"listPrice":"2400.00","volumeDiscount":"0.00"}""",
                              200);
    }

    /**
     * 2 clusters, with each having 1 node, with 1 vcpu, 1 Gb memory, 10 Gb disk and no GPU
     * price will be 20000 + 2000 + 200
     */
    String urlEncodedPriceInformation() {
        var parameters = "supportLevel=standard&committedSpend=0&enclave=false" +
                "&resources=nodes=1,vcpu=1,memoryGb=1,diskGb=10,gpuMemoryGb=0" +
                "&resources=nodes=1,vcpu=1,memoryGb=1,diskGb=10,gpuMemoryGb=0";

        return URLEncoder.encode(parameters, UTF_8);
    }

}
