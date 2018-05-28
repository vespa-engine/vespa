// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import org.junit.Test;

import java.io.File;
import java.time.Duration;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class TenantsMaintainerTest {

    @Test
    public void deleteTenantWithNoApplications() {
        MaintainerTester tester = new MaintainerTester();
        TenantRepository tenantRepository = tester.tenantRepository();
        ApplicationRepository applicationRepository = tester.applicationRepository();

        TenantName shouldBeDeleted = TenantName.from("to-be-deleted");
        TenantName shouldNotBeDeleted = TenantName.from("should-not-be-deleted");

        tenantRepository.addTenant(shouldBeDeleted);
        tenantRepository.addTenant(shouldNotBeDeleted);
        applicationRepository.deploy(new File("src/test/apps/app"),
                new PrepareParams.Builder()
                        .applicationId(ApplicationId.from(shouldNotBeDeleted, ApplicationName.from("foo"), InstanceName.defaultName()))
                        .build());
        assertNotNull(tenantRepository.getTenant(shouldBeDeleted));
        assertNotNull(tenantRepository.getTenant(shouldNotBeDeleted));

        new TenantsMaintainer(applicationRepository, tester.curator(), Duration.ofDays(1)).run();

        // One tenant should now have been deleted
        assertNull(tenantRepository.getTenant(shouldBeDeleted));
        assertNotNull(tenantRepository.getTenant(shouldNotBeDeleted));
    }
}
