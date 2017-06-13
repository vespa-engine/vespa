// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.yahoo.component.ComponentId;
import com.yahoo.language.Language;
import com.yahoo.log.LogLevel;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.parser.CustomParser;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.query.Properties;
import com.yahoo.search.query.QueryTree;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.IndexedItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NullItem;
import com.yahoo.yolean.Exceptions;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.query.parser.ParserFactory;
import com.yahoo.search.searchchain.Execution;

/**
 * <p>A searcher which grabs query parameters of the form "defidx.(identifier)=(index name)" and
 * "query.(identifier)=(user query)", * parses them and adds them as AND items to the query root.</p>
 *
 * <p>If the given default index does not exist in the search definition, the query part will be parsed with the
 * settings of the default index set to the "".</p>
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class QueryCombinator extends Searcher {
    private static final String QUERYPREFIX = "query.";

    private static class QueryPart {
        final String query;
        final String defaultIndex;

        QueryPart(String query, String defaultIndex) {
            this.query = query;
            this.defaultIndex = defaultIndex;
        }
    }

    public QueryCombinator(ComponentId id) {
        super(id);
    }

    @Override
    public Result search(Query query, Execution execution) {
        Set<QueryPart> pieces = findQuerySnippets(query.properties());
        if (pieces.size() == 0) {
            return execution.search(query);
        }
        addAndItems(query, pieces, execution.context());
        query.trace("Adding extra query parts.", true, 2);
        return execution.search(query);
    }

    private void addAndItems(Query query, Iterable<QueryPart> pieces, Execution.Context context) {
        IndexFacts indexFacts = context.getIndexFacts();
        IndexFacts.Session session = indexFacts.newSession(query);
        Set<String> usedSources = new HashSet<>(session.documentTypes());
        Language language = query.getModel().getParsingLanguage();
        for (QueryPart part : pieces) {
            String defaultIndex;
            Item item = null;
            Index index = session.getIndex(part.defaultIndex);
            if (index == Index.nullIndex) {
                defaultIndex = null;
            } else {
                defaultIndex = part.defaultIndex;
            }
            try {
                CustomParser parser = (CustomParser)ParserFactory.newInstance(query.getModel().getType(),
                                                                              ParserEnvironment.fromExecutionContext(context));
                item = parser.parse(part.query, null, language, usedSources, indexFacts, defaultIndex);
            } catch (RuntimeException e) {
                String err = Exceptions.toMessageString(e);
                query.trace("Query parser threw an exception: " + err, true, 1);
                getLogger().log(LogLevel.WARNING,
                        "Query parser threw exception searcher QueryCombinator for "
                        + query.getHttpRequest().toString() + ", query part " + part.query + ": " + err);
            }
            if (item == null) {
                continue;
            }
            if (defaultIndex == null) {
                assignDefaultIndex(item, part.defaultIndex);
            }
            addAndItem(query.getModel().getQueryTree(), item);
        }
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
        for (Map.Entry<String, Object> k : properties.listProperties().entrySet()) {
            String key = k.getKey();
            if (!key.startsWith(QUERYPREFIX)) {
                continue;
            }
            String name = key.substring(QUERYPREFIX.length());
            if (hasDots(name)) {
                continue;
            }
            String index = properties.getString("defidx." + name);
            pieces.add(new QueryPart(k.getValue().toString(), index));
        }
        return pieces;
    }

    private static boolean hasDots(String name) {
        int index = name.indexOf('.', 0);
        return index != -1;
    }
}
