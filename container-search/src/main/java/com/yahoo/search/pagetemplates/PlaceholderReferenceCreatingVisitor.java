// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates;

import com.yahoo.search.pagetemplates.model.MapChoice;
import com.yahoo.search.pagetemplates.model.PageTemplateVisitor;
import com.yahoo.search.pagetemplates.model.Placeholder;

import java.util.Map;

/**
 * Creates references from all placeholders to the choices which resolves them.
 * If a placeholder is encountered which is not resolved by any choice, an IllegalArgumentException is thrown.
 *
 * @author bratseth
 */
class PlaceholderReferenceCreatingVisitor extends PageTemplateVisitor {

    private final Map<String, MapChoice> placeholderIdToChoice;

    public PlaceholderReferenceCreatingVisitor(Map<String, MapChoice> placeholderIdToChoice) {
        this.placeholderIdToChoice = placeholderIdToChoice;
    }

    @Override
    public void visit(Placeholder placeholder) {
        MapChoice choice = placeholderIdToChoice.get(placeholder.getId());
        if (choice == null)
            throw new IllegalArgumentException(placeholder + " is not referenced by any choice");
        placeholder.setValueContainer(choice);
    }

}
