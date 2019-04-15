package com.yahoo.vespa.hosted.controller.restapi.user;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerCloudTest;
import org.junit.Test;

import java.io.File;
import java.util.Set;

import static com.yahoo.application.container.handler.Request.Method.DELETE;
import static com.yahoo.application.container.handler.Request.Method.POST;
import static com.yahoo.application.container.handler.Request.Method.PUT;
import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 */
public class UserApiTest extends ControllerContainerCloudTest {

    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/user/responses/";

    @Test
    public void testUserManagement() {
        ContainerTester tester = new ContainerTester(container, responseFiles);
        assertEquals(SystemName.Public, tester.controller().system());
        Set<Role> operator = Set.of(Role.hostedOperator());
        ApplicationId id = ApplicationId.from("my-tenant", "my-app", "default");


        // GET at application/v4 root fails as it's not public read.
        tester.assertResponse(request("/application/v4/"),
                              accessDenied, 403);

        // GET at application/v4/tenant succeeds for operators.
        tester.assertResponse(request("/application/v4/tenant")
                                      .roles(operator),
                              "[]");

        // GET at application/v4/tenant is available also under the /api prefix.
        tester.assertResponse(request("/api/application/v4/tenant")
                                      .roles(operator),
                              "[]");

        // POST a tenant is not available to everyone.
        tester.assertResponse(request("/application/v4/tenant/my-tenant", POST)
                                      .data("{\"token\":\"hello\"}"),
                              accessDenied, 403);

        // POST a tenant is available to operators.
        tester.assertResponse(request("/application/v4/tenant/my-tenant", POST)
                                      .roles(operator)
                                      .user("owner@tenant")
                                      .data("{\"token\":\"hello\"}"),
                              new File("tenant-without-applications.json"));

        // PUT a tenant is not available to anyone.
        tester.assertResponse(request("/application/v4/user/", PUT)
                                      .roles(operator),
                              "{\"error-code\":\"FORBIDDEN\",\"message\":\"Not authenticated or not a user.\"}", 403);

        // GET at user/v1 root fails as no access control is defined there.
        tester.assertResponse(request("/user/v1/"),
                              accessDenied, 403);

        // POST a hosted operator role is not allowed.
        tester.assertResponse(request("/user/v1/tenant/my-tenant", POST)
                                      .roles(Set.of(Role.tenantOwner(id.tenant())))
                                      .data("{\"user\":\"evil@evil\",\"roleName\":\"hostedOperator\"}"),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Malformed or illegal role name 'hostedOperator'.\"}", 400);

        // POST a tenant operator is available to the tenant owner.
        tester.assertResponse(request("/user/v1/tenant/my-tenant", POST)
                                      .roles(Set.of(Role.tenantOwner(id.tenant())))
                                      .data("{\"user\":\"operator@tenant\",\"roleName\":\"tenantOperator\"}"),
                              "{\"message\":\"user 'operator@tenant' is now a member of role 'tenantOperator' of 'my-tenant'\"}");

        // POST a tenant admin is not available to a tenant operator.
        tester.assertResponse(request("/user/v1/tenant/my-tenant", POST)
                                      .roles(Set.of(Role.tenantOperator(id.tenant())))
                                      .data("{\"user\":\"admin@tenant\",\"roleName\":\"tenantAdmin\"}"),
                              accessDenied, 403);

        // POST an application admin for a non-existent application fails.
        tester.assertResponse(request("/user/v1/tenant/my-tenant/application/my-app", POST)
                                      .roles(Set.of(Role.tenantOwner(TenantName.from("my-tenant"))))
                                      .data("{\"user\":\"admin@app\",\"roleName\":\"applicationAdmin\"}"),
                              "{\"error-code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"NullPointerException\"}", 500);

        // POST an application is allowed for a tenant operator.
        tester.assertResponse(request("/application/v4/tenant/my-tenant/application/my-app", POST)
                                      .user("operator@tenant")
                                      .roles(Set.of(Role.tenantOperator(id.tenant()))),
                              new File("application-created.json"));

        // POST an application is not allowed under a different tenant.
        tester.assertResponse(request("/application/v4/tenant/other-tenant/application/my-app", POST)
                                      .roles(Set.of(Role.tenantOperator(id.tenant()))),
                              accessDenied, 403);

        // POST an application role is allowed for a tenant admin.
        tester.assertResponse(request("/user/v1/tenant/my-tenant/application/my-app", POST)
                                      .roles(Set.of(Role.tenantAdmin(id.tenant())))
                                      .data("{\"user\":\"reader@app\",\"roleName\":\"applicationReader\"}"),
                              "{\"message\":\"user 'reader@app' is now a member of role 'applicationReader' of 'my-app' owned by 'my-tenant'\"}");

        // POST a tenant role is not allowed to an application.
        tester.assertResponse(request("/user/v1/tenant/my-tenant/application/my-app", POST)
                                      .roles(Set.of(Role.hostedOperator()))
                                      .data("{\"user\":\"reader@app\",\"roleName\":\"tenantOperator\"}"),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Malformed or illegal role name 'tenantOperator'.\"}", 400);

        // GET tenant role information is available to application readers.
        tester.assertResponse(request("/user/v1/tenant/my-tenant")
                             .roles(Set.of(Role.applicationReader(id.tenant(), id.application()))),
                              new File("tenant-roles.json"));

        // GET application role information is available to tenant operators.
        tester.assertResponse(request("/user/v1/tenant/my-tenant/application/my-app")
                                      .roles(Set.of(Role.tenantOperator(id.tenant()))),
                              new File("application-roles.json"));

        // GET application role information is available also under the /api prefix.
        tester.assertResponse(request("/api/user/v1/tenant/my-tenant/application/my-app")
                                      .roles(Set.of(Role.tenantOperator(id.tenant()))),
                              new File("application-roles.json"));

        // DELETE an application role is allowed for an application admin.
        tester.assertResponse(request("/user/v1/tenant/my-tenant/application/my-app", DELETE)
                                      .roles(Set.of(Role.applicationAdmin(id.tenant(), id.application())))
                                      .data("{\"user\":\"operator@tenant\",\"roleName\":\"applicationAdmin\"}"),
                              "{\"message\":\"user 'operator@tenant' is no longer a member of role 'applicationAdmin' of 'my-app' owned by 'my-tenant'\"}");

        // DELETE an application is available to application admins.
        tester.assertResponse(request("/application/v4/tenant/my-tenant/application/my-app", DELETE)
                             .roles(Set.of(Role.applicationAdmin(id.tenant(), id.application()))),
                              "");

        // DELETE a tenant role is available to tenant admins.
        tester.assertResponse(request("/user/v1/tenant/my-tenant", DELETE)
                                      .roles(Set.of(Role.tenantAdmin(id.tenant())))
                                      .data("{\"user\":\"operator@tenant\",\"roleName\":\"tenantOperator\"}"),
                              "{\"message\":\"user 'operator@tenant' is no longer a member of role 'tenantOperator' of 'my-tenant'\"}");

        // DELETE the last tenant owner is not allowed.
        tester.assertResponse(request("/user/v1/tenant/my-tenant", DELETE)
                             .roles(operator)
                             .data("{\"user\":\"owner@tenant\",\"roleName\":\"tenantOwner\"}"),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Can't remove the last owner of a tenant.\"}", 400);

        // DELETE the tenant is available to the tenant owner.
        tester.assertResponse(request("/application/v4/tenant/my-tenant", DELETE)
                                      .roles(Set.of(Role.tenantOwner(id.tenant()))),
                              new File("tenant-without-applications.json"));
    }

}
