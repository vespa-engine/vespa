// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.hostname.config.ApihostnamesConfig;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Contact;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author mpolden
 */
public class ContactInformationMaintainerTest {

    private ControllerTester tester;
    private ContactInformationMaintainer maintainer;
    private String contactInfoPath = "/contactinfo/v1/tenant/tenant1";
    private String tenantPath = "/application/v4/tenant/";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(4443);


    @Before
    public void before() {
        tester = new ControllerTester();
        ApihostnamesConfig.Builder apihostnamesConfigBuilder = new ApihostnamesConfig.Builder().hostnames(Arrays.asList("http://localhost:4443/"));
        ApihostnamesConfig apihostnamesConfig = new ApihostnamesConfig(apihostnamesConfigBuilder);
        maintainer = new ContactInformationMaintainer(tester.controller(), Duration.ofDays(1),
                                                      new JobControl(tester.controller().curator()),
                                                      tester.organization(), apihostnamesConfig);
        wireMockRule.stubFor(post(urlEqualTo(contactInfoPath))
                .willReturn(aResponse().withStatus(200)));
        wireMockRule.stubFor(get(urlEqualTo(tenantPath))
                .willReturn(okJson("[{\"tenant\":\"tenant1\"}]")));
        wireMockRule.stubFor(get(urlEqualTo(tenantPath + "tenant1"))
                .willReturn(okJson("{\"tenant\":\"tenant1\", \"athensDomain\":\"domain\", \"property\":\"property\", \"propertyId\":\"1\"}")));
    }

    @Test
    public void updates_contact_information() {
        long propertyId = 1;
        TenantName name = tester.createTenant("tenant1", "domain1", propertyId);
        Supplier<AthenzTenant> tenant = () -> tester.controller().tenants().requireAthenzTenant(name);
        assertFalse("No contact information initially", tenant.get().contact().isPresent());
        Contact contact = testContact();
        registerContact(propertyId, contact);
        maintainer.run();
        verify(1, postRequestedFor(urlEqualTo(contactInfoPath)));
        LoggedRequest request = findAll(postRequestedFor(urlEqualTo(contactInfoPath))).get(0);
        String expectedBody = "{\"url\":\"http://contact1.test\",\"issueTrackerUrl\":\"http://issue-tracker1.test\",\"propertyUrl\":\"http://property1.test\",\"persons\":[[\"alice\"],[\"bob\"]]}";
        assertEquals(expectedBody, new String(request.getBody()));
    }

    private void registerContact(long propertyId, Contact contact) {
        PropertyId p = new PropertyId(String.valueOf(propertyId));
        tester.organization().addProperty(p)
              .setContactsUrl(p, contact.url())
              .setIssueUrl(p, contact.issueTrackerUrl())
              .setPropertyUrl(p, contact.propertyUrl())
              .setContactsFor(p, contact.persons().stream().map(persons -> persons.stream()
                                                                                  .map(User::from)
                                                                                  .collect(Collectors.toList()))
                                        .collect(Collectors.toList()));
    }

    private static Contact testContact() {
        URI contactUrl = URI.create("http://contact1.test");
        URI issueTrackerUrl = URI.create("http://issue-tracker1.test");
        URI propertyUrl = URI.create("http://property1.test");
        List<List<String>> persons = Arrays.asList(Collections.singletonList("alice"),
                                                   Collections.singletonList("bob"));
        return new Contact(contactUrl, propertyUrl, issueTrackerUrl, persons);
    }

}
