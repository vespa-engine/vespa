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
        var request = request("/pricing/v1/pricing?" + urlEncodedPriceInformation1App(BASIC));
        tester().assertJsonResponse(request, """
                                            {
                                              "applications": [
                                                {
                                                  "name": "app1",
                                                  "priceInfo": [
                                                    {"description": "Basic support unit price", "amount": "2240.00"},
                                                    {"description": "Volume discount", "amount": "-5.64"}
                                                  ]
                                                }
                                              ],
                                              "priceInfo": [
                                                {"description": "Committed spend", "amount": "-1.23"}
                                              ],
                                              "totalAmount": "2233.13"
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
                                                  "name": "app1",
                                                  "priceInfo": [
                                                    {"description": "Basic support unit price", "amount": "2240.00"},
                                                    {"description": "Enclave", "amount": "-15.12"},
                                                    {"description": "Volume discount", "amount": "-5.64"}
                                                  ]
                                                }
                                              ],
                                              "priceInfo": [
                                                {"description": "Committed spend", "amount": "-1.23"}
                                              ],
                                              "totalAmount": "2218.00"
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
                                                  "name": "app1",
                                                  "priceInfo": [
                                                    {"description": "Commercial support unit price", "amount": "3200.00"},
                                                    {"description": "Enclave", "amount": "-15.12"},
                                                    {"description": "Volume discount", "amount": "-5.64"}
                                                  ]
                                                }
                                              ],
                                              "priceInfo": [
                                                {"description": "Committed spend", "amount": "-1.23"}
                                              ],
                                              "totalAmount": "3178.00"
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
                                                  "name": "app1",
                                                  "priceInfo": [
                                                    {"description": "Commercial support unit price", "amount": "2000.00"},
                                                    {"description": "Enclave", "amount": "-15.12"},
                                                    {"description": "Volume discount", "amount": "-5.64"}
                                                  ]
                                                },
                                                {
                                                  "name": "app2",
                                                  "priceInfo": [
                                                    {"description": "Commercial support unit price", "amount": "2000.00"},
                                                    {"description": "Enclave", "amount": "-15.12"},
                                                    {"description": "Volume discount", "amount": "-5.64"}
                                                  ]
                                                }
                                              ],
                                              "priceInfo": [
                                                {"description": "Committed spend", "amount": "-1.23"}
                                              ],
                                              "totalAmount": "3957.24"
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
     * 1 node, with 1 vcpu, 1 Gb memory, 10 Gb disk and no GPU,
     * price will be 20000 + 2000 + 200
     */
    String urlEncodedPriceInformation1App(PricingInfo.SupportLevel supportLevel) {
        return "application=" + URLEncoder.encode("name=app1,vcpu=2,memoryGb=2,diskGb=20,gpuMemoryGb=0", UTF_8) +
                "&supportLevel=" + supportLevel.name().toLowerCase() + "&committedSpend=100";
    }

    /**
     * 1 app, with 2 clusters (with total resources for all clusters with each having
     * 1 node, with 1 vcpu, 1 Gb memory, 10 Gb disk and no GPU,
     * price will be 20000 + 2000 + 200
     */
    String urlEncodedPriceInformation1AppEnclave(PricingInfo.SupportLevel supportLevel) {
        return "application=" + URLEncoder.encode("name=app1,enclaveVcpu=2,enclaveMemoryGb=2,enclaveDiskGb=20,enclaveGpuMemoryGb=0", UTF_8) +
                "&supportLevel=" + supportLevel.name().toLowerCase() + "&committedSpend=100";
    }

    /**
     * 2 apps, with 1 cluster (with total resources for all clusters with each having
     * 1 node, with 1 vcpu, 1 Gb memory, 10 Gb disk and no GPU
     */
    String urlEncodedPriceInformation2AppsEnclave(PricingInfo.SupportLevel supportLevel) {
        return "application=" + URLEncoder.encode("name=app1,enclaveVcpu=1,enclaveMemoryGb=1,enclaveDiskGb=10,enclaveGpuMemoryGb=0", UTF_8) +
                "&application=" + URLEncoder.encode("name=app2,enclaveVcpu=1,enclaveMemoryGb=1,enclaveDiskGb=10,enclaveGpuMemoryGb=0", UTF_8) +
                "&supportLevel=" + supportLevel.name().toLowerCase() + "&committedSpend=0";
    }

}
