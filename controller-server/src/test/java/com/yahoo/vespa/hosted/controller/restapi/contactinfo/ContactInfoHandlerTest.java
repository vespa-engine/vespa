package com.yahoo.vespa.hosted.controller.restapi.contactinfo;

import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;
import com.yahoo.vespa.hosted.controller.restapi.ContainerControllerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ContactInfoHandlerTest extends ControllerContainerTest {

    private ContainerControllerTester tester;

    @Before
    public void before() {
        tester = new ContainerControllerTester(container, null);
    }

    @Test
    public void testGettingAndFeedingContactInfo() {
        tester.createApplication();

        // No contact information available yet
        String notFoundMessage = "{\"error-code\":\"NOT_FOUND\",\"message\":\"Could not find contact info for tenant1\"}";
        assertResponse(new Request("http://localhost:8080/contactinfo/v1/tenant/tenant1"), 404, notFoundMessage);

        // Feed contact information for tenant1
        String contactInfo = "{\"url\":\"https://url:4444/\",\"issueTrackerUrl\":\"https://issueTrackerUrl:4444/\",\"propertyUrl\":\"https://propertyUrl:4444/\",\"persons\":[[\"foo\",\"bar\"]],\"queue\":\"queue\",\"component\":\"component\"}";
        String expectedResponseMessage = "Added contact info for tenant1 - Contact{url=https://url:4444/, propertyUrl=https://propertyUrl:4444/, issueTrackerUrl=https://issueTrackerUrl:4444/, persons=[[foo, bar]], queue=queue, component=component}";
        assertResponse(new Request("http://localhost:8080/contactinfo/v1/tenant/tenant1", contactInfo, Request.Method.POST), 200, expectedResponseMessage);

        // Get contact information for tenant1
        Response response = container.handleRequest(new Request("http://localhost:8080/contactinfo/v1/tenant/tenant1"));
        String actualContactInfo = new String(response.getBody());
        assertEquals(contactInfo, actualContactInfo);
    }

}