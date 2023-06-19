// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers;

import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.prelude.query.HasIndexItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.PrefixItem;
import com.yahoo.prelude.query.ToolBox;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.schema.Field;
import com.yahoo.search.schema.FieldInfo;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;

import static com.yahoo.search.grouping.GroupingQueryParser.SELECT_PARAMETER_PARSING;

/**
 * Validation of query operators against the schema which is searched
 *
 * @author bratseth
 */
@After(SELECT_PARAMETER_PARSING)
@Before(PhaseNames.BACKEND)
public class QueryValidator extends Searcher {

    @Override
    public Result search(Query query, Execution execution) {
        var session = execution.context().schemaInfo().newSession(query);
        ToolBox.visit(new TermSearchValidator(session), query.getModel().getQueryTree().getRoot());
        ToolBox.visit(new PrefixSearchValidator(session), query.getModel().getQueryTree().getRoot());
        return execution.search(query);
    }

    private abstract static class TermValidator extends ToolBox.QueryVisitor {

        final SchemaInfo.Session schema;

        public TermValidator(SchemaInfo.Session schema) {
            this.schema = schema;
        }

    }

    private static class TermSearchValidator extends TermValidator {

        public TermSearchValidator(SchemaInfo.Session schema) {
            super(schema);
        }

        @Override
        public boolean visit(Item item) {
            if (item instanceof HasIndexItem indexItem) {
                var field = schema.fieldInfo(indexItem.getIndexName());
                if (! field.isPresent()) return true;
                if (field.get().type().kind() == Field.Type.Kind.TENSOR)
                    throw new IllegalArgumentException("Cannot search for terms in '" + indexItem.getIndexName() +
                                                       "': It is a tensor field");
            }
            return true;
        }

    }

    private static class PrefixSearchValidator extends TermValidator {

        public PrefixSearchValidator(SchemaInfo.Session schema) {
            super(schema);
        }

        @Override
        public boolean visit(Item item) {
            if (schema.isStreaming()) return true; // prefix is always supported
            if (item instanceof PrefixItem prefixItem) {
                var field = schema.fieldInfo(prefixItem.getIndexName());
                if (! field.isPresent()) return true;
                if ( ! field.get().isAttribute())
                    throw new IllegalArgumentException("'" + prefixItem.getIndexName() + "' is not an attribute field: Prefix matching is not supported");
                if (field.get().isIndex()) // index overrides attribute
                    throw new IllegalArgumentException("'" + prefixItem.getIndexName() + "' is an index field: Prefix matching is not supported even when it is also an attribute");
            }
            return true;
        }

    }

}
