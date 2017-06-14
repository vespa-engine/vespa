// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.debug;

import static com.yahoo.protect.Validator.ensureNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.yahoo.component.provider.ComponentRegistry;
import org.apache.commons.collections.CollectionUtils;

import com.yahoo.container.Container;
import com.yahoo.jrt.Request;
import com.yahoo.prelude.cluster.ClusterSearcher;
import com.yahoo.search.Searcher;
import com.yahoo.search.handler.SearchHandler;
import com.yahoo.search.searchchain.SearchChainRegistry;

/**
 * Utility functions for searchers and search chains.
 *
 * @author tonytv
 */
final class SearcherUtils {
    private static Collection<Searcher> allSearchers() {
        SearchChainRegistry searchChainRegistry = getSearchHandler().getSearchChainRegistry();
        ComponentRegistry<Searcher> searcherRegistry = searchChainRegistry.getSearcherRegistry();
        return searcherRegistry.allComponents();
    }

    private static Collection<ClusterSearcher> allClusterSearchers() {
        return filter(allSearchers(), ClusterSearcher.class);
    }

    private static <T> Collection<T> filter(Collection<?> collection, Class<T> classToMatch) {
        List<T> filtered = new ArrayList<>();
        for (Object candidate : collection) {
            if (classToMatch.isInstance(candidate))
                filtered.add(classToMatch.cast(candidate));
        }
        return filtered;
    }

    public static Collection<ClusterSearcher> clusterSearchers(final String clusterName) {
        Collection<ClusterSearcher> searchers = allClusterSearchers();
        CollectionUtils.filter(searchers,
                o -> clusterName.equalsIgnoreCase(((ClusterSearcher)o).getClusterModelName()));
        return searchers;
    }

    //Return value is never null
    static SearchHandler getSearchHandler() {
        SearchHandler searchHandler = (SearchHandler) Container.get().getRequestHandlerRegistry().getComponent("com.yahoo.search.handler.SearchHandler");
        ensureNotNull("The standard search handler is not available.", searchHandler);
        return searchHandler;
    }

    //Retrieve all the cluster searchers as specified by the first parameter of the request.
    static Collection<ClusterSearcher> clusterSearchers(Request request) {
        String clusterName = request.parameters().get(0).asString();
        Collection<ClusterSearcher> searchers = clusterSearchers(clusterName);
        if (searchers.isEmpty())
            throw new RuntimeException("No cluster named " + clusterName);
        return searchers;
    }
}
