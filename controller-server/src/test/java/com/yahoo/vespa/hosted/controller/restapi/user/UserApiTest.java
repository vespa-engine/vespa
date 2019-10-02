package com.yahoo.vespa.hosted.controller.restapi.user;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerCloudTest;
import org.junit.Test;

import java.io.File;
import java.util.Set;

import static com.yahoo.application.container.handler.Request.Method.DELETE;
import static com.yahoo.application.container.handler.Request.Method.PATCH;
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
                        .user("administrator@tenant")
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
                        .roles(Set.of(Role.administrator(id.tenant())))
                        .data("{\"user\":\"evil@evil\",\"roleName\":\"hostedOperator\"}"),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Malformed or illegal role name 'hostedOperator'.\"}", 400);

        // POST an administrator to the tenant.
        tester.assertResponse(request("/user/v1/tenant/my-tenant", POST)
                        .roles(Set.of(Role.administrator(id.tenant())))
                        .data("{\"user\":\"administrator@tenant\",\"roleName\":\"administrator\"}"),
                "{\"message\":\"user 'administrator@tenant' is now a member of role 'administrator' of 'my-tenant'\"}");

        // POST a developer to the tenant.
        tester.assertResponse(request("/user/v1/tenant/my-tenant", POST)
                        .roles(Set.of(Role.administrator(id.tenant())))
                        .data("{\"user\":\"developer@tenant\",\"roleName\":\"developer\"}"),
                "{\"message\":\"user 'developer@tenant' is now a member of role 'developer' of 'my-tenant'\"}");

        // POST a reader to the tenant.
        tester.assertResponse(request("/user/v1/tenant/my-tenant", POST)
                        .roles(Set.of(Role.administrator(id.tenant())))
                        .data("{\"user\":\"reader@tenant\",\"roleName\":\"reader\"}"),
                "{\"message\":\"user 'reader@tenant' is now a member of role 'reader' of 'my-tenant'\"}");

        // POST an application is allowed for a tenant developer.
        tester.assertResponse(request("/application/v4/tenant/my-tenant/application/my-app", POST)
                        .user("developer@tenant")
                        .roles(Set.of(Role.developer(id.tenant()))),
                new File("application-created.json"));

        // GET tenant role information is available to application readers.
        tester.assertResponse(request("/user/v1/tenant/my-tenant")
                        .roles(Set.of(Role.reader(id.tenant()))),
                new File("tenant-roles.json"));

        // GET application role information is available to tenant operators.
        tester.assertResponse(request("/user/v1/tenant/my-tenant/application/my-app")
                        .roles(Set.of(Role.developer(id.tenant()))),
                new File("application-roles.json"));

        // GET application role information is available also under the /api prefix.
        tester.assertResponse(request("/api/user/v1/tenant/my-tenant/application/my-app")
                        .roles(Set.of(Role.developer(id.tenant()))),
                new File("application-roles.json"));

        // POST a pem deploy key
        tester.assertResponse(request("/application/v4/tenant/my-tenant/application/my-app/key", POST)
                                      .roles(Set.of(Role.developer(id.tenant())))
                                      .data("{\"key\":\"-----BEGIN PUBLIC KEY-----\n∠( ᐛ 」∠)＿\n-----END PUBLIC KEY-----\"}"),
                              "{\"message\":\"Added deploy key -----BEGIN PUBLIC KEY-----\\n∠( ᐛ 」∠)＿\\n-----END PUBLIC KEY-----\"}");

        // POST a pem developer key
        tester.assertResponse(request("/application/v4/tenant/my-tenant/key", POST)
                                      .user("joe@dev")
                                      .roles(Set.of(Role.developer(id.tenant())))
                                      .data("{\"key\":\"-----BEGIN PUBLIC KEY-----\n∠( ᐛ 」∠)＿\n-----END PUBLIC KEY-----\"}"),
                              "{\"message\":\"Set developer key -----BEGIN PUBLIC KEY-----\\n∠( ᐛ 」∠)＿\\n-----END PUBLIC KEY----- for joe@dev\"}");

        // POST the same pem developer key for a different user is forbidden
        tester.assertResponse(request("/application/v4/tenant/my-tenant/key", POST)
                                      .user("operator@tenant")
                                      .roles(Set.of(Role.developer(id.tenant())))
                                      .data("{\"key\":\"-----BEGIN PUBLIC KEY-----\n∠( ᐛ 」∠)＿\n-----END PUBLIC KEY-----\"}"),
                              "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Multiple entries with same key: -----BEGIN PUBLIC KEY-----\\n∠( ᐛ 」∠)＿\\n-----END PUBLIC KEY-----=operator@tenant and -----BEGIN PUBLIC KEY-----\\n∠( ᐛ 」∠)＿\\n-----END PUBLIC KEY-----=joe@dev\"}",
                              400);

        // PATCH in a different pem developer key
        tester.assertResponse(request("/application/v4/tenant/my-tenant/key", POST)
                                      .user("operator@tenant")
                                      .roles(Set.of(Role.developer(id.tenant())))
                                      .data("{\"key\":\"-----BEGIN PUBLIC KEY-----\nƪ(`▿▿▿▿´ƪ)\n-----END PUBLIC KEY-----\"}"),
                              "{\"message\":\"Set developer key -----BEGIN PUBLIC KEY-----\\nƪ(`▿▿▿▿´ƪ)\\n-----END PUBLIC KEY----- for operator@tenant\"}");

        // GET tenant information with keys
        tester.assertResponse(request("/application/v4/tenant/my-tenant/")
                                      .roles(Set.of(Role.reader(id.tenant()))),
                              new File("tenant-with-keys.json"));

        // DELETE a pem developer key
        tester.assertResponse(request("/application/v4/tenant/my-tenant/key", DELETE)
                                      .roles(Set.of(Role.developer(id.tenant())))
                                      .data("{\"key\":\"-----BEGIN PUBLIC KEY-----\\n∠( ᐛ 」∠)＿\\n-----END PUBLIC KEY-----\"}"),
                              "{\"message\":\"Removed developer key -----BEGIN PUBLIC KEY-----\\n∠( ᐛ 」∠)＿\\n-----END PUBLIC KEY----- for joe@dev\"}");

        // DELETE a tenant role is available to tenant admins.
        tester.assertResponse(request("/user/v1/tenant/my-tenant", DELETE)
                        .roles(Set.of(Role.administrator(id.tenant())))
                        .data("{\"user\":\"developer@tenant\",\"roleName\":\"developer\"}"),
                "{\"message\":\"user 'developer@tenant' is no longer a member of role 'developer' of 'my-tenant'\"}");

        // DELETE the last tenant administrator is not allowed.
        tester.assertResponse(request("/user/v1/tenant/my-tenant", DELETE)
                        .roles(operator)
                        .data("{\"user\":\"administrator@tenant\",\"roleName\":\"administrator\"}"),
                "{\"error-code\":\"BAD_REQUEST\",\"message\":\"Can't remove the last administrator of a tenant.\"}", 400);

        // DELETE the tenant is not available.
        tester.assertResponse(request("/application/v4/tenant/my-tenant", DELETE)
                        .roles(Set.of(Role.administrator(id.tenant()))),
                accessDenied, 403);
    }

}
