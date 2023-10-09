// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.engine.resolvers;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.pagetemplates.engine.Resolution;
import com.yahoo.search.pagetemplates.engine.Resolver;
import com.yahoo.search.pagetemplates.model.Choice;
import com.yahoo.search.pagetemplates.model.MapChoice;
import com.yahoo.search.pagetemplates.model.PageElement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A resolver which
 * <ul>
 *   <li>Always chooses the <i>last</i> alternative of any Choice
 *   <li>Always maps values to placeholders in the order they are listed in the map definition of any MapChoice
 * </ul>
 * This is useful for testing.
 * <p>
 * The id of this if <code>native.deterministic</code>
 *
 * @author bratseth
 */
public class DeterministicResolver extends Resolver {
    public static final String nativeId = "native.deterministic";

    public DeterministicResolver() {}

    protected DeterministicResolver(String id) {
        super(id);
    }

    /** Chooses the last alternative of any choice */
    @Override
    public void resolve(Choice choice, Query query, Result result, Resolution resolution) {
        resolution.addChoiceResolution(choice,choice.alternatives().size()-1);
    }

    /** Chooses a mapping which is always by the literal order given in the source template */
    @Override
    public void resolve(MapChoice choice,Query query,Result result,Resolution resolution) {
        Map<String, List<PageElement>> mapping=new HashMap<>();
        // Map 1-1 by order
        List<String> placeholderIds=choice.placeholderIds();
        List<List<PageElement>> valueList=choice.values();
        int i=0;
        for (String placeholderId : placeholderIds)
            mapping.put(placeholderId,valueList.get(i++));
        resolution.addMapChoiceResolution(choice,mapping);
    }

}
