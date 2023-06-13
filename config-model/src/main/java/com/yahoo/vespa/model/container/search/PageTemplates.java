// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.io.reader.NamedReader;
import com.yahoo.search.pagetemplates.config.PageTemplateXMLReader;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.search.pagetemplates.PageTemplatesConfig;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns the page templates to be handed to the qrs nodes.
 * Owned by a container cluster.
 *
 * @author bratseth
 */
public class PageTemplates implements Serializable, PageTemplatesConfig.Producer {

    private final List<String> pages = new ArrayList<>();
    
    /** Validates page templates in an application package. The passed readers will be closed. */
    public static void validate(ApplicationPackage applicationPackage) {
        List<NamedReader> pageTemplateFiles=null;
        try {
            pageTemplateFiles=applicationPackage.getPageTemplateFiles();
            new PageTemplateXMLReader().read(pageTemplateFiles,true); // Parse XML for validation only
        }
        finally {
            NamedReader.closeAll(pageTemplateFiles);
        }
    }

    /** Creates from an application package. The passed readers will be closed. */
    public static PageTemplates create(ApplicationPackage applicationPackage) {
        List<NamedReader> pageTemplateFiles=null;
        try {
            pageTemplateFiles=applicationPackage.getPageTemplateFiles();
            return new PageTemplates(pageTemplateFiles);
        }
        finally {
            NamedReader.closeAll(pageTemplateFiles);
        }
    }

    // We are representing these as XML rather than a structured config type because the structure
    // is not easily representable by config (arbitrary nesting of many types of elements within each other)
    // and config<->xml generation will not pull its weight in work and possible bugs.
    // The XML content is already validated when we get here.
    public PageTemplates(List<NamedReader> readers) {
        for (NamedReader pageReader : readers) {
            try {
                pages.add(contentAsString(pageReader));
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not read page template '" + pageReader.getName() + "'",e);
            }
        }
    }

    @Override
    public void getConfig(PageTemplatesConfig.Builder builder) {
        for (String page : pages) {
            builder.page(page);
        }
    }

    private String contentAsString(Reader pageReader) throws IOException {
        BufferedReader bufferedReader=new BufferedReader(pageReader);
        StringBuilder b=new StringBuilder();
        String line;
        while (null!=(line=bufferedReader.readLine())) {
            b.append(line);
            b.append("\n");
        }
        return b.toString();
    }

    @Override
    public String toString() {
        return pages.toString();
    }

    /**
     * The config produced by this
     *
     * @return page templates config
     */
    public PageTemplatesConfig getConfig() {
        PageTemplatesConfig.Builder ptB = new PageTemplatesConfig.Builder();
        getConfig(ptB);
        return new PageTemplatesConfig(ptB);
    }
    
}
