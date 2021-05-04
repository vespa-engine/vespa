// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser;

import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.vespa.configdefinition.SpecialtokensConfig;
import com.yahoo.vespa.configdefinition.SpecialtokensConfig.Tokenlist;
import com.yahoo.vespa.configdefinition.SpecialtokensConfig.Tokenlist.Tokens;

import java.util.*;
import java.util.logging.Logger;


/**
 * A <i>registry</i> which is responsible for knowing the current
 * set of special tokens. The default registry returns empty token lists
 * for all names. Usage of this registry is multithread safe.
 *
 * @author bratseth
 */
public class SpecialTokenRegistry {

    /** The log of this */
    private static final Logger log = Logger.getLogger(SpecialTokenRegistry.class.getName());

    private static final SpecialTokens nullSpecialTokens = new SpecialTokens();

    /**
     * The current authorative special token lists, indexed on name.
     * These lists are unmodifiable and used directly by clients of this
     */
    private Map<String,SpecialTokens> specialTokenMap = new HashMap<>();

    private boolean frozen = false;

    /**
     * Creates an empty special token registry which
     * does not subscribe to any configuration
     */
    public SpecialTokenRegistry() {}

    /**
     * Create a special token registry which subscribes to the specialtokens
     * configuration. Only used for testing.
     */
    public SpecialTokenRegistry(String configId) {
        try {
            build(new ConfigGetter<>(SpecialtokensConfig.class).getConfig(configId));
        } catch (Exception e) {
            log.config(
                    "No special tokens are configured (" + e.getMessage() + ")");
        }
    }

    /**
     * Create a special token registry from a configuration object. This is the production code path.
     */
    public SpecialTokenRegistry(SpecialtokensConfig config) {
        if (config != null) {
            build(config);
        }
        freeze();
    }

    private void freeze() {
        frozen = true;
    }

    private void build(SpecialtokensConfig config) {
        List<SpecialTokens> list = new ArrayList<>();
        for (Iterator<Tokenlist> i = config.tokenlist().iterator(); i.hasNext();) {
            Tokenlist tokenList = i.next();
            SpecialTokens tokens = new SpecialTokens(tokenList.name());

            for (Iterator<Tokens> j = tokenList.tokens().iterator(); j.hasNext();) {
                Tokens token = j.next();
                tokens.addSpecialToken(token.token(), token.replace());
            }
            tokens.freeze();
            list.add(tokens);
        }
        addSpecialTokens(list);
    }

    /**
     * Adds a SpecialTokens instance to the registry. That is, add the
     * tokens contained for the name of the SpecialTokens instance
     * given.
     *
     * @param specialTokens the SpecialTokens object to add
     */
    public void addSpecialTokens(SpecialTokens specialTokens) {
        ensureNotFrozen();
        List<SpecialTokens> list = new ArrayList<>();
        list.add(specialTokens);
        addSpecialTokens(list);

    }

    private void ensureNotFrozen() {
        if (frozen) {
            throw new IllegalStateException("Tried to modify a frozen SpecialTokenRegistry instance.");
        }
    }

    private void addSpecialTokens(List<SpecialTokens> list) {
        HashMap<String,SpecialTokens> tokens = new HashMap<>(specialTokenMap);
        for(SpecialTokens t: list) {
            tokens.put(t.getName(),t);
        }
        specialTokenMap = tokens;
    }


    /**
     * Returns the currently authorative list of special tokens for
     * a given name.
     *
     * @param  name the name of the special tokens to return
     *         null, the empth string or the string "default" returns
     *         the default ones
     * @return a read-only list of SpecialToken instances, an empty list if this name
     *         has no special tokens
     */
    public SpecialTokens getSpecialTokens(String name) {
        if (name == null || name.trim().equals("")) {
            name = "default";
        }
        SpecialTokens specialTokens = specialTokenMap.get(name);

        if (specialTokens == null) {
            return nullSpecialTokens;
        }
        return specialTokens;
    }

}
