package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerCloudTest;
import com.yahoo.vespa.hosted.controller.security.CloudTenantSpec;
import com.yahoo.vespa.hosted.controller.security.Credentials;
import org.junit.Test;

import java.io.File;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.application.container.handler.Request.Method.PATCH;
import static com.yahoo.application.container.handler.Request.Method.POST;

public class CloudApplicationApiTest extends ControllerContainerCloudTest {

    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/application/responses/";

    private static final String publicKey = "-----BEGIN PUBLIC KEY-----\n" +
                                                 "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEuKVFA8dXk43kVfYKzkUqhEY2rDT9\n" +
                                                 "z/4jKSTHwbYR8wdsOSrJGVEUPbS2nguIJ64OJH7gFnxM6sxUVj+Nm2HlXw==\n" +
                                                 "-----END PUBLIC KEY-----\n";

    private static final String privateKey = "-----BEGIN EC PRIVATE KEY-----\n" +
                                                  "MHcCAQEEIJUmbIX8YFLHtpRgkwqDDE3igU9RG6JD9cYHWAZii9j7oAoGCCqGSM49\n" +
                                                  "AwEHoUQDQgAEuKVFA8dXk43kVfYKzkUqhEY2rDT9z/4jKSTHwbYR8wdsOSrJGVEU\n" +
                                                  "PbS2nguIJ64OJH7gFnxM6sxUVj+Nm2HlXw==\n" +
                                                  "-----END EC PRIVATE KEY-----\n";

    @Test
    public void testResponses() {
        ContainerTester tester = new ContainerTester(container, responseFiles);
        ApplicationId id = ApplicationId.from("my-tenant", "my-app", "default");
        Optional<Credentials> credentials = Optional.of(new Credentials(() -> "user"));

        // Create an application.
        tester.controller().tenants().create(new CloudTenantSpec(TenantName.from("my-tenant"), "token"), credentials.get());
        tester.controller().applications().createApplication(id, credentials);

        // PATCH in a pem deploy key.
        tester.assertResponse(request("/application/v4/tenant/my-tenant/application/my-app", PATCH)
                                      .roles(Set.of(Role.hostedOperator()))
                                      .data("{\"pemDeployKey\":\"" + publicKey + "\"}"),
                              "{\"message\":\"Set pem deploy key to " +
                              publicKey.replaceAll("\\n", "\\\\n") + "\"}");

        tester.assertResponse(request("/application/v4/tenant/my-tenant/application/my-application/instance/default/submit", POST)
                             .roles());
    }

}
