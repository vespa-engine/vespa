// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.test;

import com.yahoo.config.model.ConfigModel;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.builder.xml.ConfigModelId;
import com.yahoo.vespa.model.builder.xml.dom.LegacyConfigModelBuilder;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A simple test config model.
 *
 * @author  gjoranv
 */
public class SimpleConfigModel extends ConfigModel implements TestApi {

    private List<SimpleService> simpleServices = new ArrayList<>();
    private List<ParentService> parentServices = new ArrayList<>();

    public SimpleConfigModel(ConfigModelContext modelContext) {
        super(modelContext);
    }

    /** Implement TestApi */
    public int getNumSimpleServices() {
        return simpleServices.size();
    }
    public int getNumParentServices() {
        return parentServices.size();
    }

    public static class Builder extends LegacyConfigModelBuilder<SimpleConfigModel> {

        public Builder() {
            super(SimpleConfigModel.class);
        }

        @Override
        public List<ConfigModelId> handlesElements() {
            return Arrays.asList(ConfigModelId.fromName("simple"));
        }

        @Override
        public void doBuild(SimpleConfigModel configModel, Element spec, ConfigModelContext context) {
            int s,p; s=p=0;

            // Validate the services given in the config
            NodeList childNodes = spec.getChildNodes();
            for (int i=0; i < childNodes.getLength(); i++) {
                Node child   = childNodes.item(i);
                if (! (child instanceof Element)) {
                    // skip #text and #comment nodes
                    continue;
                }
                Element e = (Element)child;
                String service = e.getTagName();

                if (service.equals("simpleservice")) {
                    configModel.simpleServices.add(new DomTestServiceBuilder.SimpleServiceBuilder(s).build(context.getDeployState(), context.getParentProducer(), e));
                    s++;
                }
                else if (service.equals("parentservice")) {
                    configModel.parentServices.add(new DomTestServiceBuilder.ParentServiceBuilder(p).build(context.getDeployState(), context.getParentProducer(), e));
                    p++;
                }
                else {
                    throw new IllegalArgumentException("Unknown service: " + service);
                }
            }
        }
    }

}
