// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.prelude.cluster;

import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.search.Query;
import com.yahoo.search.searchchain.Execution;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Resolves schemas from query and execution context
 *
 * @author bjorncs
 */
class SchemaResolver {

    private final Set<String> schemas;

    SchemaResolver(DocumentdbInfoConfig cfg) {
        this(cfg.documentdb().stream().map(DocumentdbInfoConfig.Documentdb::name).toList());
    }

    SchemaResolver(Collection<String> schemas) {
        this.schemas = new LinkedHashSet<>(schemas);
    }

    Set<String> resolve(Query query, Execution execution) {
        return resolve(query, execution.context().getIndexFacts());
    }

    Set<String> resolve(Query query, IndexFacts indexFacts) {
        if (schemas.size() == 1) return Set.of(schemas.iterator().next());
        var restrict = query.getModel().getRestrict();
        if (restrict == null || restrict.isEmpty()) {
            Set<String> sources = query.getModel().getSources();
            return (sources == null || sources.isEmpty())
                    ? schemas
                    : new LinkedHashSet<>(indexFacts.newSession(sources, Set.of(), schemas).documentTypes());
        } else {
            return filterValidDocumentTypes(restrict);
        }
    }

    private Set<String> filterValidDocumentTypes(Collection<String> restrict) {
        Set<String> retval = new LinkedHashSet<>();
        for (String docType : restrict) {
            if (docType != null && schemas.contains(docType)) {
                retval.add(docType);
            }
        }
        return retval;
    }

}
