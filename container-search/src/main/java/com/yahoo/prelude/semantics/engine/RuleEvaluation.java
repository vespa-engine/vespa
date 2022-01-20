// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.engine;

import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.TermType;
import com.yahoo.prelude.semantics.rule.Condition;
import com.yahoo.prelude.semantics.rule.ProductionRule;

import java.util.*;

/**
 * A particular evaluation of a particular rule.
 *
 * @author bratseth
 */
public class RuleEvaluation {

    // TODO: Create a query builder (or something) though which all query manipulation
    // here and in Evaluation is done. This class must also hold all the matches
    // and probably be able to update the match positions to keep them in sync with changes
    // to the query

    // Remember that whenever state is added to this class, you
    // must consider whether/how to make that state backtrackable
    // by saving information in choicepoint.state

    /** The items to match in this evaluation */
    private List<FlattenedItem> items;

    /** The current position into the list of items */
    private int position;

    /** The start position into the item list */
    private int startPosition;

    /** The references to matched contexts to be made in this evaluation */
    private Set<String> matchReferences;

    /** The current context of this evaluation, or null we're currently not in an interesting context */
    private String currentContext;

    /** A list of referencedMatches */
    private final List<ReferencedMatches> referencedMatchesList = new java.util.ArrayList<>();

    private final List<Match> nonreferencedMatches = new java.util.ArrayList<>();

    /** The evaluation owning this */
    private final Evaluation evaluation;

    /** The choice points saved in this evaluation */
    private Stack<Choicepoint> choicepoints = null;

    /* The last value returned by a condition evaluated in this, may be null */
    private Object value = null;

    /** True when we are evaluating inside a condition which inverts the truth value */
    private boolean inNegation = false;

    /**
     * A label we should use to match candidate terms for.
     * Used to propagate a label from e.g. reference conditions to named conditions
     */
    private String currentLabel = null;

    public RuleEvaluation(Evaluation owner) {
        this.evaluation = owner;
    }

    public void initialize(List<FlattenedItem> list, int startPosition) {
        this.startPosition = startPosition;
        items = list;
        reinitialize();
    }

    void reinitialize() {
        position = startPosition;
        currentContext = null;
        referencedMatchesList.clear();
        nonreferencedMatches.clear();
        if (choicepoints != null)
            choicepoints.clear();
    }

    public void setMatchReferences(Set<String> matchReferences) { this.matchReferences = matchReferences; }

    /**
     * <p>Calculates an id which is unique for each match (the totality of the matched terms)
     * to a high probability. Why can we not simply look at the position
     * of terms? Because rules are allowed to modify the query tree in ways that makes positions
     * change.</p>
     *
     * <p>This digest is also problematic, because it's really the matching condition who should
     * calculate a match digest for that term which incorporates the semantics of that kind
     * of match (maybe not the word and index, but something else). This is a todo for when
     * we add other kinds of conditions.</p>
     */
    int calculateMatchDigest(ProductionRule rule) {
        int matchDigest = rule.hashCode();
        int matchCounter = 1;
        for (Iterator<ReferencedMatches> i = referencedMatchesList.iterator(); i.hasNext(); ) {
            ReferencedMatches matches = i.next();
            int termCounter = 0;
            for (Iterator<Match> j = matches.matchIterator(); j.hasNext(); ) {
                Match match = j.next();
                matchDigest = 7 * matchDigest * matchCounter+
                              71 * termCounter +
                              match.hashCode();
                termCounter++;
            }
            matchCounter++;
        }
        for (Iterator<Match> i = nonreferencedMatches.iterator(); i.hasNext(); ) {
            Match match = i.next();
            matchDigest = 7 * matchDigest * matchCounter + match.hashCode();
            matchCounter++;
        }
        return matchDigest;
    }

    /**
     * Returns the current term item to look at,
     * or null if there are no more elements
     */
    public FlattenedItem currentItem() {
        if (position >= items.size()) return null;
        return items.get(position);
    }

    public FlattenedItem previousItem() {
        if (position-1 < 0) return null;
        return items.get(position - 1);
    }

    /** Returns the position of the current item */
    public int currentPosition() {
        return position;
    }

    /** Sets the current position */
    public void setPosition(int position) {
        this.position = position;
    }

    /** Returns the total number of items to match in this evaluation */
    public int itemCount() {
        return items.size() - startPosition;
    }

    /** Returns the last value returned by a condition in this evaluation, or null */
    public Object getValue() { return value; }

    /** Sets the last value returned by a condition in this evaluatiino, or null */
    public void setValue(Object value) { this.value = value; }

    /** Returns whether we are evaluating inside a condition which inverts the truth value */
    public boolean isInNegation() { return inNegation; }

    /** sets whether we are evaluating inside a condition which inverts the truth value */
    public void setInNegation(boolean inNegation) { this.inNegation = inNegation; }

    /** Returns the current position into the terms this evaluates over */
    public int getPosition() { return position; }

    /** Sets a new current label and returns the previous one */
    public String setCurrentLabel(String currentLabel) {
        String oldLabel = currentLabel;
        this.currentLabel = currentLabel;
        return oldLabel;
    }

    public String getCurrentLabel() { return currentLabel; }

    /**
     * Advances currentItem to the next term item and returns thatItem.
     * If the current item before this call is the last item, this will
     * return (and set currentItem to) null.
     */
    public FlattenedItem next() {
        position++;

        if (position >= items.size()) {
            position = items.size();
            return null;
        }

        return items.get(position);
    }

    // TODO: Simplistic yet. Nedd to support context nesting etc.
    public void entering(String context) {
        if (context == null) return;
        if (matchReferences != null && matchReferences.contains(context)) {
            currentContext = context;
        }
    }

    public void leaving(String context) {
        if (context == null) return;
        if (currentContext == null) return;
        if (currentContext.equals(context))
            currentContext = null;
    }

    /**
     * Adds a match
     *
     * @param item the match to add
     * @param replaceString the string to replace this match by, usually the item.getIndexedValue()
     */
    public void addMatch(FlattenedItem item, String replaceString) {
        evaluation.makeParentMutable(item.getItem());
        Match match = new Match(item, replaceString);
        if (currentContext != null) {
            ReferencedMatches matches = getReferencedMatches(currentContext);
            if (matches == null) {
                matches = new ReferencedMatches(currentContext);
                referencedMatchesList.add(matches);
            }
            matches.addMatch(match);
        }
        else {
            nonreferencedMatches.add(match);
        }
    }

    /** Returns the referenced matches for a context name, or null if none */
    public ReferencedMatches getReferencedMatches(String name) {
        for (Iterator<ReferencedMatches> i = referencedMatchesList.iterator(); i.hasNext(); ) {
            ReferencedMatches matches = i.next();
            if (name.equals(matches.getContextName()))
                return matches;
        }
        return null;
    }

    public int getReferencedMatchCount() { return referencedMatchesList.size(); }

    public int getNonreferencedMatchCount() { return nonreferencedMatches.size(); }

    /** Returns the evaluation this belongs to */
    public Evaluation getEvaluation() { return evaluation; }

    /** Adds an item to the query being evaluated in a way consistent with the query type */
    public void addItems(List<Item> items, TermType termType) {
        items.forEach(item -> evaluation.addItem(item, termType));
    }

    public void removeItem(Item item) {
        evaluation.removeItem(item);
    }

    public void removeItemByIdentity(Item item) {
        evaluation.removeItemByIdentity(item);
    }

    /** Removes an item, prefers the one at/close to the given position if there are multiple ones */
    public void removeItem(int position,Item item) {
        evaluation.removeItem(position,item);
    }


    /**
     * Inserts an item to the query being evaluated in a way consistent with the query type
     *
     * @param items the items to insert
     * @param parent the parent of this item, or null to set the root
     * @param index the index at which to insert this into the parent
     * @param termType the kind of item to index, this decides the resulting structure
     */
    public void insertItems(List<Item> items, CompositeItem parent, int index, TermType termType) {
        evaluation.insertItems(items, parent, index, termType);
    }

    /** Returns a read-only view of the items of this */
    public List<FlattenedItem> items() {
        return Collections.unmodifiableList(items);
    }

    public Match getNonreferencedMatch(int index) {
        return nonreferencedMatches.get(index);
    }

    public void trace(int level,String string) {
        evaluation.trace(level, string);
    }

    public int getTraceLevel() {
        return evaluation.getTraceLevel();
    }

    public void indentTrace() {
        evaluation.indentTrace();
    }

    public void unindentTrace() {
        evaluation.unindentTrace();
    }

    /**
     * Add a choice point to this evaluation
     *
     * @param  condition the creating condition
     * @param  create true to create this choicepoint if it is missing
     * @return the choicepoint, or null if not present, and create is false
     */
    public Choicepoint getChoicepoint(Condition condition, boolean create) {
        if (choicepoints == null) {
            if ( ! create) return null;
            choicepoints = new java.util.Stack<>();
        }
        Choicepoint choicepoint=lookupChoicepoint(condition);
        if (choicepoint == null) {
            if ( ! create) return null;
            choicepoint = new Choicepoint(this, condition);
            choicepoints.push(choicepoint);
        }
        return choicepoint;
    }

    private Choicepoint lookupChoicepoint(Condition condition) {
        for (Iterator<Choicepoint> i = choicepoints.iterator(); i.hasNext(); ) {
            Choicepoint choicepoint = i.next();
            if (condition == choicepoint.getCondition())
                return choicepoint;
        }
        return null;
    }

    List<ReferencedMatches> referencedMatches() {
        return referencedMatchesList;
    }

    List<Match> nonreferencedMatches() {
        return nonreferencedMatches;
    }

    /** Remove all the terms recognized by this match */
    public void removeMatches(ReferencedMatches matches) {
        for (Iterator<Match> i = matches.matchIterator(); i.hasNext(); ) {
            Match match = i.next();
            removeItemByIdentity(match.getItem());
        }
    }

}
