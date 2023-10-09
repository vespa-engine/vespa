// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.model;

import com.yahoo.search.pagetemplates.PageTemplate;

/**
 * Superclass of visitors over the page template object structure
 *
 * @author bratseth
 */
public class PageTemplateVisitor {

    /** Called each time a page template is encountered. This default implementation does nothing */
    public void visit(PageTemplate pageTemplate) {
    }

    /** Called each time a source or source placeholder is encountered. This default implementation does nothing */
    public void visit(Source source) {
    }

    /** Called each time a section or section placeholder is encountered. This default implementation does nothing */
    public void visit(Section section) {
    }

    /** Called each time a renderer is encountered. This default implementation does nothing */
    public void visit(Renderer renderer) {
    }

    /** Called each time a choice is encountered. This default implementation does nothing */
    public void visit(Choice choice) {
    }

    /** Called each time a map choice is encountered. This default implementation does nothing */
    public void visit(MapChoice choice) {
    }

    /** Called each time a placeholder is encountered. This default implementation does nothing */
    public void visit(Placeholder placeholder) {
    }

}
