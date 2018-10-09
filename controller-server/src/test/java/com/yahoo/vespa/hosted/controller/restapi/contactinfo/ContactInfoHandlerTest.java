package com.yahoo.vespa.hosted.controller.restapi.contactinfo;

import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.restapi.ContainerControllerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import com.yahoo.vespa.hosted.controller.tenant.Contact;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.Arrays;

import static org.junit.Assert.*;

public class ContactInfoHandlerTest extends ControllerContainerTest {

    private ContainerControllerTester tester;

    @Before
    public void before() {
        tester = new ContainerControllerTester(container, null);
    }

    @Test
    public void testGettingAndFeedingContactInfo() throws Exception {
        tester.createApplication();

        // No contact information available yet
        String notFoundMessage = "{\"error-code\":\"NOT_FOUND\",\"message\":\"Could not find contact info for tenant1\"}";
        assertResponse(new Request("https://localhost:8080/contactinfo/v1/tenant/tenant1"), 404, notFoundMessage);

        // Feed contact information for tenant1
        Contact contact = new Contact(URI.create("https://localhost:4444/"), URI.create("https://localhost:4444/"), URI.create("https://localhost:4444/"), Arrays.asList(Arrays.asList("foo", "bar")));
        Slime contactSlime = ContactInfoHandler.contactToSlime(contact);
        byte[] body = SlimeUtils.toJsonBytes(contactSlime);
        String expectedResponseMessage = "Added contact info for tenant1 - Contact{url=https://localhost:4444/, propertyUrl=https://localhost:4444/, issueTrackerUrl=https://localhost:4444/, persons=[[foo, bar]]}";
        assertResponse(new Request("https://localhost:8080/contactinfo/v1/tenant/tenant1", body, Request.Method.POST), 200, expectedResponseMessage);

        // Get contact information for tenant1
        Response response = container.handleRequest(new Request("https://localhost:8080/contactinfo/v1/tenant/tenant1"));
        Contact actualContact = ContactInfoHandler.contactFromSlime(SlimeUtils.jsonToSlime(response.getBody()));
        assertEquals(contact, actualContact);
    }

}