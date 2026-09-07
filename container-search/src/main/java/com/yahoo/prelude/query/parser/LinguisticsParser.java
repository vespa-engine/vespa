// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser;

import com.yahoo.language.Language;
import com.yahoo.language.process.LinguisticsParameters;
import com.yahoo.language.process.StemMode;
import com.yahoo.language.process.TokenType;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.Substring;
import com.yahoo.prelude.query.TermItem;
import com.yahoo.prelude.query.WordAlternativesItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.schema.FieldSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A parser which delegates all tokenization and processing to the linguistics component.
 * The full string is given as-is to the linguistics component for tokenization, and
 * what comes back is assumed fully processed including stemming (if applicable).
 * The returned tokens are collected into a single parent item.
 *
 * @author bratseth
 */
public final class LinguisticsParser extends AbstractParser {

    public LinguisticsParser(ParserEnvironment environment) {
        super(environment);
    }

    @Override
    Item parse(String queryToParse, String filterToParse, Language parsingLanguage,
               IndexFacts.Session indexFacts, String fieldOrFieldSet, Parsable parsable) {
        setState(language, indexFacts, fieldOrFieldSet);
        List<FieldProfile> linguisticProfiles = linguisticProfilesForFieldArgument(fieldOrFieldSet, parsable);
        List<FieldTokens> tokensPerField =
                linguisticProfiles.stream().map(fieldProfile -> new FieldTokens(fieldProfile.fieldOrFieldSet(),
                                                                                tokenize(queryToParse, fieldProfile.profile(), language).iterator())).toList();
        var parent = newComposite();

        // Iterate over the tokens of each resulting tokenization in parallel to create an OR item for each token
        for (List<FieldToken> nextTokens = nextTokens(tokensPerField);
             !nextTokens.isEmpty();
             nextTokens = nextTokens(tokensPerField)) {
            if (nextTokens.size() == 1) {
                parent.addItem(toItem(nextTokens.get(0).token(), nextTokens.get(0).fieldOrFieldSet()));
            }
            else {
                OrItem orOverFields = new OrItem();
                for (FieldToken nextToken : nextTokens)
                    orOverFields.addItem(toItem(nextToken.token(), nextToken.fieldOrFieldSet()));
                parent.addItem(orOverFields);
            }
        }
        return parent;
    }

    private List<FieldToken> nextTokens(List<FieldTokens> tokensPerField) {
        if (tokensPerField.size() == 1) { // Shortcut
            var fieldToken = nextIndexable(tokensPerField.get(0));
            return fieldToken != null ? List.of(fieldToken) : List.of();
        }
        List<FieldToken> next = new ArrayList<>();
        for (FieldTokens fieldTokens : tokensPerField) {
            var fieldToken = nextIndexable(fieldTokens);
            if (fieldToken != null)
                next.add(fieldToken);
        }
        return next;
    }

    private FieldToken nextIndexable(FieldTokens tokens) {
        while (tokens.tokens().hasNext()) {
            var token = tokens.tokens().next();
            if ( ! token.getType().isIndexable()) continue;
            return new FieldToken(tokens.fieldOrFieldSet(), token);
        }
        return null;
    }

    @Override
    protected Item parseItems() {
        throw new RuntimeException(); // Not used since this overrides the parse method to delegate tokenization
    }

    private List<FieldProfile> linguisticProfilesForFieldArgument(String fieldOrFieldSet, Parsable parsable) {
        var schemaInfo = environment.getSchemaInfo().newSession(parsable.getSources(), parsable.getRestrict());
        Optional<FieldSet> fieldSet = schemaInfo.fieldSet(fieldOrFieldSet);
        if (fieldSet.isPresent()) {
            Set<String> profiles = new HashSet<>();
            List<FieldProfile> fieldProfiles = new ArrayList<>();
            for (var field : fieldSet.get().fieldNames()) {
                String profile = linguisticsProfileFor(field);
                profiles.add(profile);
                fieldProfiles.add(new FieldProfile(field, profile));
            }
            if (profiles.size() > 1) // Expand each field in the query
                return fieldProfiles;
        }
        // Search one field, or if this is a fieldset, let the content layer expand
        return List.of(new FieldProfile(fieldOrFieldSet, linguisticsProfileFor(fieldOrFieldSet)));
    }

    private Iterable<com.yahoo.language.process.Token> tokenize(String queryToParse,
                                                                String profile,
                                                                Language parsingLanguage) {
        var parameters = new LinguisticsParameters(profile,
                                                   parsingLanguage,
                                                   StemMode.BEST,
                                                   true,
                                                   true);
        return environment.getLinguistics().getTokenizer().tokenize(queryToParse, parameters);
    }

    private Item toItem(com.yahoo.language.process.Token token, String index) {
        TermItem item;
        if (token.getType() == TokenType.NUMERIC) {
            item = new IntItem(token.getTokenString());
        }
        else if (token.getNumStems() == 1) {
            WordItem word = new WordItem(token.getTokenString());
            word.setStemmed(true); // Disable downstream stemming
            word.setNormalizable(false); // Disable downstream normalizing
            word.setLowercased(true); // Disable downstream lowercasing
            item = word;
        }
        else {
            List<WordAlternativesItem.Alternative> alternatives = new ArrayList<>();
            for (int i = 0; i < token.getNumStems(); i++)
                alternatives.add(new WordAlternativesItem.Alternative(token.getStem(i), 1.0));
            item = new WordAlternativesItem(index, true, new Substring(token.getOrig()), alternatives);
            item.setQueryType(environment.getType());
            item.setNormalizable(false); // Disable downstream normalizing
            item.setLowercased(true); // Disable downstream lowercasing
        }
        item.setIndexName(index);
        item.setQueryType(environment.getType());
        return item;
    }

    private record FieldProfile(String fieldOrFieldSet, String profile) {}
    private record FieldTokens(String fieldOrFieldSet, Iterator<com.yahoo.language.process.Token> tokens) {}
    private record FieldToken(String fieldOrFieldSet, com.yahoo.language.process.Token token) {}

}
