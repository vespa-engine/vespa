// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.filter;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.identifiers.ScrewdriverId;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ApplicationAction;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactoryMock;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzDbMock;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.athenz.HostedAthenzIdentities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author jonmv
 */
public class AthenzRoleFilterTest {

    private static final AthenzPrincipal USER = new AthenzPrincipal(new AthenzUser("john"));
    private static final AthenzPrincipal HOSTED_OPERATOR = new AthenzPrincipal(new AthenzUser("hosted-operator"));
    private static final AthenzDomain TENANT_DOMAIN = new AthenzDomain("tenantdomain");
    private static final AthenzDomain TENANT_DOMAIN2 = new AthenzDomain("tenantdomain2");
    private static final AthenzPrincipal TENANT_ADMIN = new AthenzPrincipal(new AthenzService(TENANT_DOMAIN, "adminservice"));
    private static final AthenzPrincipal TENANT_PIPELINE = new AthenzPrincipal(HostedAthenzIdentities.from(new ScrewdriverId("12345")));
    private static final AthenzPrincipal TENANT_ADMIN_AND_PIPELINE = new AthenzPrincipal(HostedAthenzIdentities.from(new ScrewdriverId("56789")));
    private static final TenantName TENANT = TenantName.from("mytenant");
    private static final TenantName TENANT2 = TenantName.from("othertenant");
    private static final ApplicationName APPLICATION = ApplicationName.from("myapp");
    private static final URI NO_CONTEXT_PATH = URI.create("/application/v4/");
    private static final URI TENANT_CONTEXT_PATH = URI.create("/application/v4/tenant/mytenant/");
    private static final URI APPLICATION_CONTEXT_PATH = URI.create("/application/v4/tenant/mytenant/application/myapp/");
    private static final URI TENANT2_CONTEXT_PATH = URI.create("/application/v4/tenant/othertenant/");
    private static final URI APPLICATION2_CONTEXT_PATH = URI.create("/application/v4/tenant/othertenant/application/myapp/");
    private static final URI INSTANCE_CONTEXT_PATH = URI.create("/application/v4/tenant/mytenant/application/myapp/instance/john");
    private static final URI INSTANCE2_CONTEXT_PATH = URI.create("/application/v4/tenant/mytenant/application/myapp/instance/jane");

    private AthenzRoleFilter filter;

    @BeforeEach
    public void setup() {
        ControllerTester tester = new ControllerTester();
        filter = new AthenzRoleFilter(new AthenzClientFactoryMock(tester.athenzDb()),
                                      tester.controller());

        tester.athenzDb().hostedOperators.add(HOSTED_OPERATOR.getIdentity());
        tester.createTenant(TENANT.value(), TENANT_DOMAIN.getName(), null);
        tester.createApplication(TENANT.value(), APPLICATION.value(), "default");
        AthenzDbMock.Domain tenantDomain = tester.athenzDb().domains.get(TENANT_DOMAIN);
        tenantDomain.admins.add(TENANT_ADMIN.getIdentity());
        tenantDomain.admins.add(TENANT_ADMIN_AND_PIPELINE.getIdentity());
        tenantDomain.applications.get(new ApplicationId(APPLICATION.value())).addRoleMember(ApplicationAction.deploy, TENANT_PIPELINE.getIdentity());
        tenantDomain.applications.get(new ApplicationId(APPLICATION.value())).addRoleMember(ApplicationAction.deploy, TENANT_ADMIN_AND_PIPELINE.getIdentity());
        tester.createTenant(TENANT2.value(), TENANT_DOMAIN2.getName(), null);
        tester.createApplication(TENANT2.value(), APPLICATION.value(), "default");
    }

    @Test
    void testTranslations() throws Exception {

        // Hosted operators are always members of the hostedOperator role.
        assertEquals(Set.of(Role.hostedOperator(), Role.systemFlagsDeployer(), Role.systemFlagsDryrunner(), Role.paymentProcessor(), Role.hostedAccountant(), Role.hostedSupporter()),
                filter.roles(HOSTED_OPERATOR, NO_CONTEXT_PATH));

        assertEquals(Set.of(Role.hostedOperator(), Role.systemFlagsDeployer(), Role.systemFlagsDryrunner(), Role.paymentProcessor(), Role.hostedAccountant(), Role.hostedSupporter()),
                filter.roles(HOSTED_OPERATOR, TENANT_CONTEXT_PATH));

        assertEquals(Set.of(Role.hostedOperator(), Role.systemFlagsDeployer(), Role.systemFlagsDryrunner(), Role.paymentProcessor(), Role.hostedAccountant(), Role.hostedSupporter()),
                filter.roles(HOSTED_OPERATOR, APPLICATION_CONTEXT_PATH));

        // Tenant admins are members of the athenzTenantAdmin role within their tenant subtree.
        assertEquals(Set.of(Role.everyone()),
                filter.roles(TENANT_PIPELINE, NO_CONTEXT_PATH));

        assertEquals(Set.of(Role.athenzTenantAdmin(TENANT)),
                filter.roles(TENANT_ADMIN, TENANT_CONTEXT_PATH));

        assertEquals(Set.of(Role.athenzTenantAdmin(TENANT)),
                filter.roles(TENANT_ADMIN, APPLICATION_CONTEXT_PATH));

        assertEquals(Set.of(Role.athenzTenantAdmin(TENANT)),
                filter.roles(TENANT_ADMIN, TENANT2_CONTEXT_PATH));

        assertEquals(Set.of(Role.athenzTenantAdmin(TENANT)),
                filter.roles(TENANT_ADMIN, APPLICATION2_CONTEXT_PATH));

        // Build services are members of the buildService role within their application subtree.
        assertEquals(Set.of(Role.everyone()),
                filter.roles(TENANT_PIPELINE, NO_CONTEXT_PATH));

        assertEquals(Set.of(Role.everyone()),
                filter.roles(TENANT_PIPELINE, TENANT_CONTEXT_PATH));

        assertEquals(Set.of(Role.buildService(TENANT, APPLICATION)),
                filter.roles(TENANT_PIPELINE, APPLICATION_CONTEXT_PATH));

        assertEquals(Set.of(Role.everyone()),
                filter.roles(TENANT_PIPELINE, APPLICATION2_CONTEXT_PATH));

        // Principals member of both tenantPipeline and tenantAdmin roles get correct roles
        assertEquals(Set.of(Role.athenzTenantAdmin(TENANT)),
                filter.roles(TENANT_ADMIN_AND_PIPELINE, TENANT_CONTEXT_PATH));

        assertEquals(Set.of(Role.athenzTenantAdmin(TENANT), Role.buildService(TENANT, APPLICATION)),
                filter.roles(TENANT_ADMIN_AND_PIPELINE, APPLICATION_CONTEXT_PATH));

        // Users have nothing special under their instance
        assertEquals(Set.of(Role.everyone()),
                filter.roles(USER, INSTANCE_CONTEXT_PATH));

        // Unprivileged users are just members of the everyone role.
        assertEquals(Set.of(Role.everyone()),
                filter.roles(USER, NO_CONTEXT_PATH));

        assertEquals(Set.of(Role.everyone()),
                filter.roles(USER, TENANT_CONTEXT_PATH));

        assertEquals(Set.of(Role.everyone()),
                filter.roles(USER, APPLICATION_CONTEXT_PATH));

        assertEquals(Set.of(Role.everyone()),
                filter.roles(USER, INSTANCE2_CONTEXT_PATH));
    }

}
