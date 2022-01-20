// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.engine;

import com.yahoo.prelude.query.*;
import com.yahoo.prelude.semantics.RuleBase;
import com.yahoo.search.Query;
import com.yahoo.search.query.QueryTree;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * An evaluation of a query over a rule base. There is one evaluation for each evaluation
 * of one query over one rule base.
 *
 * @author bratseth
 */
public class Evaluation {

    // TODO: Retrofit query into the namespace construct
    private ParameterNameSpace parameterNameSpace = null;

    private final Query query;

    /** The current index into the flattened item list */
    private int currentIndex = 0;

    /** Query items flattened to a list iterator */
    private List<FlattenedItem> flattenedItems;

    /** The rule evaluation context, can be reset once the rule is evaluated */
    private final RuleEvaluation ruleEvaluation;

    private final RuleBase ruleBase;

    /**
     * The amount of context information to collect about this evaluation.
     * 0 means no context information, higher numbers means more context information.
     */
    private final int traceLevel;

    private String traceIndentation = "";

    /** See RuleEngine */
    private final Set<Integer> matchDigests = new HashSet<>();

    /** The previous size of this query (see RuleEngine), set on matches only */
    private int previousQuerySize = 0;

    /** Should we allow stemmed matches? */
    private boolean stemming = true;

    public Evaluation(Query query, RuleBase ruleBase) {
        this(query, ruleBase, 0);
    }

    /**
     * Creates a new evaluation
     *
     * @param query the query this evaluation is for
     * @param traceLevel the amount of tracing to do
     */
    public Evaluation(Query query, RuleBase ruleBase, int traceLevel) {
        this.query = query;
        this.ruleBase = ruleBase;
        this.traceLevel = traceLevel;
        reset();
        ruleEvaluation = new RuleEvaluation(this);
    }

    /** Returns the rule base this evaluates over */
    public RuleBase ruleBase() { return ruleBase; }

    /** Resets the item iterator to point to the first item */
    public void reset() {
        if (flattenedItems != null)
            previousQuerySize = flattenedItems.size();
        currentIndex = 0;
        traceIndentation = "";
        flattenedItems = new java.util.ArrayList<>();
        flatten(query.getModel().getQueryTree().getRoot(), 0, flattenedItems);
    }

    /** Sets the item iterator to point to the last item: */
    public void setToLast() {
        if (flattenedItems != null)
                currentIndex = flattenedItems.size() - 1;
        else
                currentIndex = -1;
    }

    /** Resets the item iterator to point to the last item: */
    public void resetToLast() {
        if (flattenedItems != null)
            previousQuerySize = flattenedItems.size();
        traceIndentation = "";
        flattenedItems = new java.util.ArrayList<>();
        flatten(query.getModel().getQueryTree().getRoot(), 0, flattenedItems);
        currentIndex = flattenedItems.size() - 1;
    }

    public Query getQuery() { return query; }

    /** Set to true to enable stemmed matches. True by default */
    public void setStemming(boolean stemming) { this.stemming = stemming; }

    /** Returns whether stemmed matches are allowed. True by default */
    public boolean getStemming() { return stemming; }

    void addMatchDigest(int digest) { matchDigests.add(Integer.valueOf(digest)); }

    boolean hasMatchDigest(int matchDigest) { return matchDigests.contains(Integer.valueOf(matchDigest)); }

    int getPreviousQuerySize() { return previousQuerySize; }

    public int getQuerySize() { return flattenedItems.size(); }

    /** Advances to the next item as current item */
    public void next() {
        currentIndex++;
    }

    public void previous() {//PGA
        currentIndex--;
    }

    /** Returns the current item, or null if there is no more elements */
    public FlattenedItem currentItem() {
        if ( (currentIndex >= flattenedItems.size()) || (currentIndex < 0)) return null;
        return flattenedItems.get(currentIndex);
    }

    /** Returns a fresh rule evaluation starting at the current position of this */
    public RuleEvaluation freshRuleEvaluation() {
        ruleEvaluation.initialize(flattenedItems, currentIndex);
        return ruleEvaluation;
    }

    /** Adds an item to the query being evaluated in a way consistent with the query type */
    // TODO: Add this functionality to Query?
    public void addItem(Item item, TermType termType) {
        Item root = query.getModel().getQueryTree().getRoot();
        if (root == null)
            query.getModel().getQueryTree().setRoot(item);
        else
            query.getModel().getQueryTree().setRoot(combineItems(root, item, termType));
    }

    /** Removes this item */
    public void removeItem(Item item) {
        item.getParent().removeItem(item);
    }

    /**
     * Removes this item by identity to ensure we remove the right one if there are multiple
     * equal items
     */
    public void removeItemByIdentity(Item item) {
        int position = findIndexByIdentity(item);
        if (position >= 0)
            item.getParent().removeItem(position);
        else
            item.getParent().removeItem(item); // Fallback to removeField by equal()
    }

    private int findIndexByIdentity(Item item) {
        int position = 0;
        for (Iterator<Item> i = item.getParent().getItemIterator(); i.hasNext(); ) {
            Item child = i.next();
            if (item == child) {
                return position;
            }
            position++;
        }
        return -1;
    }

    /** Removes an item, prefers the one at/close to the given position if there are multiple ones */
    public void removeItem(int position,Item item) {
        Item removeCandidate = item.getParent().getItem(position);
        if (removeCandidate.equals(item)) // Remove based on position
            item.getParent().removeItem(position);
        else
            item.getParent().removeItem(item); // Otherwise, just removeField any such item
    }

    /**
     * Convert segment items into their mutable counterpart, do not update query tree.
     * Non-segment items are returned directly.
     *
     * @return a mutable CompositeItem instance
     */
    private CompositeItem convertSegmentItem(CompositeItem item) {
        if (!(item instanceof SegmentItem)) {
            return item;
        }
        CompositeItem converted;
        if (item instanceof AndSegmentItem) {
            converted = new AndItem();
        } else if (item instanceof PhraseSegmentItem) {
            PhraseItem p = new PhraseItem();
            PhraseSegmentItem old = (PhraseSegmentItem) item;
            p.setIndexName(old.getIndexName());
            converted = p;
        } else {
            // TODO: Do something else than nothing for unknowns?
            return item;
        }
        for (Iterator<Item> i = item.getItemIterator(); i.hasNext();) {
            converted.addItem(i.next());
        }
        return converted;
    }


    private void insertMutableInTree(CompositeItem mutable, CompositeItem original, CompositeItem parent) {
        if (parent == null) {
            query.getModel().getQueryTree().setRoot(mutable);

        } else {
            int parentsIndex = parent.getItemIndex(original);
            parent.setItem(parentsIndex, mutable);
        }
    }

    /**
     * Convert The parent of this item into a mutable item. Note, this
     * may change the shape of the query tree. (E.g. if the original parent is a
     * segment phrase, and the original parent's parent is a phrase, the terms
     * from the parent will be moved to the parent's parent.)
     *
     * @param item the item for which the parent shall be made mutable
     */
    public void makeParentMutable(TermItem item) {
        CompositeItem parent = item.getParent();
        CompositeItem mutable = convertSegmentItem(parent);
        if (parent != mutable) {
            CompositeItem parentsParent = parent.getParent();
            insertMutableInTree(mutable, parent, parentsParent);
        }
    }

    /**
     * Inserts an item to the query being evaluated in a way consistent with the query type
     *
     * @param items the items to insert
     * @param parent the parent of these items, or null to set the root
     * @param index the index at which to insert these into the parent
     * @param desiredParentType the desired type of the composite which contains items when this returns
     */
    public void insertItems(List<Item> items, CompositeItem parent, int index, TermType desiredParentType) {
        if (isEmpty(parent)) {
            if (items.size() == 1 && desiredParentType.hasItemClass(items.get(0).getClass())) {
                query.getModel().getQueryTree().setRoot(items.get(0));
            }
            else {
                CompositeItem newParent = (CompositeItem) desiredParentType.createItemClass();
                items.forEach(item -> newParent.addItem(item));
                query.getModel().getQueryTree().setRoot(newParent);
            }
            return;
        }

        if (parent.getItemCount() > 0 && parent instanceof QueryTree && parent.getItem(0) instanceof CompositeItem) {
            // combine with the existing root instead
            parent = (CompositeItem)parent.getItem(0);
            if (index == 1) { // that means adding it after the existing root
                index = parent.getItemCount();
            }
        }

        if (( desiredParentType == TermType.DEFAULT || desiredParentType.hasItemClass(parent.getClass()) )
             && equalIndexNameIfParentIsPhrase(items, parent)) {
            for (Item item : items)
                addItem(parent, index, item, desiredParentType);
        }
        else if (parent.items().isEmpty()) {
            CompositeItem parentsParent = parent.getParent();
            CompositeItem newParent = newParent(desiredParentType);
            items.forEach(item -> newParent.addItem(item));
            parentsParent.setItem(parentsParent.getItemIndex(parent), newParent);
        }
        else if (items.size() == 1 && desiredParentType.hasItemClass(items.get(0).getClass())) {
            addItem(parent, index, items.get(0), desiredParentType);
        }
        else {
            insertWithDesiredParentType(items, parent, desiredParentType);
        }
    }

    /** Returns true if this item represents an empty query *tree*. */
    private boolean isEmpty(Item item) {
        if (item == null) return true;
        if (item instanceof QueryTree && ((QueryTree) item).isEmpty()) return true;
        return false;
    }

    private void addItem(CompositeItem parent, int index, Item item, TermType desiredParentType) {
        if (parent instanceof NotItem) {
            if (index == 0 && parent.getItem(0) == null) { // Case 1: The current positive is null and we are adding a positive
                parent.setItem(0, item);
            }
            else if (index <= 1 && !(parent.getItem(0) instanceof CompositeItem))  { // Case 2: The positive must become a composite
                CompositeItem positiveComposite = (CompositeItem)desiredParentType.createItemClass();
                positiveComposite.addItem(parent.getItem(0));
                positiveComposite.addItem(index, item);
                parent.setItem(0, positiveComposite);
            }
            else if (parent.getItem(0)!=null && parent.getItem(0) instanceof CompositeItem // Case 3: Add to the positive composite
                     && index <= ((CompositeItem)parent.getItem(0)).getItemCount()) {
                ((CompositeItem)parent.getItem(0)).addItem(index, item);
            }
            else { // Case 4: Add negative
                parent.addItem(index, item);
            }
        }
        else if (parent.getItemCount() > 0 && parent instanceof QueryTree) {
            CompositeItem composite = (CompositeItem)desiredParentType.createItemClass();
            composite.addItem(parent.getItem(0));
            composite.addItem(index, item);
            parent.setItem(0, composite);
        }
        else {
            parent.addItem(index, item);
        }
    }

    /** A special purpose check used to simplify the above */
    private boolean equalIndexNameIfParentIsPhrase(List<Item> items, CompositeItem parent) {
        if ( ! (parent instanceof PhraseItem)) return true;
        var phrase = (PhraseItem)parent;

        for (Item item : items) {
            if ( ! (item instanceof IndexedItem)) continue;
            var indexedItem = (IndexedItem)item;
            if (! indexedItem.getIndexName().equals(phrase.getIndexName())) return false;
        }
        return true;
    }

    private void insertWithDesiredParentType(List<Item> items, CompositeItem parent, TermType desiredParentType) {
        CompositeItem parentsParent = parent.getParent();

        CompositeItem newParent = newParent(desiredParentType);

        if (! (parentsParent instanceof QueryTree) && parentsParent.getItemType() == newParent.getItemType()) { // Collapse
            newParent = parentsParent;
        }

        for (Item item : items)
            newParent.addItem(item);

        if (desiredParentType == TermType.EQUIV || desiredParentType == TermType.PHRASE) { // insert new parent below the current
            parent.addItem(newParent);
        }
        else { // insert new parent above the current
            newParent.addItem(parent);
            if (newParent != parentsParent) // Insert new parent as root or child of old parent's parent
                parentsParent.setItem(parentsParent.getItemIndex(parent), newParent);
        }
    }

    private CompositeItem newParent(TermType desiredParentType) {
        return desiredParentType == TermType.DEFAULT ? new AndItem() : (CompositeItem)desiredParentType.createItemClass();
    }

    private Item combineItems(Item first, Item second, TermType termType) {
        if (first instanceof NullItem) {
            return second;
        } else if (first instanceof NotItem) {
            NotItem notItem = (NotItem)first;
            if (termType == TermType.NOT) {
                notItem.addNegativeItem(second);
            }
            else {
                Item newPositive = combineItems(notItem.getPositiveItem(), second, termType);
                notItem.setPositiveItem(newPositive);
            }
            return notItem;
        }
        else if (first instanceof CompositeItem) {
            CompositeItem composite = (CompositeItem)first;
            CompositeItem combined = createType(termType);
            if (combined.getClass().equals(composite.getClass())) {
                composite.addItem(second);
                return composite;
            }
            else {
                if (combined instanceof EquivItem) {
                    first = makeEquivCompatible(first);
                    second = makeEquivCompatible(second);
                }
                combined.addItem(first);
                combined.addItem(second); // Also works for nots
                return combined;
            }
        }
        else if (first instanceof TermItem) {
            CompositeItem combined = createType(termType);
            combined.addItem(first);
            combined.addItem(second);
            return combined;
        }
        else {
            throw new RuntimeException("Don't know how to add an item to type " + first.getClass());
        }
    }

    private Item makeEquivCompatible(Item item) {
        if (item instanceof AndItem || item instanceof WeakAndItem) {
            PhraseItem phrase = new PhraseItem();
            List<Item> children = ((CompositeItem)item).items();
            if (children.isEmpty()) return phrase;
            String index = ((IndexedItem)children.get(0)).getIndexName();
            for (var child : ((CompositeItem)item).items())
                phrase.addItem(child);
            phrase.setIndexName(index);
            return phrase;
        }
        else {
            return item; // Compatible, or can't be made so
        }
    }

    private CompositeItem createType(TermType termType) {
        if (termType == TermType.DEFAULT) {
            if (query.getModel().getType() == Query.Type.ANY)
                return new OrItem();
            else
                return new AndItem();
        }
        else {
            return (CompositeItem)termType.createItemClass();
        }
    }

    private void flatten(Item item,int position,List<FlattenedItem> toList) {
        if (item == null) return;
        if (item.isFilter()) return;

        if (item instanceof TermItem) {  // make eligible for matching
            toList.add(new FlattenedItem((TermItem)item, position));
            return;
        }

        if (item instanceof CompositeItem) { // make children eligible for matching
            CompositeItem composite = (CompositeItem)item;
            int childPosition = 0;
            for (Iterator<?> i = composite.getItemIterator(); i.hasNext(); ) {
                flatten((Item)i.next(), childPosition++, toList);
            }
        }

        // other terms are inmatchable
    }

    public void trace(int level,String message) {
        if (level > getTraceLevel()) return;
        query.trace(traceIndentation + message,false,1);
    }

    /**
     * The amount of context information to collect about this evaluation.
     * 0 (the default) means no context information, higher numbers means
     * more context information.
     */
    public int getTraceLevel() { return traceLevel; }

    public void indentTrace() {
        traceIndentation  =traceIndentation + "   ";
    }

    public void unindentTrace() {
        if (traceIndentation.length()<3)
            traceIndentation  ="";
        else
            traceIndentation  =traceIndentation.substring(3);
    }

    public NameSpace getNameSpace(String nameSpaceName) {
        if (nameSpaceName.equals("parameter")) {
            if (parameterNameSpace == null)
                parameterNameSpace = new ParameterNameSpace();
            return parameterNameSpace;
        }

        // That's all for now
        throw new RuntimeException("Unknown namespace '" + nameSpaceName + "'");

    }

}
