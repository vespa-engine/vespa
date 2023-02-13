// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.test;

import com.yahoo.test.StandardConfig.Builder;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.PortAllocBridge;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This is a service that creates child services
 */
public class ParentService extends AbstractService implements com.yahoo.test.StandardConfig.Producer {

    /**
     * Creates a new ParentService instance
     *
     * @param parent     The parent ConfigProducer.
     * @param name       Service name
     * @param config     The xml config Element for this Service
     */
    public ParentService(TreeConfigProducer parent, String name,
                         Element config)
    {
        super(parent, name);

        int s,p; s=p=0;
        NodeList childNodes = config.getChildNodes();
        for (int i=0; i < childNodes.getLength(); i++) {
            Node child   = childNodes.item(i);
            if (! (child instanceof Element)) {
                // skip #text and #comment nodes
                continue;
            }
            Element e = (Element)child;
            String service = e.getTagName();

            if (service.equals("simpleservice")) {
                new SimpleService(this, "simpleservice."+s);
                s++;
            }
            else if (service.equals("parentservice")) {
                new ParentService(this, "parentservice."+p, e);
                p++;
            }
            else {
                throw new IllegalArgumentException("Unknown service: " + service);
            }
        }
    }

    @Override
    public void getConfig(Builder builder) {
        builder.astring("parentservice");
    }
    
    public int getPortCount() { return 0; }

    @Override public void allocatePorts(int start, PortAllocBridge from) { }
}
