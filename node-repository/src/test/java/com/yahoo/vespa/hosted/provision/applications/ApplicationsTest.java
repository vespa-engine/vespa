// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.applications;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationTransaction;
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
        Applications applications = new NodeRepositoryTester().nodeRepository().applications();
        ApplicationId app1 = ApplicationId.from("t1", "a1", "i1");
        ApplicationId app2 = ApplicationId.from("t1", "a2", "i1");
        ApplicationId app3 = ApplicationId.from("t1", "a2", "default");

        assertTrue(applications.get(app1).isEmpty());
        assertEquals(List.of(), applications.ids());
        applications.put(Application.empty(app1), () -> {});
        assertEquals(app1, applications.get(app1).get().id());
        assertEquals(List.of(app1), applications.ids());
        NestedTransaction t = new NestedTransaction();
        applications.remove(new ApplicationTransaction(provisionLock(app1), t));
        t.commit();
        assertTrue(applications.get(app1).isEmpty());
        assertEquals(List.of(), applications.ids());

        applications.put(Application.empty(app1), () -> {});
        applications.put(Application.empty(app2), () -> {});
        t = new NestedTransaction();
        applications.put(Application.empty(app3), new ApplicationTransaction(provisionLock(app1), t));
        assertEquals(List.of(app1, app2), applications.ids());
        t.commit();
        assertEquals(List.of(app1, app2, app3), applications.ids());
        t = new NestedTransaction();
        applications.remove(new ApplicationTransaction(provisionLock(app1), t));
        applications.remove(new ApplicationTransaction(provisionLock(app2), t));
        applications.remove(new ApplicationTransaction(provisionLock(app3), t));
        assertEquals(List.of(app1, app2, app3), applications.ids());
        t.commit();
        assertTrue(applications.get(app1).isEmpty());
        assertEquals(List.of(), applications.ids());
    }

    private ProvisionLock provisionLock(ApplicationId application) {
        return new ProvisionLock(application, () -> {});
    }

}
