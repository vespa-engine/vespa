// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.querytransform;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.StemMode;
import com.yahoo.language.process.StemList;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.AndSegmentItem;
import com.yahoo.prelude.query.BlockItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Highlight;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.PhraseSegmentItem;
import com.yahoo.prelude.query.PrefixItem;
import com.yahoo.prelude.query.SegmentingRule;
import com.yahoo.prelude.query.Substring;
import com.yahoo.prelude.query.TaggableItem;
import com.yahoo.prelude.query.TermItem;
import com.yahoo.prelude.query.WordAlternativesItem;
import com.yahoo.prelude.query.WordAlternativesItem.Alternative;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;


import static com.yahoo.prelude.querytransform.CJKSearcher.TERM_ORDER_RELAXATION;


/**
 * Replaces query terms with their stems
 *
 * @author Mathias Lidal
 * @author bratseth
 * @author Steinar Knutsen
 */
@After({PhaseNames.UNBLENDED_RESULT, TERM_ORDER_RELAXATION})
@Provides(StemmingSearcher.STEMMING)
public class StemmingSearcher extends Searcher {

    private static class StemContext {
        public boolean isCJK = false;
        public boolean insidePhrase = false;
        public Language language = null;
        public IndexFacts.Session indexFacts = null;
        public Map<Item, TaggableItem> reverseConnectivity = null;
    }

    public static final String STEMMING = "Stemming";
    public static final CompoundName DISABLE = CompoundName.from("nostemming");
    private final Linguistics linguistics;

    public StemmingSearcher(Linguistics linguistics) {
        this.linguistics = linguistics;
    }

    @Inject
    public StemmingSearcher(ComponentId id, Linguistics linguistics) {
        super(id);
        this.linguistics = linguistics;
    }

    @Override
    public Result search(Query query, Execution execution) {
        if (query.properties().getBoolean(DISABLE)) return execution.search(query);

        IndexFacts.Session indexFacts = execution.context().getIndexFacts().newSession(query);
        Item newRoot = replaceTerms(query, indexFacts);
        query.getModel().getQueryTree().setRoot(newRoot);

        query.trace(getFunctionName(), true, 2);

        Highlight highlight = query.getPresentation().getHighlight();
        if (highlight != null) {
            Set<String> highlightFields = highlight.getHighlightItems().keySet();
            for (String field : highlightFields) {
                StemMode stemMode = indexFacts.getIndex(field).getStemMode();
                if (stemMode != StemMode.NONE) {
                    StemContext context = new StemContext();
                    context.language = Language.ENGLISH;
                    context.indexFacts = indexFacts;
                    Item newHighlight = scan(highlight.getHighlightItems().get(field), context);
                    highlight.getHighlightItems().put(field, (AndItem)newHighlight);
                }
            }
        }
        return execution.search(query);
    }

    public String getFunctionName() { return "Stemming"; }

    private Item replaceTerms(Query q, IndexFacts.Session indexFacts) {
        Language language = q.getModel().getParsingLanguage();
        if (language == Language.UNKNOWN) {
            return q.getModel().getQueryTree().getRoot();
        }
        StemContext context = new StemContext();
        context.isCJK = language.isCjk();
        context.language = language;
        context.indexFacts = indexFacts;
        context.reverseConnectivity = createReverseConnectivities(q.getModel().getQueryTree().getRoot());
        q.trace("Stemming with language="+language, 3);
        return scan(q.getModel().getQueryTree().getRoot(), context);
    }

    private Map<Item, TaggableItem> createReverseConnectivities(Item root) {
        return populateReverseConnectivityMap(root, new IdentityHashMap<>());
    }

    private Map<Item, TaggableItem> populateReverseConnectivityMap(Item root, Map<Item, TaggableItem> reverseConnectivity) {
        if (root instanceof TaggableItem asTaggable) {
            Item connectsTo = asTaggable.getConnectedItem();
            if (connectsTo != null) {
                reverseConnectivity.put(connectsTo, asTaggable);
            }
        }
        if (root instanceof CompositeItem c && !(root instanceof BlockItem)) {
            for (Iterator<Item> i = c.getItemIterator(); i.hasNext();) {
                Item item = i.next();
                populateReverseConnectivityMap(item, reverseConnectivity);
            }
        }
        return reverseConnectivity;
    }

    private Item scan(Item item, StemContext context) {
        if (item == null) {
            return null;
        }
        boolean old = context.insidePhrase;
        if (item instanceof PhraseItem || item instanceof PhraseSegmentItem) {
            context.insidePhrase = true;
        }
        if (item instanceof BlockItem) {
            item = checkBlock((BlockItem) item, context);
        } else if (item instanceof CompositeItem comp) {
            ListIterator<Item> i = comp.getItemIterator();

            while (i.hasNext()) {
                Item original = i.next();
                Item transformed = scan(original, context);
                if (original != transformed)
                    i.set(transformed);
            }
        }
        context.insidePhrase = old;
        return item;
    }

    private Item checkBlock(BlockItem b, StemContext context) {
        if (b instanceof PrefixItem || !b.isWords()) return (Item) b;

        if (b.isFromQuery() && !b.isStemmed()) {
            Index index = context.indexFacts.getIndex(b.getIndexName());
            StemMode stemMode = index.getStemMode();
            if (stemMode != StemMode.NONE) return stem(b, context, index);
        }
        return (Item) b;
    }

    private Substring getOffsets(BlockItem b) {
        if (b instanceof TermItem) {
            return b.getOrigin();
        } else if (b instanceof CompositeItem) {
            Item i = ((CompositeItem) b).getItem(0);
            if (i instanceof TermItem) {
                return ((TermItem) i).getOrigin(); // this should always be the case
            } else {
                getLogger().log(Level.WARNING, "Weird, BlockItem '" + b + "' was a composite containing " + 
                                                  i.getClass().getName() + ", expected TermItem.");
            }
        }
        return null;
    }

    // The rewriting logic is here
    private Item stem(BlockItem current, StemContext context, Index index) {
        Item blockAsItem = (Item)current;
        CompositeItem composite;
        List<StemList> segments = linguistics.getStemmer().stem(current.stringValue(), index.getStemMode(), context.language);
        String indexName = current.getIndexName();
        Substring substring = getOffsets(current);

        if (segments.size() == 1) {
            getLogger().log(Level.FINE, () -> "Stem '"+current.stringValue()+"' mode "+index.getStemMode()
                            +" and language '"+context.language+"' -> '"+segments.get(0)+"'");
            TaggableItem w = singleWordSegment(current, segments.get(0), index, substring, context.insidePhrase);
            setMetaData(current, context.reverseConnectivity, w);
            return (Item) w;
        } else if (getLogger().isLoggable(Level.FINE)) {
            var buf = new StringBuilder();
            buf.append("Stem '").append(current.stringValue());
            buf.append("' mode ").append(index.getStemMode());
            buf.append(" and language '").append(context.language).append("' ->");
            for (StemList segment : segments) {
                buf.append(" '").append(segment).append("'");
            }
            getLogger().log(Level.FINE, buf.toString());
        }

        if (context.isCJK)
            composite = chooseCompositeForCJK(current, ((Item) current).getParent(), indexName);
        else
            composite = chooseComposite(current, ((Item) current).getParent(), indexName);

        for (StemList segment : segments) {
            getLogger().log(Level.FINE, () -> "Stem to multiple segments '"+segment+"'");
            TaggableItem w = singleWordSegment(current, segment, index, substring, context.insidePhrase);

            if (composite instanceof AndSegmentItem) {
                setSignificance(w, current);
            }
            composite.addItem((Item) w);
        }
        if (composite instanceof AndSegmentItem) {
            andSegmentConnectivity(current, context.reverseConnectivity, composite);
        }
        copyAttributes(blockAsItem, composite);
        composite.lock();

        if (composite instanceof PhraseSegmentItem replacement) {
            setSignificance(replacement, current);
            phraseSegmentConnectivity(current, context.reverseConnectivity, replacement);
        }

        return composite;
    }

    private void phraseSegmentConnectivity(BlockItem current, Map<Item, TaggableItem> reverseConnectivity,
                                           PhraseSegmentItem replacement) {
        Connectivity c = getConnectivity(current);
        if (c != null) {
            replacement.setConnectivity(c.word, c.value);
            reverseConnectivity.put(c.word, replacement);
        }
        setConnectivity(current, reverseConnectivity, replacement);
    }

    private void andSegmentConnectivity(BlockItem current, Map<Item, TaggableItem> reverseConnectivity, 
                                        CompositeItem composite) {
        // if the original has connectivity to something, add to last word
        Connectivity connectivity = getConnectivity(current);
        if (connectivity != null) {
            TaggableItem w = lastWord(composite);
            if (w != null) {
                w.setConnectivity(connectivity.word, connectivity.value);
                reverseConnectivity.put(connectivity.word, w);
            }
        }
        // If we create an AND from something taggable, add connectivity to the first word
        TaggableItem w = firstWord(composite);
        if (w != null) {
            setConnectivity(current, reverseConnectivity, (Item) w);
        }
    }

    private Connectivity getConnectivity(BlockItem current) {
        if (!(current instanceof TaggableItem t)) {
            return null;
        }
        if (t.getConnectedItem() == null) {
            return null;
        }
        return new Connectivity(t.getConnectedItem(), t.getConnectivity());
    }

    private TaggableItem firstWord(CompositeItem composite) {
        // yes, this assumes only WordItem instances in the CompositeItem
        int l = composite.getItemCount();
        if (l == 0) {
            return null;
        } else {
            return (TaggableItem) composite.getItem(0);
        }
    }

    private TaggableItem lastWord(CompositeItem composite) {
        // yes, this assumes only WordItem instances in the CompositeItem
        int l = composite.getItemCount();
        if (l == 0) {
            return null;
        } else {
            return (TaggableItem) composite.getItem(l - 1);
        }
    }

    private TaggableItem singleWordSegment(BlockItem current,
                                           StemList segment,
                                           Index index,
                                           Substring substring,
                                           boolean insidePhrase) {
        String indexName = current.getIndexName();
        if (!insidePhrase && ((index.getLiteralBoost() || index.getStemMode() == StemMode.ALL))) {
            List<Alternative> terms = new ArrayList<>(segment.size() + 1);
            terms.add(new Alternative(current.stringValue(), 1.0d));
            for (String term : segment) {
                terms.add(new Alternative(term, 0.7d));
            }
            WordAlternativesItem alternatives = new WordAlternativesItem(indexName, current.isFromQuery(), substring, terms);
            if (alternatives.getAlternatives().size() > 1) {
                return alternatives;
            }
        }
        return singleStemSegment((Item) current, segment.get(0), indexName, substring);
    }

    private void setMetaData(BlockItem current, Map<Item, TaggableItem> reverseConnectivity, TaggableItem replacement) {
        copyAttributes((Item) current, (Item) replacement);
        setSignificance(replacement, current);
        Connectivity c = getConnectivity(current);
        if (c != null) {
            replacement.setConnectivity(c.word, c.value);
            reverseConnectivity.put(c.word, replacement);
        }
        setConnectivity(current, reverseConnectivity, (Item) replacement);
    }

    private WordItem singleStemSegment(Item blockAsItem, String stem, String indexName,
                                       Substring substring) {
        WordItem replacement = new WordItem(stem, indexName, true, substring);
        replacement.setStemmed(true);
        copyAttributes(blockAsItem, replacement);
        return replacement;
    }

    private void setConnectivity(BlockItem current,
                                 Map<Item, TaggableItem> reverseConnectivity,
                                 Item replacement) {
        if (reverseConnectivity != null && !reverseConnectivity.isEmpty()) {
            // This Map<Item, TaggableItem>.get(BlockItem) is technically wrong, but the Item API ensures its correctness
            TaggableItem connectedTo = reverseConnectivity.get(current);
            if (connectedTo != null) {
                double connectivity = connectedTo.getConnectivity();
                connectedTo.setConnectivity(replacement, connectivity);
            }
        }
    }

    private CompositeItem chooseComposite(BlockItem current, CompositeItem parent, String indexName) {
        if (parent instanceof PhraseItem || current instanceof PhraseSegmentItem)
            return createPhraseSegment(current, indexName);
        else
            return createAndSegment(current);

    }

    private CompositeItem chooseCompositeForCJK(BlockItem current, CompositeItem parent, String indexName) {
        if (current.getSegmentingRule() == SegmentingRule.LANGUAGE_DEFAULT)
            return chooseComposite(current, parent, indexName);

        return switch (current.getSegmentingRule()) { // TODO: Why for CJK only? The segmentingRule says nothing about being for CJK only
            case PHRASE -> createPhraseSegment(current, indexName);
            case BOOLEAN_AND -> createAndSegment(current);
            default -> throw new IllegalArgumentException("Unknown segmenting rule: " + current.getSegmentingRule() +
                    ". This is a bug in Vespa, as the implementation has gotten out of sync." +
                    " Please create an issue.");
        };
    }

    private AndSegmentItem createAndSegment(BlockItem current) {
        return new AndSegmentItem(current.stringValue(), true, true);
    }

    private CompositeItem createPhraseSegment(BlockItem current, String indexName) {
        CompositeItem composite = new PhraseSegmentItem(current.getRawWord(), current.stringValue(), true, true);
        composite.setIndexName(indexName);
        return composite;
    }

    private void copyAttributes(Item blockAsItem, Item replacement) {
        copyWeight(blockAsItem, replacement);
        replacement.setFilter(blockAsItem.isFilter());
        replacement.setRanked(blockAsItem.isRanked());
        replacement.setPositionData(blockAsItem.usePositionData());
    }

    private void copyWeight(Item block, Item replacement) {
        int weight = getWeight(block);
        setWeight(replacement, weight);
    }

    private int getWeight(Item block) {
        if (block instanceof AndSegmentItem
                && ((AndSegmentItem) block).getItemCount() > 0) {
            return ((AndSegmentItem) block).getItem(0).getWeight();
        } else {
            return block.getWeight();
        }
    }

    // this smells like an extension of AndSegmentItem...
    private void setWeight(Item replacement, int weight) {
        if (replacement instanceof AndSegmentItem) {
            for (Iterator<Item> i = ((AndSegmentItem) replacement).getItemIterator();
                    i.hasNext();) {
                i.next().setWeight(weight);
            }
        } else {
            replacement.setWeight(weight);
        }
    }

    // TODO: Next four methods indicate Significance should be bubbled up the class hierarchy
    // TODO: Perhaps Significance should bubble up, but the real problem is the class/interface hierarchy for queries is in dire need of restructuring
    private void setSignificance(PhraseSegmentItem target, BlockItem original) {
        if (hasExplicitSignificance(original)) target.setSignificance(getSignificance(original));
    }

    private void setSignificance(TaggableItem target, BlockItem original) {
        if (hasExplicitSignificance(original)) target.setSignificance(getSignificance(original)); //copy
    }

    private boolean hasExplicitSignificance(BlockItem blockItem) {
        if (blockItem instanceof TermItem ) return ((TermItem)blockItem).hasExplicitSignificance();
        if (blockItem instanceof PhraseSegmentItem ) return ((PhraseSegmentItem)blockItem).hasExplicitSignificance();
        return false;
    }

    //assumes blockItem instanceof TermItem or PhraseSegmentItem
    private double getSignificance(BlockItem blockItem) {
        if (blockItem instanceof TermItem) return ((TermItem)blockItem).getSignificance();
        else return ((PhraseSegmentItem)blockItem).getSignificance();
    }

    private static class Connectivity {
        public final Item word;
        public final double value;

        public Connectivity(Item connectedItem, double connectivity) {
            this.word = connectedItem;
            this.value = connectivity;
        }

    }

}
