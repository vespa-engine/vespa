// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.applications;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ProvisionLock;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.hosted.provision.NodeRepositoryTester;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class ApplicationsTest {

    @Test
    public void testApplications() {
        NodeRepositoryTester tester = new NodeRepositoryTester();
        Applications applications = new NodeRepositoryTester().nodeRepository().applications();
        ApplicationId app1 = ApplicationId.from("t1", "a1", "i1");
        ApplicationId app2 = ApplicationId.from("t1", "a2", "i1");
        ApplicationId app3 = ApplicationId.from("t1", "a2", "default");

        assertTrue(applications.get(app1).isEmpty());
        assertEquals(List.of(), applications.ids());
        applications.put(new Application(app1), tester.nodeRepository().lock(app1));
        assertEquals(app1, applications.get(app1).get().id());
        assertEquals(List.of(app1), applications.ids());
        NestedTransaction t = new NestedTransaction();
        applications.remove(app1, t, provisionLock(app1, tester));
        t.commit();
        assertTrue(applications.get(app1).isEmpty());
        assertEquals(List.of(), applications.ids());

        applications.put(new Application(app1), tester.nodeRepository().lock(app1));
        applications.put(new Application(app2), tester.nodeRepository().lock(app1));
        t = new NestedTransaction();
        applications.put(new Application(app3), t, tester.nodeRepository().lock(app1));
        assertEquals(List.of(app1, app2), applications.ids());
        t.commit();
        assertEquals(List.of(app1, app2, app3), applications.ids());
        t = new NestedTransaction();
        applications.remove(app1, t, provisionLock(app1, tester));
        applications.remove(app2, t, provisionLock(app2, tester));
        applications.remove(app3, t, provisionLock(app3, tester));
        assertEquals(List.of(app1, app2, app3), applications.ids());
        t.commit();
        assertTrue(applications.get(app1).isEmpty());
        assertEquals(List.of(), applications.ids());
    }

    private ProvisionLock provisionLock(ApplicationId application, NodeRepositoryTester tester) {
        return new ProvisionLock(application, tester.nodeRepository().lock(application));
    }

}
