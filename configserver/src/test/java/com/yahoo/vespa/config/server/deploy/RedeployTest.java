// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.component.Version;
import org.junit.Test;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static com.yahoo.vespa.config.server.deploy.DeployTester.createFailingModelFactory;
import static com.yahoo.vespa.config.server.deploy.DeployTester.createModelFactory;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests redeploying of an already existing application.
 *
 * @author bratseth
 */
public class RedeployTest {

    @Test
    public void testRedeploy() {
        DeployTester tester = new DeployTester();
        tester.deployApp("src/test/apps/app");
        Optional<com.yahoo.config.provision.Deployment> deployment = tester.redeployFromLocalActive();

        assertTrue(deployment.isPresent());
        long activeSessionIdBefore = tester.applicationRepository().getActiveSession(tester.applicationId()).getSessionId();
        assertEquals(tester.applicationId(), tester.tenant().getLocalSessionRepo().getSession(activeSessionIdBefore).getApplicationId());
        deployment.get().activate();
        long activeSessionIdAfter =  tester.applicationRepository().getActiveSession(tester.applicationId()).getSessionId();
        assertEquals(activeSessionIdAfter, activeSessionIdBefore + 1);
        assertEquals(tester.applicationId(), tester.tenant().getLocalSessionRepo().getSession(activeSessionIdAfter).getApplicationId());
    }

    /** No deployment is done because there is no local active session. */
    @Test
    public void testNoRedeploy() {
        List<ModelFactory> modelFactories = List.of(createModelFactory(Clock.systemUTC()),
                                                    createFailingModelFactory(Version.fromString("1.0.0")));
        DeployTester tester = new DeployTester(modelFactories);
        ApplicationId id = ApplicationId.from(tester.tenant().getName(),
                                              ApplicationName.from("default"),
                                              InstanceName.from("default"));
        assertFalse(tester.redeployFromLocalActive(id).isPresent());
    }

}
