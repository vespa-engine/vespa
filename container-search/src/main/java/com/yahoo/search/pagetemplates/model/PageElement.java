// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.model;

import com.yahoo.component.provider.Freezable;

/**
 * Implemented by all page template model classes
 *
 * @author bratseth
 */
public interface PageElement extends Freezable {

    /** Accepts a visitor to this structure */
    void accept(PageTemplateVisitor visitor);

}
