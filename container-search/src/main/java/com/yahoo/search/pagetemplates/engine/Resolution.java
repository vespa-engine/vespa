// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.engine;

import com.yahoo.search.pagetemplates.model.Choice;
import com.yahoo.search.pagetemplates.model.MapChoice;
import com.yahoo.search.pagetemplates.model.PageElement;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A resolution of choices within a template.
 *
 * @author bratseth
 */
public class Resolution {

    /** A record of choices made as choice → alternative index (id) */
    private Map<Choice,Integer> choiceResolutions=new IdentityHashMap<>();

    /** A of map choices made as choice → mapping */
    private Map<MapChoice,Map<String,List<PageElement>>> mapChoiceResolutions=
            new IdentityHashMap<>();

    public void addChoiceResolution(Choice choice,int alternativeIndex) {
        choiceResolutions.put(choice,alternativeIndex);
    }

    public void addMapChoiceResolution(MapChoice choice, Map<String,List<PageElement>> mapping) {
        mapChoiceResolutions.put(choice,mapping);
    }

    /**
     * Returns the resolution of a choice.
     *
     * @return the (0-base) index of the choice made. If the given choice has exactly one alternative,
     *         0 is always returned (whether or not the choice has been attempted resolved).
     * @throws IllegalArgumentException if the choice is empty, or if it has multiple alternatives but have not
     *         been resolved in this
     */
    public int getResolution(Choice choice) {
        if (choice.alternatives().size() == 1) return 0;
        if (choice.isEmpty()) throw new IllegalArgumentException("Cannot return a resolution of empty " + choice);
        Integer resolution = choiceResolutions.get(choice);
        if (resolution == null) throw new IllegalArgumentException(this + " has no resolution of " + choice);
        return resolution;
    }

    /**
     * Returns the resolution of a map choice.
     *
     * @return the chosen mapping - entries from placeholder id to the values to use at the location of that placeholder
     * @throws IllegalArgumentException if this choice has not been resolved in this
     */
    public Map<String,List<PageElement>> getResolution(MapChoice choice) {
        Map<String,List<PageElement>> resolution=mapChoiceResolutions.get(choice);
        if (resolution==null) throw new IllegalArgumentException(this + " has no resolution of " + choice);
        return resolution;
    }

    @Override
    public String toString() {
        return "a resolution of " + choiceResolutions.size() + " choices";
    }

}
