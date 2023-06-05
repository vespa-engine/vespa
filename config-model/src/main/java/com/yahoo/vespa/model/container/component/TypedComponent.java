// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.container.component;

import com.yahoo.osgi.provider.model.ComponentModel;
import org.w3c.dom.Element;

/**
 * @author bjorncs
 */
abstract class TypedComponent extends SimpleComponent {

    private final Element xml;

    protected TypedComponent(String className, String bundle, Element xml) {
        super(new ComponentModel(xml.getAttribute("id"),  className, bundle));
        this.xml = xml;
    }

}
