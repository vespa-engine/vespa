// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.builder.xml.ConfigModelBuilder;
import com.yahoo.config.model.builder.xml.ConfigModelId;
import com.yahoo.vespa.model.admin.Admin;
import com.yahoo.vespa.model.content.Content;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author baldersheim
 */
public class DomContentBuilder extends ConfigModelBuilder<Content> {

    public static final List<ConfigModelId> configModelIds = Collections.singletonList(ConfigModelId.fromName("content"));

    public DomContentBuilder() {
        super(Content.class);
    }

    @Override
    public List<ConfigModelId> handlesElements() {
        return configModelIds;
    }

    @Override
    public void doBuild(Content content, Element xml, ConfigModelContext modelContext) {
        content.build(xml, modelContext);
    }

}
