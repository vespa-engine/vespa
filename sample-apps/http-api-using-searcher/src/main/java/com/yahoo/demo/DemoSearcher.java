// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.demo;

import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.demo.DemoConfig.Demo;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;

import java.util.List;

/**
 * A searcher for adding a set of configured terms as AND terms, and add a
 * single term from the request to the query tree (after running the term
 * through a shared component).
 */
@After(PhaseNames.RAW_QUERY)
@Before(PhaseNames.TRANSFORMED_QUERY)
@Provides(DemoSearcher.DEMO_TRANSFORM)
public class DemoSearcher extends Searcher {
    public static final String DEMO_TRANSFORM = "com.yahoo.demo.DemoSearcher.NothingUseful";

    /**
     * The request property with this name will be filtered and added to the
     * query as an AND term.
     */
    public static final CompoundName EXTRA_TERM = new CompoundName("extraTerm");

    private final List<Demo> extraTerms;

    private final DemoComponent infrastructure;

    public DemoSearcher(DemoComponent infrastructure, DemoConfig extraTerms) {
        this.extraTerms = extraTerms.demo();
        this.infrastructure = infrastructure;
    }

    /**
     * Programmatic query transform, add terms from config and the EXTRA_TERM
     * request property.
     */
    @Override
    public Result search(Query query, Execution execution) {
        QueryTree q = query.getModel().getQueryTree();
        addAndItem(q, infrastructure.normalize(
                query.properties().getString(EXTRA_TERM)));
        for (Demo d : extraTerms) {
            addAndItem(q, d.term());
        }
        return execution.search(query);
    }

    private void addAndItem(QueryTree q, String term) {
        Item root = q.getRoot();
        CompositeItem compositeRoot;
        if (root instanceof AndItem) {
            compositeRoot = (CompositeItem) root;
        } else {
            compositeRoot = new AndItem();
            compositeRoot.addItem(root);
            q.setRoot(compositeRoot);
        }
        compositeRoot.addItem(new WordItem(term));
    }

}
