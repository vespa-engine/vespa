// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

import java.util.Iterator;
import java.util.List;

import com.yahoo.prelude.semantics.engine.Choicepoint;
import com.yahoo.prelude.semantics.engine.FlattenedItem;
import com.yahoo.prelude.semantics.engine.RuleEvaluation;

/**
 * A condition which greedily matches anything, represented as "..."
 *
 * @author bratseth
 */
public class EllipsisCondition extends Condition {

    /** Whether this ellipsis is actually referable (enclosed in []) or not */
    private boolean referable;

    /** Creates a referable ellipsis condition with no label */
    public EllipsisCondition() {
        this(true);
    }

    /** Creates an ellipsis condition with no label */
    public EllipsisCondition(boolean referable) {
        this(null,referable);
    }

    /** Creates an ellipsis condition */
    public EllipsisCondition(String label,boolean referable) {
        super(label);
        this.referable=referable;
        if (referable)
            setContextName("...");
    }

    public EllipsisCondition(String label,String context) {
        super(label,context);
    }

    public boolean doesMatch(RuleEvaluation e) {
        // We use a choice point to remember which untried alternatives are not tried (if any)
        // We never need to backtrack to this choice - backtracking is done by the parent
        // if this choice gives a global invalid state
        Choicepoint choicepoint=e.getChoicepoint(this,false);
        if (choicepoint==null) { // First try
            choicepoint=e.getChoicepoint(this,true);
        }
        else {
            if (!choicepoint.isOpen()) return false;
        }

        // Match all the rest of the items the first time, then all except the last item and so on
        int numberOfTermsToMatch=e.itemCount() - e.currentPosition() - choicepoint.tryCount();
        if (numberOfTermsToMatch<0) {
            choicepoint.close();
            return false;
        }
        choicepoint.addTry();

        String matchedTerms=matchTerms(numberOfTermsToMatch,e);
        e.setValue(matchedTerms);
        return true;
    }

    private String matchTerms(int numberOfTerms,RuleEvaluation e) {
        StringBuilder b=new StringBuilder();
        for (int i=0; i<numberOfTerms; i++) {
            e.addMatch(e.currentItem(),e.currentItem().getItem().getIndexedString());
            b.append(e.currentItem().getItem().stringValue());
            if (i<(numberOfTerms-1))
                b.append(" ");
            e.next();
        }
        return b.toString();
    }

    public String getMatchInfo(RuleEvaluation e) {
        Choicepoint choicepoint=e.getChoicepoint(this,false);
        if (choicepoint==null) return null;

        return spaceSeparated(e.items().subList(choicepoint.getState().getPosition(),
                                                e.itemCount() - choicepoint.tryCount() +1 ));
    }

    private String spaceSeparated(List<FlattenedItem> items) {
        StringBuilder buffer=new StringBuilder();
        for (Iterator<FlattenedItem> i=items.iterator(); i.hasNext(); ) {
            buffer.append(i.next().toString());
            if (i.hasNext())
                buffer.append(" ");
        }
        return buffer.toString();
    }

    /** Returns whether this ellipsis condition can be referred from a production */
    public boolean isReferable() {
        return referable || super.isReferable();
    }

    /** Sets whether this ellipsis condition can be referred from a production or not */
    public void setReferable(boolean referable) {
        this.referable=referable;
        if (referable && getContextName()==null)
            setContextName("...");
        if (!referable && "...".equals(getContextName()))
            setContextName(null);
    }

    protected boolean hasOpenChoicepoint(RuleEvaluation e) {
        Choicepoint choicepoint=e.getChoicepoint(this,false);
        if (choicepoint==null) return false; // Not tried yet
        if (!choicepoint.isOpen()) return false;
        return true;
    }

    protected boolean isDefaultContextName() {
        return (getContextName()==null || getContextName().equals("..."));
    }

    protected String toInnerString() {
        if (referable)
            return "[...]";
        else
            return "...";
    }

}
