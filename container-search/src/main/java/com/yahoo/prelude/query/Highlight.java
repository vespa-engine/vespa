// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.yahoo.language.LinguisticsCase.toLowerCase;

/**
 * Class encapsulating information on extra highlight-terms for a query
 *
 * @author Mathias Lidal
 */
public class Highlight implements Cloneable {

    /** The name of the property map which contains extra highlight terms */
    public static final String HIGHLIGHTTERMS = "highlightterms";

    private Map<String, AndItem> highlightItems = new LinkedHashMap<>();

    private Map<String, List<String>> highlightTerms = new LinkedHashMap<>();

    public Highlight() {}

    private void addHighlightItem(String key, Item value) {
        AndItem item = highlightItems.get(key);
        if (item == null) {
            item = new AndItem();
            highlightItems.put(key, item);
        }
        item.addItem(value);
    }

    /**
     * Add custom highlight term
     *
     * @param field the field name
     * @param item the term to be highlighted
     */
    public void addHighlightTerm(String field, String item) {
        addHighlightItem(field, new WordItem(toLowerCase(item), field, true));
    }

    /**
     * Add custom highlight phrase
     *
     * @param field the field name
     * @param phrase the list of terms to be highlighted as a phrase
     */
    public void addHighlightPhrase(String field, List<String> phrase) {
        PhraseItem pi = new PhraseItem();
        pi.setIndexName(field);
        for (String s : phrase)
            pi.addItem(new WordItem(toLowerCase(s), field, true));
        addHighlightItem(field, pi);
    }

    /** Returns the modifiable map of highlight items (never null) */
    public Map<String, AndItem> getHighlightItems() {
        return highlightItems;
    }

    @Override
    public Highlight clone() {
        try {
            Highlight clone = (Highlight) super.clone();

            clone.highlightItems = new LinkedHashMap<>();
            for (Map.Entry<String,AndItem> entry: highlightItems.entrySet()) {
                clone.highlightItems.put(entry.getKey(),(AndItem)entry.getValue().clone());
            }

            clone.highlightTerms = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : highlightTerms.entrySet())
                clone.highlightTerms.put(entry.getKey(), new ArrayList<>(entry.getValue()));

            return clone;

        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, List<String>> getHighlightTerms() { return highlightTerms; }

    /** Prepares this for binary serialization. For internal use - see {@link com.yahoo.search.Query#prepare} */
    public void prepare() {
        this.highlightTerms.clear();

        for (String index : getHighlightItems().keySet()) {
            AndItem root = getHighlightItems().get(index);
            List<WordItem> words = new ArrayList<>();
            List<CompositeItem> phrases = new ArrayList<>();
            for (Iterator<Item> i = root.getItemIterator(); i.hasNext(); ) {
                Item item = i.next();
                if (item instanceof WordItem) {
                    words.add((WordItem)item);
                } else if (item instanceof CompositeItem) {
                    phrases.add((CompositeItem)item);
                }
            }

            List<String> terms = new ArrayList<>();
            terms.add(String.valueOf(words.size() + phrases.size()));
            for (WordItem item : words) {
                terms.add(item.getWord());
            }

            for (CompositeItem item : phrases) {
                terms.add("\"");
                terms.add(String.valueOf(item.getItemCount()));
                for (Iterator<Item> i = item.getItemIterator(); i.hasNext(); ) {
                    terms.add(((IndexedItem)i.next()).getIndexedString());
                }
                terms.add("\"");
            }

            if (terms.size() > 1)
                this.highlightTerms.put(index, terms);
        }
    }

}




