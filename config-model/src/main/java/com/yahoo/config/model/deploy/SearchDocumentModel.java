// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.deploy;

import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.vespa.documentmodel.DocumentModel;
import com.yahoo.vespa.model.search.SearchDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Internal helper class to retrieve document model and search definitions.
 *
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class SearchDocumentModel {

    private final DocumentModel documentModel;
    private final List<SearchDefinition> searchDefinitions;

    public SearchDocumentModel(DocumentModel documentModel, List<SearchDefinition> searchDefinitions) {
        this.documentModel = documentModel;
        this.searchDefinitions = searchDefinitions;

    }

    public DocumentModel getDocumentModel() {
        return documentModel;
    }

    public List<SearchDefinition> getSearchDefinitions() {
        return searchDefinitions;
    }

    public static SearchDocumentModel fromBuilderAndNames(SearchBuilder builder, Map<String, String> names) {
        List<SearchDefinition> ret = new ArrayList<>();
        for (com.yahoo.searchdefinition.Search search : builder.getSearchList()) {
            ret.add(new SearchDefinition(names.get(search.getName()), search));
        }
        return new SearchDocumentModel(builder.getModel(), ret);
    }

    public static SearchDocumentModel fromBuilder(SearchBuilder builder) {
        List<SearchDefinition> ret = new ArrayList<>();
        for (com.yahoo.searchdefinition.Search search : builder.getSearchList()) {
            ret.add(new SearchDefinition(search.getName(), search));
        }
        return new SearchDocumentModel(builder.getModel(), ret);
    }

}
