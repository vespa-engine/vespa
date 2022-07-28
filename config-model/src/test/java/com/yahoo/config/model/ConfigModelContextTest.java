// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.model.test.MockRoot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * @author Ulf Lilleengen
 */
public class ConfigModelContextTest {

    @Test
    void testConfigModelContext() {
        MockRoot root = new MockRoot();
        String id = "foobar";
        ApplicationPackage pkg = new MockApplicationPackage.Builder()
                .withServices("<services version=\"1.0\"><admin version=\"2.0\" /></services>")
                .build();
        DeployState deployState = DeployState.createTestState(pkg);
        DeployLogger logger = deployState.getDeployLogger();
        ConfigModelContext ctx = ConfigModelContext.create(deployState, null, null, root, id);
        assertEquals(pkg, ctx.getApplicationPackage());
        assertEquals(id, ctx.getProducerId());
        assertEquals(root, ctx.getParentProducer());
        assertEquals(logger, ctx.getDeployLogger());
        ctx = ConfigModelContext.create(root.getDeployState(), null, null, root, id);
        assertEquals(id, ctx.getProducerId());
        assertEquals(root, ctx.getParentProducer());
        AbstractConfigProducer newRoot = new MockRoot("bar");
        ctx = ctx.withParent(newRoot);
        assertEquals(id, ctx.getProducerId());
        assertNotEquals(root, ctx.getParentProducer());
        assertEquals(newRoot, ctx.getParentProducer());
    }

}
