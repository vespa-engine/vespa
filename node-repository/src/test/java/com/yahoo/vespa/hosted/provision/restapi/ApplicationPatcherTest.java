// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.NodeRepositoryTester;
import com.yahoo.vespa.hosted.provision.applications.Application;
import org.junit.Test;

import java.io.ByteArrayInputStream;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class ApplicationPatcherTest {

    @Test
    public void testPatching() {
        NodeRepositoryTester tester = new NodeRepositoryTester();
        Application application = Application.empty(ApplicationId.from("t1", "a1", "i1"));
        tester.nodeRepository().applications().put(application, tester.nodeRepository().applications().lock(application.id()));
        String patch = "{ \"currentReadShare\" :0.4, \"maxReadShare\": 1.0 }";
        ApplicationPatcher patcher = new ApplicationPatcher(new ByteArrayInputStream(patch.getBytes()),
                                                            application.id(),
                                                            tester.nodeRepository());
        Application patched = patcher.apply();
        assertEquals(0.4, patcher.application().status().currentReadShare(), 0.0000001);
        assertEquals(1.0, patcher.application().status().maxReadShare(), 0.0000001);
        patcher.close();
    }

}
