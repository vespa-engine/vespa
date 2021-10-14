// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.model.test.MockRoot;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

/**
 * @author Ulf Lilleengen
 */
public class ConfigModelContextTest {

    @Test
    public void testConfigModelContext() {
        MockRoot root = new MockRoot();
        String id = "foobar";
        ApplicationPackage pkg = new MockApplicationPackage.Builder()
                .withServices("<services version=\"1.0\"><admin version=\"2.0\" /></services>")
                .build();
        DeployState deployState = DeployState.createTestState(pkg);
        DeployLogger logger = deployState.getDeployLogger();
        ConfigModelContext ctx = ConfigModelContext.create(deployState, null, null, root, id);
        assertThat(ctx.getApplicationPackage(), is(pkg));
        assertThat(ctx.getProducerId(), is(id));
        assertThat(ctx.getParentProducer(), is(root));
        assertThat(ctx.getDeployLogger(), is(logger));
        ctx = ConfigModelContext.create(root.getDeployState(), null, null, root, id);
        assertThat(ctx.getProducerId(), is(id));
        assertThat(ctx.getParentProducer(), is(root));
        AbstractConfigProducer newRoot = new MockRoot("bar");
        ctx = ctx.withParent(newRoot);
        assertThat(ctx.getProducerId(), is(id));
        assertThat(ctx.getParentProducer(), is(not(root)));
        assertThat(ctx.getParentProducer(), is(newRoot));
    }

}
