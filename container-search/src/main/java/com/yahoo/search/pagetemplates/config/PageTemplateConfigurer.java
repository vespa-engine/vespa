// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.config;

import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.search.pagetemplates.PageTemplatesConfig;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.search.pagetemplates.PageTemplateRegistry;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides a static method to convert a page template config into a PageTemplateRegistry.
 * In addition, instances of this can be created to subscribe to config and keep an up to date registry reference.
 *
 * @author bratseth
 */
public class PageTemplateConfigurer {

    /**
     * Creates a new page template registry from the content of a config and returns it.
     * The returned registry will <b>not</b> be frozen. This should be done, by calling freeze(), before it is used.
     */
    public static PageTemplateRegistry toRegistry(PageTemplatesConfig config) {
        List<NamedReader> pageReaders=new ArrayList<>();
        int pageNumber=0;
        for (String pageString : config.page())
            pageReaders.add(new NamedReader("page[" + pageNumber++ + "]",new StringReader(pageString)));
        return new PageTemplateXMLReader().read(pageReaders,false);
    }

}
