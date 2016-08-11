// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.application.Xml;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.application.api.ApplicationPackage;

import com.yahoo.path.Path;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ContainerModel;
import com.yahoo.vespa.model.container.configserver.option.CloudConfigOptions;
import com.yahoo.vespa.model.container.configserver.ConfigserverCluster;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.List;

/**
 * Builds the config model for the standalone config server.
 *
 * @author lulf
 * @since 5.16
 */
public class ConfigServerContainerModelBuilder extends ContainerModelBuilder {

    private final CloudConfigOptions options;
    private final static String HOSTED_VESPA_INCLUDE_DIR = "hosted-vespa";

    public ConfigServerContainerModelBuilder(CloudConfigOptions options) {
        super(true, Networking.enable);
        this.options = options;
    }


    @Override
    public void doBuild(ContainerModel model, Element spec, ConfigModelContext modelContext) {
        ApplicationPackage app = modelContext.getDeployState().getApplicationPackage();
        if ( ! app.getFiles(Path.fromString(HOSTED_VESPA_INCLUDE_DIR), ".xml").isEmpty()) {
            app.validateIncludeDir(HOSTED_VESPA_INCLUDE_DIR);
            List<Element> configModelElements = Xml.allElemsFromPath(app, HOSTED_VESPA_INCLUDE_DIR);
            mergeInto(spec, configModelElements);
        }

        ConfigserverCluster cluster = new ConfigserverCluster(modelContext.getParentProducer(), "configserver", options);
        super.doBuild(model, spec, modelContext.withParent(cluster));
        cluster.setContainerCluster(model.getCluster());
    }

    private void mergeInto(Element destination, List<Element> configModelElements) {
        for (Element jdiscElement: configModelElements) {
            for (Node child = jdiscElement.getFirstChild();  child != null; child = child.getNextSibling()) {
                Node copiedNode = destination.getOwnerDocument().importNode(child, true);
                destination.appendChild(copiedNode);
            }
        }
    }

    @Override
    protected void addDefaultComponents(ContainerCluster containerCluster) {
        // To avoid search specific stuff.
    }

    @Override
    protected void addDefaultHandlers(ContainerCluster containerCluster) {
        addDefaultHandlersExceptStatus(containerCluster);
    }
}
