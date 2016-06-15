// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.cloud.config.ElkConfig;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.container.configserver.TestOptions;
import org.junit.Test;

import java.io.File;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author lulf
 * @since 5.17
 */
public class ConfigServerContainerModelBuilderTest {
    @Test
    public void testHostedVespaInclude() {
        File testApp = new File("src/test/cfg/container/data/configserverinclude");
        FilesApplicationPackage app = FilesApplicationPackage.fromFile(testApp);
        MockRoot root = new MockRoot();
        new ConfigServerContainerModelBuilder(new TestOptions()).build(new DeployState.Builder().applicationPackage(app).build(), null, root, XML.getChild(XML.getDocument(app.getServices()).getDocumentElement(), "jdisc"));
        root.freezeModelTopology();
        ElkConfig config = root.getConfig(ElkConfig.class, "configserver/configserver");
        assertThat(config.elasticsearch().size(), is(1));
        assertThat(config.elasticsearch(0).host(), is("foo"));
    }
}
