// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class TenantsMaintainerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void deleteTenantWithNoApplications() throws IOException {
        ManualClock clock = new ManualClock("2020-06-01T00:00:00");
        MaintainerTester tester = new MaintainerTester(clock, temporaryFolder);
        TenantRepository tenantRepository = tester.tenantRepository();
        ApplicationRepository applicationRepository = tester.applicationRepository();
        File applicationPackage = new File("src/test/apps/hosted");

        TenantName shouldBeDeleted = TenantName.from("to-be-deleted");
        TenantName shouldNotBeDeleted = TenantName.from("should-not-be-deleted");

        tenantRepository.addTenant(shouldBeDeleted);
        tenantRepository.addTenant(shouldNotBeDeleted);
        tenantRepository.addTenant(TenantRepository.HOSTED_VESPA_TENANT);

        tester.deployApp(applicationPackage, prepareParams(shouldNotBeDeleted));
        assertNotNull(tenantRepository.getTenant(shouldBeDeleted));
        assertNotNull(tenantRepository.getTenant(shouldNotBeDeleted));

        clock.advance(TenantsMaintainer.defaultTtlForUnusedTenant.plus(Duration.ofDays(1)));
        new TenantsMaintainer(applicationRepository, tester.curator(), new InMemoryFlagSource(), Duration.ofDays(1), clock).run();

        // One tenant should now have been deleted
        assertNull(tenantRepository.getTenant(shouldBeDeleted));
        assertNotNull(tenantRepository.getTenant(shouldNotBeDeleted));

        // System tenants should not be deleted
        assertNotNull(tenantRepository.getTenant(TenantName.defaultName()));
        assertNotNull(tenantRepository.getTenant(TenantRepository.HOSTED_VESPA_TENANT));

        // Delete app, add tenant again and deploy
        tester.applicationRepository().delete(applicationId(shouldNotBeDeleted));
        tenantRepository.addTenant(shouldBeDeleted);
        tester.deployApp(applicationPackage, prepareParams(shouldBeDeleted));
    }

    private PrepareParams.Builder prepareParams(TenantName tenantName) {
        return new PrepareParams.Builder().applicationId(applicationId(tenantName));
    }

    private ApplicationId applicationId(TenantName tenantName) {
        return ApplicationId.from(tenantName, ApplicationName.from("foo"), InstanceName.defaultName());
    }

}
