// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.intent.model;

import com.yahoo.search.Query;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.text.interpretation.Interpretation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This is the root node of an intent model.
 * The intent model represents the intent analysis of a query.
 * This is a probabilistic model - the query may have multiple interpretations with different probability.
 * Each interpretation may have multiple
 * possible intents, making this a tree.
 *
 * @author bratseth
 */
public class IntentModel extends ParentNode<InterpretationNode> {

    /** The name of the property carrying the intent model string: intentModel */
    public static final CompoundName intentModelStringName = CompoundName.from("intentModel");
    /** The name of the property carrying the intent model object: IntentModel */
    public static final CompoundName intentModelObjectName = CompoundName.from("IntentModel");

    private static final InterpretationNodeComparator inodeComp = new InterpretationNodeComparator();

    /** Creates an empty intent model */
    public IntentModel() {
    }

    /** Creates an intent model from some interpretations */
    public IntentModel(List<Interpretation> interpretations) {
        for (Interpretation interpretation : interpretations)
            children().add(new InterpretationNode(interpretation));
        sortChildren();
    }

    /** Creates an intent model from some interpretations */
    public IntentModel(Interpretation... interpretations) {
        for (Interpretation interpretation : interpretations)
            children().add(new InterpretationNode(interpretation));
        sortChildren();
    }

    /** Sort interpretations by descending score order */
    public void sortChildren() {
        children().sort(inodeComp);
    }

    /**
     * Returns a flattened list of sources with a normalized appropriateness of each, sorted by
     * decreasing appropriateness.
     * This is obtained by summing the source appropriateness vectors of each intent node weighted
     * by the owning intent and interpretation probabilities.
     * Sources with a resulting probability of 0 is omitted in the returned list.
     */
    public List<SourceNode> getSources() {
        Map<Source,SourceNode> sources=new HashMap<>();
        addSources(1.0,sources);
        List<SourceNode> sourceList=new ArrayList<>(sources.values());
        Collections.sort(sourceList);
        return sourceList;
    }

    /** Returns the names of the sources returned from {@link #getSources} for convenience */
    public List<String> getSourceNames() {
        List<String> sourceNames=new ArrayList<>();
        for (SourceNode sourceNode : getSources())
            sourceNames.add(sourceNode.getSource().getId());
        return sourceNames;
    }

    /** Returns the intent model stored at property key "intentModel" in this query, or null if none */
    public static IntentModel getFrom(Query query) {
        return (IntentModel)query.properties().get(intentModelObjectName);
    }

    /** Stores this intent model at property key "intentModel" in this query */
    public void setTo(Query query) {
        query.properties().set(intentModelObjectName,this);
    }

    static class InterpretationNodeComparator implements Comparator<InterpretationNode> {
        public int compare(InterpretationNode o1, InterpretationNode o2) {
            double diff = o2.getScore()-o1.getScore();
            return (diff>0) ? 1 : ( (diff<0)? -1:0 );
        }
    }
}
