// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.rewrite;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.yahoo.prelude.query.WeakAndItem;
import org.junit.jupiter.api.Test;
import com.yahoo.prelude.query.AndItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.language.process.SpecialTokenRegistry;
import com.yahoo.search.Query;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.Execution.Context;
import com.yahoo.vespa.configdefinition.SpecialtokensConfig;
import com.yahoo.vespa.configdefinition.SpecialtokensConfig.Tokenlist;
import com.yahoo.vespa.configdefinition.SpecialtokensConfig.Tokenlist.Tokens;

/**
 * Fine grained testing of RewriterFeatures for easier testing of innards.
 */
public class RewriterFeaturesTestCase {

    private static final String ASCII_ELLIPSIS = "...";

    @Test
    final void testConvertStringToQTree() {
        Execution placeholder = new Execution(Context.createContextStub());
        SpecialTokenRegistry tokenRegistry = new SpecialTokenRegistry(
                new SpecialtokensConfig(
                        new SpecialtokensConfig.Builder()
                                .tokenlist(new Tokenlist.Builder().name(
                                        "default").tokens(
                                        new Tokens.Builder().token(ASCII_ELLIPSIS)))));
        placeholder.context().setTokenRegistry(tokenRegistry);
        Query query = new Query();
        query.getModel().setExecution(placeholder);
        Item parsed = RewriterFeatures.convertStringToQTree(query, "a b c "
                + ASCII_ELLIPSIS);
        assertSame(WeakAndItem.class, parsed.getClass());
        assertEquals(4, ((CompositeItem) parsed).getItemCount());
        assertEquals(ASCII_ELLIPSIS, ((CompositeItem) parsed).getItem(3).toString());
    }

}
