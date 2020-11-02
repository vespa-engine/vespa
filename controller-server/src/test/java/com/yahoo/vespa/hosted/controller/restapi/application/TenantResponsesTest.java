package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.OktaAccessToken;
import com.yahoo.vespa.athenz.api.OktaIdentityToken;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static com.yahoo.application.container.handler.Request.Method.GET;
import static com.yahoo.application.container.handler.Request.Method.POST;
import static com.yahoo.vespa.hosted.controller.restapi.application.ApplicationApiTest.createAthenzDomainWithAdmin;
import static com.yahoo.vespa.hosted.controller.restapi.application.RequestBuilder.request;

public class TenantResponsesTest extends ControllerContainerTest {

    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/application/responses/";
    private static final UserId USER_ID = new UserId("myuser");
    private static final AthenzDomain ATHENZ_TENANT_DOMAIN = new AthenzDomain("domain1");
    private static final OktaIdentityToken OKTA_IT = new OktaIdentityToken("okta-it");
    private static final OktaAccessToken OKTA_AT = new OktaAccessToken("okta-at");
    private ContainerTester tester;

    @Before
    public void before() {
        tester = new ContainerTester(container, responseFiles);
    }

    @Test
    public void getTenantInfo() {
        // Setup a tenant first
        createAthenzDomainWithAdmin(ATHENZ_TENANT_DOMAIN, USER_ID, tester);
        tester.assertResponse(request("/application/v4/tenant/tenant1", POST)
                        .userIdentity(USER_ID)
                        .data("{\"athensDomain\":\"domain1\", \"property\":\"property1\"}")
                        .oktaAccessToken(OKTA_AT).oktaIdentityToken(OKTA_IT),
                new File("tenant-without-applications.json"));

        // Assert that initially the tenant has no info
        tester.assertResponse(request("/application/v4/tenant/tenant1/info", GET).userIdentity(USER_ID),
                new File("root.json"));
    }
}
