// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher;

import java.util.*;

import com.yahoo.component.ComponentId;
import com.yahoo.search.result.Hit;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.hitfield.FieldPart;
import com.yahoo.prelude.hitfield.HitField;
import com.yahoo.prelude.hitfield.ImmutableFieldPart;
import com.yahoo.prelude.hitfield.StringFieldPart;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;

/**
 * A searcher which does quoting based on a quoting table.
 *
 * May be extended to do quoting template sensitive.
 *
 * @author Steinar Knutsen
 */
public class QuotingSearcher extends Searcher {

    // Char to String
    private QuoteTable quoteTable;

    private synchronized void setQuoteTable(QuoteTable quoteTable) {
        this.quoteTable = quoteTable;
    }
    private synchronized QuoteTable getQuoteTable() {
        return quoteTable;
    }

    private static class QuoteTable {

        private final int lowerUncachedBound;
        private final int upperUncachedBound;
        private final Map<Character, String> quoteMap;
        private final String[] lowerTable;
        private final boolean useMap;
        private final boolean isEmpty;

        public QuoteTable(QrQuotetableConfig config) {
            int minOrd = 0;
            int maxOrd = 0;
            String[] newLowerTable = new String[256];
            boolean newUseMap = false;
            boolean newIsEmpty = true;
            Map<Character, String> newQuoteMap = new HashMap<>();
            for (Iterator<?> i = config.character().iterator(); i.hasNext(); ) {
                QrQuotetableConfig.Character character = (QrQuotetableConfig.Character)i.next();
                if (character.ordinal() > 256) {
                    newIsEmpty = false;
                    newQuoteMap.put(new Character((char)character.ordinal()), character.quoting());
                    newUseMap = true;
                    if (minOrd == 0 || character.ordinal() < minOrd)
                        minOrd = character.ordinal();
                    if (maxOrd == 0 || character.ordinal() > maxOrd)
                        maxOrd = character.ordinal();
                }
                else {
                    newIsEmpty = false;
                    newLowerTable[character.ordinal()] = character.quoting();
                }
            }
            lowerUncachedBound = minOrd;
            upperUncachedBound = maxOrd;
            quoteMap = newQuoteMap;
            useMap = newUseMap;
            isEmpty = newIsEmpty;
            lowerTable = newLowerTable;
        }

        public String get(char c) {
            if (isEmpty) return null;

            int ord = (int)c;
            if (ord < 256) {
                return lowerTable[ord];
            }
            else {
                if ((!useMap) || ord < lowerUncachedBound || ord > upperUncachedBound)
                    return null;
                else
                    return quoteMap.get(new Character(c));
            }
        }
        public boolean isEmpty() {
            return isEmpty;
        }
    }

    public QuotingSearcher(ComponentId id, QrQuotetableConfig config) {
        super(id);
        setQuoteTable(new QuoteTable(config));
    }

    public Result search(Query query, Execution execution) {
        Result result = execution.search(query);
        execution.fill(result);
        QuoteTable translations = getQuoteTable();
        if (translations == null || translations.isEmpty()) return result;

        for (Iterator<Hit> i = result.hits().deepIterator(); i.hasNext(); ) {
            Hit h = i.next();
            if (h instanceof FastHit)
                quoteFields((FastHit) h, translations);
        }
        return result;
    }

    private void quoteFields(FastHit hit, QuoteTable translations) {
        hit.forEachField((fieldName, fieldValue) -> {
            if (fieldValue != null) {
                Class<?> fieldType = fieldValue.getClass();
                if (fieldType.equals(HitField.class))
                    quoteField((HitField) fieldValue, translations);
                else if (fieldType.equals(String.class))
                    quoteField(hit, fieldName, (String) fieldValue, translations);
            }
        });
    }

    private void quoteField(Hit hit, String fieldname, String toQuote, QuoteTable translations) {
        List<FieldPart> l = translate(toQuote, translations, true);
        if (l != null) {
            HitField hf = new HitField(fieldname, toQuote);
            hf.setTokenizedContent(l);
            hit.setField(fieldname, hf);
        }
    }

    private void quoteField(HitField field, QuoteTable translations) {
        for (ListIterator<FieldPart> i = field.listIterator(); i.hasNext(); ) {
            FieldPart f = i.next();
            if ( ! f.isFinal()) {
                List<FieldPart> newFieldParts = translate(f.getContent(), translations, f.isToken());
                if (newFieldParts != null) {
                    i.remove();
                    for (Iterator<FieldPart> j = newFieldParts.iterator(); j.hasNext(); ) {
                        i.add(j.next());
                    }
                }
            }
        }
    }

    private List<FieldPart> translate(String toQuote, QuoteTable translations, boolean isToken) {
        List<FieldPart> newFieldParts = null;
        int lastIdx = 0;
        for (int i = 0; i < toQuote.length(); i++) {
            String quote = translations.get(toQuote.charAt(i));
            if (quote != null) {
                if (newFieldParts == null)
                    newFieldParts = new ArrayList<>();
                if (lastIdx != i)
                    newFieldParts.add(new StringFieldPart(toQuote.substring(lastIdx, i), isToken));
                String initContent = Character.toString(toQuote.charAt(i));
                newFieldParts.add(new ImmutableFieldPart(initContent, quote, isToken));
                lastIdx = i+1;
            }
        }
        if (lastIdx > 0 && lastIdx < toQuote.length())
            newFieldParts.add(new StringFieldPart(toQuote.substring(lastIdx), isToken));
        return newFieldParts;
    }

}
