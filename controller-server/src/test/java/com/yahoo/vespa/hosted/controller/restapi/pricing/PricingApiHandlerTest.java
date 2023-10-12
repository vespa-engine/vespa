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
    void testPricingInfoBasicLegacy() {
        ContainerTester tester = new ContainerTester(container, responseFiles);
        assertEquals(SystemName.Public, tester.controller().system());

        var request = request("/pricing/v1/pricing?" + urlEncodedPriceInformationLegacy(BASIC, false));
        tester.assertJsonResponse(request, """
                                      {
                                        "priceInfo": [
                                          {"description": "Basic support unit price", "amount": "2240.00"},
                                          {"description": "Volume discount", "amount": "-5.64"},
                                          {"description": "Committed spend", "amount": "-1.23"}
                                        ],
                                        "totalAmount": "2233.13"
                                      }
                                      """,
                              200);
    }

    @Test
    void testPricingInfoBasic() {
        ContainerTester tester = new ContainerTester(container, responseFiles);
        assertEquals(SystemName.Public, tester.controller().system());

        var request = request("/pricing/v1/pricing?" + urlEncodedPriceInformation1App(BASIC));
        tester.assertJsonResponse(request, """
                                      {
                                        "priceInfo": [
                                          {"description": "Basic support unit price", "amount": "2240.00"},
                                          {"description": "Volume discount", "amount": "-5.64"},
                                          {"description": "Committed spend", "amount": "-1.23"}
                                        ],
                                        "totalAmount": "2233.13"
                                      }
                                      """,
                                  200);
    }

    @Test
    void testPricingInfoBasicEnclave() {
        ContainerTester tester = new ContainerTester(container, responseFiles);
        assertEquals(SystemName.Public, tester.controller().system());

        var request = request("/pricing/v1/pricing?" + urlEncodedPriceInformation1AppEnclave(BASIC));
        tester.assertJsonResponse(request, """
                                      {
                                        "priceInfo": [
                                          {"description": "Basic support unit price", "amount": "2240.00"},
                                          {"description": "Enclave", "amount": "-15.12"},
                                          {"description": "Volume discount", "amount": "-5.64"},
                                          {"description": "Committed spend", "amount": "-1.23"}
                                        ],
                                        "totalAmount": "2218.00"
                                      }
                                      """,
                                  200);
    }

    @Test
    void testPricingInfoCommercialEnclaveLegacy() {
        ContainerTester tester = new ContainerTester(container, responseFiles);
        assertEquals(SystemName.Public, tester.controller().system());

        var request = request("/pricing/v1/pricing?" + urlEncodedPriceInformationLegacy(COMMERCIAL, true));
        tester.assertJsonResponse(request, """
                                      {
                                        "priceInfo": [
                                          {"description": "Commercial support unit price", "amount": "3200.00"},
                                          {"description": "Enclave", "amount": "-15.12"},
                                          {"description": "Volume discount", "amount": "-5.64"},
                                          {"description": "Committed spend", "amount": "-1.23"}
                                        ],
                                        "totalAmount": "3178.00"
                                      }
                                      """,
                                  200);
    }

    @Test
    void testPricingInfoCommercialEnclave() {
        ContainerTester tester = new ContainerTester(container, responseFiles);
        assertEquals(SystemName.Public, tester.controller().system());

        var request = request("/pricing/v1/pricing?" + urlEncodedPriceInformation1AppEnclave(COMMERCIAL));
        tester.assertJsonResponse(request, """
                                      {
                                        "priceInfo": [
                                          {"description": "Commercial support unit price", "amount": "3200.00"},
                                          {"description": "Enclave", "amount": "-15.12"},
                                          {"description": "Volume discount", "amount": "-5.64"},
                                          {"description": "Committed spend", "amount": "-1.23"}
                                        ],
                                        "totalAmount": "3178.00"
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
        tester.assertJsonResponse(request("/pricing/v1/pricing?supportLevel=basic&committedSpend=0"),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"No application resources found in query\"}",
                400);
        tester.assertJsonResponse(request("/pricing/v1/pricing?supportLevel=basic&committedSpend=0&resources"),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Error in query parameter, expected '=' between key and value: 'resources'\"}",
                400);
        tester.assertJsonResponse(request("/pricing/v1/pricing?supportLevel=basic&committedSpend=0&resources="),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Error in query parameter, expected '=' between key and value: 'resources='\"}",
                400);
        tester.assertJsonResponse(request("/pricing/v1/pricing?supportLevel=basic&committedSpend=0&key=value"),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Unknown query parameter 'key'\"}",
                400);
        tester.assertJsonResponse(request("/pricing/v1/pricing?supportLevel=basic&committedSpend=0&resources=key%3Dvalue"),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Unknown resource type 'key'\"}",
                400);
    }

    /**
     * 2 clusters, with each having 1 node, with 1 vcpu, 1 Gb memory, 10 Gb disk and no GPU
     * price will be 20000 + 2000 + 200
     */
    String urlEncodedPriceInformationLegacy(PricingInfo.SupportLevel supportLevel, boolean enclave) {
        String resources = URLEncoder.encode("nodes=1,vcpu=1,memoryGb=1,diskGb=10,gpuMemoryGb=0", UTF_8);
        return "supportLevel=" + supportLevel.name().toLowerCase() + "&committedSpend=100&enclave=" + enclave +
                "&resources=" + resources +
                "&resources=" + resources;
    }

    /**
     * 1 app, with 2 clusters (with total resources for all clusters with each having
     * 1 node, with 1 vcpu, 1 Gb memory, 10 Gb disk and no GPU,
     * price will be 20000 + 2000 + 200
     */
    String urlEncodedPriceInformation1App(PricingInfo.SupportLevel supportLevel) {
        return "application=" + URLEncoder.encode("name=myapp,vcpu=2,memoryGb=2,diskGb=20,gpuMemoryGb=0", UTF_8) +
                "&supportLevel=" + supportLevel.name().toLowerCase() + "&committedSpend=100";
    }

    /**
     * 1 app, with 2 clusters (with total resources for all clusters with each having
     * 1 node, with 1 vcpu, 1 Gb memory, 10 Gb disk and no GPU,
     * price will be 20000 + 2000 + 200
     */
    String urlEncodedPriceInformation1AppEnclave(PricingInfo.SupportLevel supportLevel) {
        return "application=" + URLEncoder.encode("name=myapp,enclaveVcpu=2,enclaveMemoryGb=2,enclaveDiskGb=20,enclaveGpuMemoryGb=0", UTF_8) +
                "&supportLevel=" + supportLevel.name().toLowerCase() + "&committedSpend=100";
    }

}
