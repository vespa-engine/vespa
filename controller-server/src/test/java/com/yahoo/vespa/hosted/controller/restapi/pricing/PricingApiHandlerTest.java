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

    @Test
    void testPricingInfoBasic() {
        tester().assertJsonResponse(request("/pricing/v1/pricing?supportLevel=basic&committedSpend=0"),
                """
                { "applications": [ ], "priceInfo": [ ], "totalAmount": "0.00" }
                """,
                200);

        var request = request("/pricing/v1/pricing?" + urlEncodedPriceInformation1App(BASIC));
        tester().assertJsonResponse(request, """
                                            {
                                              "applications": [
                                                {
                                                  "priceInfo": [
                                                    {"description": "Basic support unit price", "amount": "4.30"},
                                                    {"description": "Volume discount", "amount": "-0.10"}
                                                  ]
                                                }
                                              ],
                                              "priceInfo": [
                                                {"description": "Committed spend", "amount": "-0.20"}
                                              ],
                                              "totalAmount": "4.00"
                                            }
                                            """,
                                    200);
    }

    @Test
    void testPricingInfoBasicEnclave() {
        var request = request("/pricing/v1/pricing?" + urlEncodedPriceInformation1AppEnclave(BASIC));
        tester().assertJsonResponse(request, """
                                            {
                                              "applications": [
                                                {
                                                  "priceInfo": [
                                                    {"description": "Basic support unit price", "amount": "4.30"},
                                                    {"description": "Enclave", "amount": "-0.15"},
                                                    {"description": "Volume discount", "amount": "-0.10"}
                                                  ]
                                                }
                                              ],
                                              "priceInfo": [
                                                {"description": "Enclave (minimum $10k per month)", "amount": "10.15"},
                                                {"description": "Committed spend", "amount": "-0.20"}
                                              ],
                                              "totalAmount": "3.85"
                                            }
                                            """,
                                    200);
    }

    @Test
    void testPricingInfoCommercialEnclave() {
        var request = request("/pricing/v1/pricing?" + urlEncodedPriceInformation1AppEnclave(COMMERCIAL));
        tester().assertJsonResponse(request, """
                                            {
                                              "applications": [
                                                {
                                                  "priceInfo": [
                                                    {"description": "Commercial support unit price", "amount": "13.30"},
                                                    {"description": "Enclave", "amount": "-0.15"},
                                                    {"description": "Volume discount", "amount": "-0.10"}
                                                  ]
                                                }
                                              ],
                                              "priceInfo": [
                                                {"description": "Enclave (minimum $10k per month)", "amount": "1.15"},
                                                {"description": "Committed spend", "amount": "-0.20"}
                                              ],
                                              "totalAmount": "12.85"
                                            }
                                            """,
                                    200);
    }

    @Test
    void testPricingInfoCommercialEnclave2Apps() {
        var request = request("/pricing/v1/pricing?" + urlEncodedPriceInformation2AppsEnclave(COMMERCIAL));
        tester().assertJsonResponse(request, """
                                            {
                                              "applications": [
                                                {
                                                  "priceInfo": [
                                                    {"description": "Commercial support unit price", "amount": "13.30"},
                                                    {"description": "Enclave", "amount": "-0.15"},
                                                    {"description": "Volume discount", "amount": "-0.10"}
                                                  ]
                                                },
                                                {
                                                  "priceInfo": [
                                                    {"description": "Commercial support unit price", "amount": "13.30"},
                                                    {"description": "Enclave", "amount": "-0.15"},
                                                    {"description": "Volume discount", "amount": "-0.10"}
                                                  ]
                                                }
                                              ],
                                              "priceInfo": [ ],
                                              "totalAmount": "26.10"
                                            }
                                            """,
                                    200);
    }

    @Test
    void testInvalidRequests() {
        ContainerTester tester = tester();
        tester.assertJsonResponse(request("/pricing/v1/pricing"),
                                  "{\"error-code\":\"BAD_REQUEST\",\"message\":\"No price information found in query\"}",
                                  400);
        tester.assertJsonResponse(request("/pricing/v1/pricing?"),
                                  "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Error in query parameter, expected '=' between key and value: ''\"}",
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
        tester.assertJsonResponse(request("/pricing/v1/pricing?supportLevel=basic&committedSpend=0&application=key%3Dvalue"),
                                  "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Unknown key 'key'\"}",
                                  400);
    }

    private ContainerTester tester() {
        ContainerTester tester = new ContainerTester(container, null);
        assertEquals(SystemName.Public, tester.controller().system());
        return tester;
    }

    /**
     * 1 app, with 2 clusters (with total resources for all clusters with each having
     * 1 node, with 4 vcpu, 8 Gb memory, 100 Gb disk and no GPU,
     * price will be 20000 + 2000 + 200
     */
    String urlEncodedPriceInformation1App(PricingInfo.SupportLevel supportLevel) {
        return "application=" + URLEncoder.encode("vcpu=4,memoryGb=8,diskGb=100,gpuMemoryGb=0", UTF_8) +
                "&supportLevel=" + supportLevel.name().toLowerCase() + "&committedSpend=20";
    }

    /**
     * 1 app, with 2 clusters (with total resources for all clusters with each having
     * 1 node, with 4 vcpu, 8 Gb memory, 100 Gb disk and no GPU,
     * price will be 20000 + 2000 + 200
     */
    String urlEncodedPriceInformation1AppEnclave(PricingInfo.SupportLevel supportLevel) {
        return "application=" + URLEncoder.encode("enclaveVcpu=4,enclaveMemoryGb=8,enclaveDiskGb=100,enclaveGpuMemoryGb=0", UTF_8) +
                "&supportLevel=" + supportLevel.name().toLowerCase() + "&committedSpend=20";
    }

    /**
     * 2 apps, with 1 cluster (with total resources for all clusters with each having
     * 1 node, with 4 vcpu, 8 Gb memory, 100 Gb disk and no GPU,
     */
    String urlEncodedPriceInformation2AppsEnclave(PricingInfo.SupportLevel supportLevel) {
        return "application=" + URLEncoder.encode("enclaveVcpu=4,enclaveMemoryGb=8,enclaveDiskGb=100,enclaveGpuMemoryGb=0", UTF_8) +
                "&application=" + URLEncoder.encode("enclaveVcpu=4,enclaveMemoryGb=8,enclaveDiskGb=100,enclaveGpuMemoryGb=0", UTF_8) +
                "&supportLevel=" + supportLevel.name().toLowerCase() + "&committedSpend=0";
    }

}
