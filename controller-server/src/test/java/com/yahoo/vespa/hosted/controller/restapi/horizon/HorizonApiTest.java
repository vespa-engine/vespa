// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.horizon;

import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerCloudTest;
import org.junit.jupiter.api.Test;

import java.util.Set;

/**
 * @author olaa
 */
public class HorizonApiTest extends ControllerContainerCloudTest {

    @Test
    void only_operators_and_flag_enabled_tenants_allowed() {
        ContainerTester tester = new ContainerTester(container, "");
        TenantName tenantName = TenantName.defaultName();

        tester.assertResponse(request("/horizon/v1/config/dashboard/topFolders")
                        .roles(Set.of(Role.hostedOperator())),
                "", 200);

        tester.assertResponse(request("/horizon/v1/config/dashboard/topFolders")
                        .roles(Set.of(Role.reader(tenantName))),
                "{\"error-code\":\"FORBIDDEN\",\"message\":\"No tenant with enabled metrics view\"}", 403);

        ((InMemoryFlagSource) tester.controller().flagSource())
                .withBooleanFlag(Flags.ENABLED_HORIZON_DASHBOARD.id(), true);

        tester.assertResponse(request("/horizon/v1/config/dashboard/topFolders")
                        .roles(Set.of(Role.reader(tenantName))),
                "", 200);
    }

    @Override
    protected SystemName system() {
        return SystemName.PublicCd;
    }

    @Override
    protected String variablePartXml() {
        return "  <component id='com.yahoo.vespa.hosted.controller.security.CloudAccessControlRequests'/>\n" +
                "  <component id='com.yahoo.vespa.hosted.controller.security.CloudAccessControl'/>\n" +

                "  <handler id=\"com.yahoo.vespa.hosted.controller.restapi.horizon.HorizonApiHandler\" bundle=\"controller-server\">\n" +
                "       <binding>http://*/horizon/v1/*</binding>\n" +
                "  </handler>\n" +

                "  <http>\n" +
                "    <server id='default' port='8080' />\n" +
                "    <filtering>\n" +
                "      <request-chain id='default'>\n" +
                "        <filter id='com.yahoo.vespa.hosted.controller.restapi.filter.ControllerAuthorizationFilter'/>\n" +
                "        <binding>http://*/*</binding>\n" +
                "      </request-chain>\n" +
                "    </filtering>\n" +
                "  </http>\n";
    }
}
