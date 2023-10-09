// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import com.yahoo.vespa.configdefinition.SpecialtokensConfig;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class SpecialTokensTestCase {

    @Test
    public void testSpecialTokensConfig() {
        var builder = new SpecialtokensConfig.Builder();
        var tokenBuilder = new SpecialtokensConfig.Tokenlist.Builder();
        tokenBuilder.name("default");

        var tokenListBuilder1 = new SpecialtokensConfig.Tokenlist.Tokens.Builder();
        tokenListBuilder1.token("c++");
        tokenListBuilder1.replace("cpp");
        tokenBuilder.tokens(tokenListBuilder1);

        var tokenListBuilder2 = new SpecialtokensConfig.Tokenlist.Tokens.Builder();
        tokenListBuilder2.token("...");
        tokenBuilder.tokens(tokenListBuilder2);

        builder.tokenlist(tokenBuilder);

        var registry = new SpecialTokenRegistry(builder.build());

        var defaultTokens = registry.getSpecialTokens("default");
        assertEquals("default", defaultTokens.name());
        assertEquals(2, defaultTokens.asMap().size());
        assertEquals("cpp", defaultTokens.asMap().get("c++"));
        assertEquals("...", defaultTokens.asMap().get("..."));
    }

}
