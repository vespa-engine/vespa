// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates;

import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.search.pagetemplates.engine.Resolver;

/**
 * @author bratseth
 */
public class PageTemplateRegistry extends ComponentRegistry<PageTemplate> {

    public void register(PageTemplate pageTemplate) {
        super.register(pageTemplate.getId(), pageTemplate);
    }

}
