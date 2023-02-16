package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Application;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class DeploymentInfoMaintainerTest {

    @Test
    void testDeploymentInfoMaintainer() {
        ApplicationId app1 = ApplicationId.from("t1", "a1", "default");
        ApplicationId app2 = ApplicationId.from("t2", "a1", "default");
        ZoneId z1 = ZoneId.from("prod.aws-us-east-1c");
        ZoneId z2 = ZoneId.from("prod.aws-eu-west-1a");

        DeploymentTester tester = new DeploymentTester(new ControllerTester(SystemName.Public));
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder().region(z1.region()).region(z2.region()).trustDefaultCertificate().build();
        List.of(app1, app2).forEach(app -> tester.newDeploymentContext(app).submit(applicationPackage).deploy());

        var maintainer = new DeploymentInfoMaintainer(tester.controller(), Duration.ofMinutes(5));
        var nodeRepo = tester.configServer().nodeRepository().allowPatching(true);
        nodeRepo.putApplication(z1, new Application(app1, List.of()));
        nodeRepo.putApplication(z1, new Application(app2, List.of()));
        assertEquals(0, tester.controller().applications().deploymentInfo().size());
        maintainer.maintain();
        assertEquals(4, tester.controller().applications().deploymentInfo().size());
        assertEquals(Set.of(new DeploymentId(app1, z1),
                            new DeploymentId(app1, z2),
                            new DeploymentId(app2, z1),
                            new DeploymentId(app2, z2)),
                     tester.controller().applications().deploymentInfo().keySet());
    }

}
