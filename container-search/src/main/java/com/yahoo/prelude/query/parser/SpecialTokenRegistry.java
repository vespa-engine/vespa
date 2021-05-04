// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser;

import com.yahoo.vespa.configdefinition.SpecialtokensConfig;
import com.yahoo.vespa.configdefinition.SpecialtokensConfig.Tokenlist;
import com.yahoo.vespa.configdefinition.SpecialtokensConfig.Tokenlist.Tokens;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A registry which is responsible for knowing the current
 * set of special tokens.Usage of this registry is multithread safe.
 *
 * @author bratseth
 */
public class SpecialTokenRegistry {

    /**
     * The current special token lists, indexed on name.
     * These lists are unmodifiable and used directly by clients of this
     */
    private Map<String, SpecialTokens> specialTokenMap;

    private boolean frozen = false;

    /** Creates an empty special token registry */
    public SpecialTokenRegistry() {
        this(List.of());
    }

    /** Create a special token registry from a configuration object. */
    public SpecialTokenRegistry(SpecialtokensConfig config) {
        this(specialTokensFrom(config));
    }

    public SpecialTokenRegistry(List<SpecialTokens> specialTokensList) {
        specialTokenMap = specialTokensList.stream().collect(Collectors.toMap(t -> t.name(), t -> t));
        freeze();
    }

    private void freeze() {
        frozen = true;
    }

    private static List<SpecialTokens> specialTokensFrom(SpecialtokensConfig config) {
        List<SpecialTokens> specialTokensList = new ArrayList<>();
        for (Iterator<Tokenlist> i = config.tokenlist().iterator(); i.hasNext();) {
            Tokenlist tokenListConfig = i.next();

            List<SpecialTokens.Token> tokenList = new ArrayList<>();
            for (Iterator<Tokens> j = tokenListConfig.tokens().iterator(); j.hasNext();) {
                Tokens tokenConfig = j.next();
                tokenList.add(new SpecialTokens.Token(tokenConfig.token(), tokenConfig.replace()));
            }
            specialTokensList.add(new SpecialTokens(tokenListConfig.name(), tokenList));
        }
        return specialTokensList;
    }

    private void ensureNotFrozen() {
        if (frozen) {
            throw new IllegalStateException("Tried to modify a frozen SpecialTokenRegistry instance.");
        }
    }

    /**
     * Returns the list of special tokens for a given name.
     *
     * @param  name the name of the special tokens to return
     *         null, the empty string or the string "default" returns
     *         the default ones
     * @return a read-only list of SpecialToken instances, an empty list if this name
     *         has no special tokens
     */
    public SpecialTokens getSpecialTokens(String name) {
        if (name == null || name.trim().equals(""))
            name = "default";
        return specialTokenMap.getOrDefault(name, SpecialTokens.empty());
    }

}
