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

    @Test
    void testPricingInfoWithIncompleteParameter() {
        ContainerTester tester = new ContainerTester(container, responseFiles);
        assertEquals(SystemName.Public, tester.controller().system());

        var request = request("/pricing/v1/pricing?" + urlEncodedPriceInformationWithMissingValueInResourcs());
        tester.assertResponse(request,
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Error in query parameter, expected '=' between key and value: resources\"}",
                              400);
    }

    /**
     * 2 clusters, with each having 1 node, with 1 vcpu, 1 Gb memory, 10 Gb disk and no GPU
     * price will be 20000 + 2000 + 200
     */
    String urlEncodedPriceInformation() {
        String resources = URLEncoder.encode("nodes=1,vcpu=1,memoryGb=1,diskGb=10,gpuMemoryGb=0", UTF_8);
        return "supportLevel=basic&committedSpend=0&enclave=false" +
                "&resources=" + resources +
                "&resources=" + resources;
    }

    String urlEncodedPriceInformationWithMissingValueInResourcs() {
        return URLEncoder.encode("supportLevel=basic&committedSpend=0&enclave=false&resources", UTF_8);
    }

}
