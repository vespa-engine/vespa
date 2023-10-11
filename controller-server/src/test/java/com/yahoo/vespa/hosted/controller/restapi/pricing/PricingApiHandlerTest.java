// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.pricing;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.api.integration.pricing.PricingInfo;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerCloudTest;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;

import static com.yahoo.vespa.hosted.controller.api.integration.pricing.PricingInfo.SupportLevel.BASIC;
import static com.yahoo.vespa.hosted.controller.api.integration.pricing.PricingInfo.SupportLevel.COMMERCIAL;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author hmusum
 */
public class PricingApiHandlerTest extends ControllerContainerCloudTest {

    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/pricing/responses/";

    @Test
    void testPricingInfoBasic() {
        ContainerTester tester = new ContainerTester(container, responseFiles);
        assertEquals(SystemName.Public, tester.controller().system());

        var request = request("/pricing/v1/pricing?" + urlEncodedPriceInformation(BASIC, false));
        tester.assertJsonResponse(request, """
                                      {
                                        "priceInfo": [
                                          {"description": "List price", "amount": "2400.00"},
                                          {"description": "Basic support", "amount": "-160.00"},
                                          {"description": "Volume discount", "amount": "-5.64"}
                                        ],
                                        "totalAmount": "2234.36"
                                      }
                                      """,
                              200);
    }

    @Test
    void testPricingInfoCommercialEnclave() {
        ContainerTester tester = new ContainerTester(container, responseFiles);
        assertEquals(SystemName.Public, tester.controller().system());

        var request = request("/pricing/v1/pricing?" + urlEncodedPriceInformation(COMMERCIAL, true));
        tester.assertJsonResponse(request, """
                                      {
                                        "priceInfo": [
                                          {"description": "List price", "amount": "2400.00"},
                                          {"description": "Commercial support", "amount": "800.00"},
                                          {"description": "Enclave discount", "amount": "-15.12"},
                                          {"description": "Volume discount", "amount": "-5.64"}
                                        ],
                                        "totalAmount": "3179.23"
                                      }
                                      """,
                                  200);
    }

    @Test
    void testInvalidRequests() {
        ContainerTester tester = new ContainerTester(container, responseFiles);
        assertEquals(SystemName.Public, tester.controller().system());

        tester.assertJsonResponse(request("/pricing/v1/pricing"),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"No price information found in query\"}",
                400);
        tester.assertJsonResponse(request("/pricing/v1/pricing?"),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Error in query parameter, expected '=' between key and value: ''\"}",
                400);
        tester.assertJsonResponse(request("/pricing/v1/pricing?supportLevel=basic&committedSpend=0&enclave=false"),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"No cluster resources found in query\"}",
                400);
        tester.assertJsonResponse(request("/pricing/v1/pricing?supportLevel=basic&committedSpend=0&enclave=false&resources"),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Error in query parameter, expected '=' between key and value: 'resources'\"}",
                400);
        tester.assertJsonResponse(request("/pricing/v1/pricing?supportLevel=basic&committedSpend=0&enclave=false&resources="),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Error in query parameter, expected '=' between key and value: 'resources='\"}",
                400);
        tester.assertJsonResponse(request("/pricing/v1/pricing?supportLevel=basic&committedSpend=0&enclave=false&key=value"),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Unknown query parameter 'key'\"}",
                400);
        tester.assertJsonResponse(request("/pricing/v1/pricing?supportLevel=basic&committedSpend=0&enclave=false&resources=key%3Dvalue"),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Unknown resource type 'key'\"}",
                400);
    }

    /**
     * 2 clusters, with each having 1 node, with 1 vcpu, 1 Gb memory, 10 Gb disk and no GPU
     * price will be 20000 + 2000 + 200
     */
    String urlEncodedPriceInformation(PricingInfo.SupportLevel supportLevel, boolean enclave) {
        String resources = URLEncoder.encode("nodes=1,vcpu=1,memoryGb=1,diskGb=10,gpuMemoryGb=0", UTF_8);
        return "supportLevel=" + supportLevel.name().toLowerCase() + "&committedSpend=0&enclave=" + enclave +
                "&resources=" + resources +
                "&resources=" + resources;
    }
}
