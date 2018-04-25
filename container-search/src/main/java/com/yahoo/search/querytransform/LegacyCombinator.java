// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.language.Language;
import com.yahoo.log.LogLevel;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.IndexedItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NotItem;
import com.yahoo.prelude.query.NullItem;
import com.yahoo.prelude.query.RankItem;
import com.yahoo.prelude.query.parser.CustomParser;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.yolean.Exceptions;
import com.yahoo.search.query.Properties;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.query.parser.ParserFactory;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;

/**
 * Compatibility layer to implement the old multi part query syntax, along with
 * the features of QueryCombinator. Do <b>not</b> use both QueryCombinator and
 * LegacyCombinator in a single search.
 *
 * <p>
 * A searcher which grabs query parameters of the form
 * "defidx.(identifier)=(index name)" and "query.(identifier)=(user query)",
 * parses them and adds them as AND items to the query root.
 *
 * <p>
 * If the given default index does not exist in the search definition, the query
 * part will be parsed with the settings of the default index set to "".
 *
 * <p>
 * If any of the following arguments exist, they will be used:
 *
 * <p>
 * query.(identifier)=query string<br>
 * query.(identifier).operator={"req", "rank", "not"}, where "req" is default<br>
 * query.(identifier).defidx=default index<br>
 * query.(identifier).type={"all", "any", "phrase", "adv", "web"} where "all" is
 * default
 *
 * <p>
 * If both defidx.(identifier) and any of
 * query.(identifier).{operator,defidx,type} is present in the query, an
 * InvalidQueryParameter error will be added, and the query will be passed
 * through untransformed.
 *
 * @author Steinar Knutsen
 */
@Before({"transformedQuery", "com.yahoo.prelude.querytransform.StemmingSearcher"})
public class LegacyCombinator extends Searcher {

    private static final String TYPESUFFIX = ".type";
    private static final String OPERATORSUFFIX = ".operator";
    private static final String DEFIDXSUFFIX = ".defidx";
    private static final String DEFIDXPREFIX = "defidx.";
    private static final String QUERYPREFIX = "query.";

    private enum Combinator {
        REQUIRED("req"), PREFERRED("rank"), EXCLUDED("not");

        String parameterValue;

        private Combinator(String parameterValue) {
            this.parameterValue = parameterValue;
        }

        static Combinator getCombinator(String name) {
            for (Combinator c : Combinator.values()) {
                if (c.parameterValue.equals(name)) {
                    return c;
                }
            }
            return REQUIRED;
        }
    }

    private static class QueryPart {
        final String query;
        final String defaultIndex;
        final Combinator operator;
        final String identifier;
        final Query.Type syntax;

        QueryPart(String identifier, String defaultIndex, String oldIndex,
                String operator, String query, String syntax) {
            validateArguments(identifier, defaultIndex, oldIndex,
                    operator,syntax);
            this.query = query;
            if (defaultIndex != null) {
                this.defaultIndex = defaultIndex;
            } else {
                this.defaultIndex = oldIndex;
            }
            this.operator = Combinator.getCombinator(operator);
            this.identifier = identifier;
            this.syntax = Query.Type.getType(syntax);
        }

        private static void validateArguments(String identifier, String defaultIndex,
                String oldIndex, String operator, String syntax) {
            if (defaultIndex == null) {
                return;
            }
            if (oldIndex != null) {
                throw new IllegalArgumentException(createErrorMessage(identifier, DEFIDXSUFFIX));
            }
            if (operator != null) {
                throw new IllegalArgumentException(createErrorMessage(identifier, OPERATORSUFFIX));
            }
            if (syntax != null) {
                throw new IllegalArgumentException(createErrorMessage(identifier, TYPESUFFIX));
            }
        }

        private static String createErrorMessage(String identifier, String legacyArgument) {
            return "Cannot set both " + DEFIDXPREFIX + identifier + " and "
                    + QUERYPREFIX + identifier + legacyArgument + ".";
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result
                    + ((identifier == null) ? 0 : identifier.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            QueryPart other = (QueryPart) obj;
            if (identifier == null) {
                if (other.identifier != null)
                    return false;
            } else if (!identifier.equals(other.identifier))
                return false;
            return true;
        }

        @Override
        public String toString() {
            return "QueryPart(" + identifier + ", " + defaultIndex + ", "
                    + operator + ", " + syntax + ")";
        }
    }

    @Override
    public Result search(Query query, Execution execution) {
        Set<QueryPart> pieces;
        Set<String> usedSources;
        IndexFacts indexFacts = execution.context().getIndexFacts();
        try {
            pieces = findQuerySnippets(query.properties());
        } catch (IllegalArgumentException e) {
            query.errors().add(ErrorMessage.createInvalidQueryParameter("LegacyCombinator got invalid parameters: "
                    + e.getMessage()));
            return execution.search(query);
        }
        if (pieces.size() == 0) {
            return execution.search(query);
        }
        IndexFacts.Session session = indexFacts.newSession(query);
        Language language = query.getModel().getParsingLanguage();
        addAndItems(language, query, pieces, session, execution.context());
        addRankItems(language, query, pieces, session, execution.context());
        try {
            addNotItems(language, query, pieces, session, execution.context());
        } catch (IllegalArgumentException e) {
            query.errors().add(ErrorMessage.createInvalidQueryParameter("LegacyCombinator found only excluding terms, no including."));
            return execution.search(query);
        }
        query.trace("Adding extra query parts.", true, 2);
        return execution.search(query);
    }

    private void addNotItems(Language language, Query query, Set<QueryPart> pieces,
                             IndexFacts.Session session, Execution.Context context) {
        for (QueryPart part : pieces) {
            if (part.operator != Combinator.EXCLUDED) continue;

            String defaultIndex = defaultIndex(session, part);
            Item item = parse(language, query, part, defaultIndex, context);
            if (item == null) continue;

            setDefaultIndex(part, defaultIndex, item);
            addNotItem(query.getModel().getQueryTree(), item);
        }

    }

    private void addNotItem(QueryTree queryTree, Item item) {
        Item root = queryTree.getRoot();
        // JavaDoc claims I can get null, code gives NullItem... well, well, well...
        if (root instanceof NullItem || root == null) {
            // errr... no positive branch at all?
            throw new IllegalArgumentException("No positive terms for query.");
        } else if (root.getClass() == NotItem.class) {
            ((NotItem) root).addNegativeItem(item);
        } else {
            NotItem newRoot = new NotItem();
            newRoot.addPositiveItem(root);
            newRoot.addNegativeItem(item);
            queryTree.setRoot(newRoot);
        }
    }

    private void addRankItems(Language language, Query query, Set<QueryPart> pieces, IndexFacts.Session session, Execution.Context context) {
        for (QueryPart part : pieces) {
            if (part.operator != Combinator.PREFERRED) continue;

            String defaultIndex = defaultIndex(session, part);
            Item item = parse(language, query, part, defaultIndex, context);
            if (item == null) continue;

            setDefaultIndex(part, defaultIndex, item);
            addRankItem(query.getModel().getQueryTree(), item);
        }
    }

    private void addRankItem(QueryTree queryTree, Item item) {
        Item root = queryTree.getRoot();
        // JavaDoc claims I can get null, code gives NullItem... well, well, well...
        if (root instanceof NullItem || root == null) {
            queryTree.setRoot(item);
        } else if (root.getClass() == RankItem.class) {
            // if no clear recall terms, just set the rank term as recall
            ((RankItem) root).addItem(item);
        } else {
            RankItem newRoot = new RankItem();
            newRoot.addItem(root);
            newRoot.addItem(item);
            queryTree.setRoot(newRoot);
        }
    }

    private void addAndItems(Language language, Query query, Iterable<QueryPart> pieces, IndexFacts.Session session, Execution.Context context) {
        for (QueryPart part : pieces) {
            if (part.operator != Combinator.REQUIRED) continue;

            String defaultIndex = defaultIndex(session, part);
            Item item = parse(language, query, part, defaultIndex, context);
            if (item == null) continue;

            setDefaultIndex(part, defaultIndex, item);
            addAndItem(query.getModel().getQueryTree(), item);
        }
    }

    private void setDefaultIndex(QueryPart part, String defaultIndex, Item item) {
        if (defaultIndex == null) {
            assignDefaultIndex(item, part.defaultIndex);
        }
    }

    private Item parse(Language language, Query query, QueryPart part, String defaultIndex, Execution.Context context) {
        Item item = null;
        try {
            CustomParser parser = (CustomParser)ParserFactory.newInstance(
                                               part.syntax, ParserEnvironment.fromExecutionContext(context));
            item = parser.parse(part.query, null, language, query.getModel().getSources(),
                                context.getIndexFacts(), defaultIndex);
        } catch (RuntimeException e) {
            String err = Exceptions.toMessageString(e);
            query.trace("Query parser threw an exception: " + err, true, 1);
            getLogger().log(LogLevel.WARNING,
                    "Query parser threw exception in searcher LegacyCombinator for "
                    + query.getHttpRequest().toString() + ", query part " + part.query + ": " + err);
        }
        return item;
    }

    private String defaultIndex(IndexFacts.Session indexFacts, QueryPart part) {
        String defaultIndex;
        if (indexFacts.getIndex(part.defaultIndex) == Index.nullIndex) {
            defaultIndex = null;
        } else {
            defaultIndex = part.defaultIndex;
        }
        return defaultIndex;
    }

    private static void addAndItem(QueryTree queryTree, Item item) {
        Item root = queryTree.getRoot();
        // JavaDoc claims I can get null, code gives NullItem... well, well, well...
        if (root instanceof NullItem || root == null) {
            queryTree.setRoot(item);
        } else if (root.getClass() == AndItem.class) {
            ((AndItem) root).addItem(item);
        } else {
            AndItem newRoot = new AndItem();
            newRoot.addItem(root);
            newRoot.addItem(item);
            queryTree.setRoot(newRoot);
        }
    }

    private static void assignDefaultIndex(Item item, String defaultIndex) {
        if (item instanceof IndexedItem) {
            IndexedItem indexName = (IndexedItem) item;

            if ("".equals(indexName.getIndexName())) {
                indexName.setIndexName(defaultIndex);
            }
        } else if (item instanceof CompositeItem) {
            Iterator<Item> items = ((CompositeItem) item).getItemIterator();
            while (items.hasNext()) {
                Item i = items.next();
                assignDefaultIndex(i, defaultIndex);
            }
        }

    }

    private static Set<QueryPart> findQuerySnippets(Properties properties) {
        Set<QueryPart> pieces = new HashSet<>();
        for (Map.Entry<String, Object> k : properties.listProperties()
                .entrySet()) {
            String key = k.getKey();
            if (!key.startsWith(QUERYPREFIX)) {
                continue;
            }
            String name = key.substring(QUERYPREFIX.length());
            if (hasDots(name)) {
                continue;
            }
            String index = properties.getString(DEFIDXPREFIX + name);
            String oldIndex = properties.getString(QUERYPREFIX + name
                    + DEFIDXSUFFIX);
            String operator = properties.getString(QUERYPREFIX + name
                    + OPERATORSUFFIX);
            String type = properties.getString(QUERYPREFIX + name + TYPESUFFIX);
            pieces.add(new QueryPart(name, index, oldIndex, operator, k
                    .getValue().toString(), type));
        }
        return pieces;
    }

    private static boolean hasDots(String name) {
        int index = name.indexOf('.', 0);
        return index != -1;
    }

}
