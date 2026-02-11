// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.engine.resolvers;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.pagetemplates.engine.Resolution;
import com.yahoo.search.pagetemplates.engine.Resolver;
import com.yahoo.search.pagetemplates.model.Choice;
import com.yahoo.search.pagetemplates.model.MapChoice;
import com.yahoo.search.pagetemplates.model.PageElement;

import java.util.*;

/**
 * A resolver which makes all choices by random.
 * The id of this is <code>native.random</code>.
 *
 * @author bratseth
 */
public class RandomResolver extends Resolver {

    public static final String nativeId = "native.random";

    private Random random = new Random(System.currentTimeMillis()); // Use of this is multithread safe

    public RandomResolver() {}

    protected RandomResolver(String id) {
        super(id);
    }

    /** Chooses the last alternative of any choice */
    @Override
    public void resolve(Choice choice, Query query, Result result, Resolution resolution) {
        resolution.addChoiceResolution(choice,random.nextInt(choice.alternatives().size()));
    }

    /** Chooses a mapping which is always by the literal order given in the source template */
    @Override
    public void resolve(MapChoice choice,Query query,Result result,Resolution resolution) {
        Map<String, List<PageElement>> mapping=new HashMap<>();
        // Draw a random element from the value list on each iteration and assign it to a placeholder
        List<String> placeholderIds=choice.placeholderIds();
        List<List<PageElement>> valueList=new ArrayList<>(choice.values());
        for (String placeholderId : placeholderIds)
            mapping.put(placeholderId,valueList.remove(random.nextInt(valueList.size())));
        resolution.addMapChoiceResolution(choice,mapping);
    }

}
