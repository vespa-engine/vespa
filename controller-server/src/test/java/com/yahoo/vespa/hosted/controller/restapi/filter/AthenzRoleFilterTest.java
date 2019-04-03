package com.yahoo.vespa.hosted.controller.restapi.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.jdisc.http.filter.security.cors.CorsFilterConfig;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.identifiers.ScrewdriverId;
import com.yahoo.vespa.hosted.controller.athenz.ApplicationAction;
import com.yahoo.vespa.hosted.controller.athenz.HostedAthenzIdentities;
import com.yahoo.vespa.hosted.controller.athenz.impl.AthenzFacade;
import com.yahoo.vespa.hosted.controller.athenz.mock.AthenzClientFactoryMock;
import com.yahoo.vespa.hosted.controller.athenz.mock.AthenzDbMock;
import com.yahoo.vespa.hosted.controller.role.Context;
import com.yahoo.vespa.hosted.controller.role.Role;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.Optional;
import java.util.Set;

import static java.util.Collections.emptySet;
import static org.junit.Assert.assertEquals;

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
    private static final TenantName TENANT = TenantName.from("mytenant");
    private static final TenantName TENANT2 = TenantName.from("othertenant");
    private static final ApplicationName APPLICATION = ApplicationName.from("myapp");
    private static final URI NO_CONTEXT_PATH = URI.create("/application/v4/");
    private static final URI TENANT_CONTEXT_PATH = URI.create("/application/v4/tenant/mytenant/");
    private static final URI APPLICATION_CONTEXT_PATH = URI.create("/application/v4/tenant/mytenant/application/myapp/");
    private static final URI TENANT2_CONTEXT_PATH = URI.create("/application/v4/tenant/othertenant/");
    private static final URI APPLICATION2_CONTEXT_PATH = URI.create("/application/v4/tenant/othertenant/application/myapp/");

    private ControllerTester tester;
    private AthenzRoleFilter filter;

    @Before
    public void setup() {
        tester = new ControllerTester();
        filter = new AthenzRoleFilter(new CorsFilterConfig.Builder().build(),
                                      new AthenzFacade(new AthenzClientFactoryMock(tester.athenzDb())),
                                      tester.controller());

        tester.athenzDb().hostedOperators.add(HOSTED_OPERATOR.getIdentity());
        tester.createTenant(TENANT.value(), TENANT_DOMAIN.getName(), null);
        tester.createApplication(TENANT, APPLICATION.value(), "default", 12345);
        AthenzDbMock.Domain tenantDomain = tester.athenzDb().domains.get(TENANT_DOMAIN);
        tenantDomain.admins.add(TENANT_ADMIN.getIdentity());
        tenantDomain.applications.get(new ApplicationId(APPLICATION.value())).addRoleMember(ApplicationAction.deploy, TENANT_PIPELINE.getIdentity());
        tester.createTenant(TENANT2.value(), TENANT_DOMAIN2.getName(), null);
        tester.createApplication(TENANT2, APPLICATION.value(), "default", 42);
    }

    @Test
    public void testTranslations() {

        // Only unprivileged users are members of the everyone role.
        assertEquals(emptySet(),
                     filter.membership(HOSTED_OPERATOR, APPLICATION_CONTEXT_PATH).contextsFor(Role.everyone));
        assertEquals(emptySet(),
                     filter.membership(TENANT_ADMIN, TENANT_CONTEXT_PATH).contextsFor(Role.everyone));
        assertEquals(Set.of(Context.unlimitedIn(tester.controller().system())),
                     filter.membership(TENANT_ADMIN, TENANT2_CONTEXT_PATH).contextsFor(Role.everyone));
        assertEquals(Set.of(Context.unlimitedIn(tester.controller().system())),
                     filter.membership(TENANT_PIPELINE, NO_CONTEXT_PATH).contextsFor(Role.everyone));
        assertEquals(Set.of(Context.unlimitedIn(tester.controller().system())),
                     filter.membership(USER, APPLICATION_CONTEXT_PATH).contextsFor(Role.everyone));

        // Only operators are members of the operator role.
        assertEquals(Set.of(Context.unlimitedIn(tester.controller().system())),
                     filter.membership(HOSTED_OPERATOR, TENANT_CONTEXT_PATH).contextsFor(Role.hostedOperator));
        assertEquals(emptySet(),
                     filter.membership(TENANT_ADMIN, NO_CONTEXT_PATH).contextsFor(Role.hostedOperator));
        assertEquals(emptySet(),
                     filter.membership(TENANT_PIPELINE, APPLICATION_CONTEXT_PATH).contextsFor(Role.hostedOperator));
        assertEquals(emptySet(),
                     filter.membership(USER, TENANT_CONTEXT_PATH).contextsFor(Role.hostedOperator));

        // Only tenant admins are tenant admins of their tenants.
        assertEquals(emptySet(),
                     filter.membership(HOSTED_OPERATOR, APPLICATION_CONTEXT_PATH).contextsFor(Role.athenzTenantAdmin));
        assertEquals(emptySet(), // TODO this is wrong, but we can't do better until we ask ZMS for roles.
                     filter.membership(TENANT_ADMIN, NO_CONTEXT_PATH).contextsFor(Role.athenzTenantAdmin));
        assertEquals(Set.of(Context.limitedTo(TENANT, tester.controller().system())),
                     filter.membership(TENANT_ADMIN, TENANT_CONTEXT_PATH).contextsFor(Role.athenzTenantAdmin));
        assertEquals(emptySet(),
                     filter.membership(TENANT_ADMIN, TENANT2_CONTEXT_PATH).contextsFor(Role.athenzTenantAdmin));
        assertEquals(emptySet(),
                     filter.membership(TENANT_PIPELINE, APPLICATION_CONTEXT_PATH).contextsFor(Role.athenzTenantAdmin));
        assertEquals(emptySet(),
                     filter.membership(USER, TENANT_CONTEXT_PATH).contextsFor(Role.athenzTenantAdmin));

        // Only build services are pipeline operators of their applications.
        assertEquals(emptySet(),
                     filter.membership(HOSTED_OPERATOR, APPLICATION_CONTEXT_PATH).contextsFor(Role.tenantPipeline));
        assertEquals(emptySet(),
                     filter.membership(TENANT_ADMIN, APPLICATION_CONTEXT_PATH).contextsFor(Role.tenantPipeline));
        assertEquals(Set.of(Context.limitedTo(TENANT, APPLICATION, tester.controller().system())),
                     filter.membership(TENANT_PIPELINE, APPLICATION_CONTEXT_PATH).contextsFor(Role.tenantPipeline));
        assertEquals(emptySet(),
                     filter.membership(TENANT_PIPELINE, APPLICATION2_CONTEXT_PATH).contextsFor(Role.tenantPipeline));
        assertEquals(emptySet(),
                     filter.membership(USER, APPLICATION_CONTEXT_PATH).contextsFor(Role.tenantPipeline));
    }

}
