// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.vespa.athenz.api.OktaAccessToken;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordData;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordId;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.GlobalDnsName;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import com.yahoo.vespa.hosted.controller.rotation.Rotation;
import com.yahoo.vespa.hosted.controller.rotation.RotationId;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.component;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType.systemTest;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class DnsMaintainerTest {

    private DeploymentTester tester;
    private DnsMaintainer maintainer;

    @Before
    public void before() {
        tester = new DeploymentTester();
        maintainer = new DnsMaintainer(tester.controller(), Duration.ofHours(12),
                                       new JobControl(new MockCuratorDb()),
                                       tester.controllerTester().nameService());
    }

    @Test
    public void removes_record_for_unassigned_rotation() {
        Application application = tester.createApplication("app1", "tenant1", 1, 1L);

        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .globalServiceId("foo")
                .region("us-west-1")
                .region("us-central-1")
                .build();

        Function<String, Optional<Record>> findCname = (name) -> tester.controllerTester().nameService()
                                                                       .findRecords(Record.Type.CNAME,
                                                                                    RecordName.from(name))
                                                                       .stream()
                                                                       .findFirst();

        // Deploy application
        tester.deployCompletely(application, applicationPackage);
        assertEquals(3, records().size());

        Optional<Record> record = findCname.apply("app1--tenant1.global.vespa.yahooapis.com");
        assertTrue(record.isPresent());
        assertEquals("app1--tenant1.global.vespa.yahooapis.com", record.get().name().asString());
        assertEquals("rotation-fqdn-01.", record.get().data().asString());

        record = findCname.apply("app1--tenant1.global.vespa.oath.cloud");
        assertTrue(record.isPresent());
        assertEquals("app1--tenant1.global.vespa.oath.cloud", record.get().name().asString());
        assertEquals("rotation-fqdn-01.", record.get().data().asString());

        record = findCname.apply("app1.tenant1.global.vespa.yahooapis.com");
        assertTrue(record.isPresent());
        assertEquals("app1.tenant1.global.vespa.yahooapis.com", record.get().name().asString());
        assertEquals("rotation-fqdn-01.", record.get().data().asString());

        // DnsMaintainer does nothing
        maintainer.maintain();
        assertTrue("DNS record is not removed", findCname.apply("app1--tenant1.global.vespa.yahooapis.com").isPresent());
        assertTrue("DNS record is not removed", findCname.apply("app1--tenant1.global.vespa.oath.cloud").isPresent());
        assertTrue("DNS record is not removed", findCname.apply("app1.tenant1.global.vespa.yahooapis.com").isPresent());

        // Remove application
        applicationPackage = new ApplicationPackageBuilder()
                .environment(Environment.prod)
                .allow(ValidationId.deploymentRemoval)
                .build();
        tester.jobCompletion(component).application(application).nextBuildNumber().uploadArtifact(applicationPackage).submit();

        tester.deployAndNotify(application, applicationPackage, true, systemTest);
        tester.applications().deactivate(application.id(), ZoneId.from(Environment.test, RegionName.from("us-east-1")));
        tester.applications().deactivate(application.id(), ZoneId.from(Environment.staging, RegionName.from("us-east-3")));
        tester.applications().deleteApplication(application.id(), Optional.of(new OktaAccessToken("okta-token")));

        // DnsMaintainer removes records
        for (int i = 0; i < ControllerTester.availableRotations; i++) {
            maintainer.maintain();
        }
        assertFalse("DNS record removed", findCname.apply("app1--tenant1.global.vespa.yahooapis.com").isPresent());
        assertFalse("DNS record removed", findCname.apply("app1--tenant1.global.vespa.oath.cloud").isPresent());
        assertFalse("DNS record removed", findCname.apply("app1.tenant1.global.vespa.yahooapis.com").isPresent());
    }

    @Test
    public void rate_limit_record_removal() {
        // Create stale records
        int staleTotal = ControllerTester.availableRotations;
        for (int i = 1; i <= staleTotal; i++) {
            Rotation r = rotation(i);
            tester.controllerTester().nameService().createCname(RecordName.from("stale-record-" + i + "." +
                                                                                GlobalDnsName.OATH_DNS_SUFFIX),
                                                                RecordData.from(r.name() + "."));
        }

        // One record is removed per run
        for (int i = 1; i <= staleTotal*2; i++) {
            maintainer.run();
            assertEquals(Math.max(staleTotal - i, 0), records().size());
        }
    }

    private Map<RecordId, Set<Record>> records() {
        return tester.controllerTester().nameService().records();
    }

    private static Rotation rotation(int n) {
        String id = String.format("%02d", n);
        return new Rotation(new RotationId("rotation-id-" + id), "rotation-fqdn-" + id);
    }

}
