// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.model;

import java.util.*;

/**
 * A choice between some alternative lists of page elements.
 *
 * @author bratseth
 */
public final class Choice extends AbstractChoice {

    private List<List<PageElement>> alternatives=new ArrayList<>(3);

    /** Creates an empty choice */
    public Choice() { }

    /** Creates a choice having a single alternative having a single page element */
    public static Choice createSingleton(PageElement singletonAlternative) {
        Choice choice=new Choice();
        choice.alternatives().add(createSingletonList(singletonAlternative));
        return choice;
    }

    /** Creates a choice in which each alternative consists of a single element */
    public static Choice createSingletons(List<PageElement> alternatives) {
        Choice choice=new Choice();
        for (PageElement alternative : alternatives)
            choice.alternatives().add(createSingletonList(alternative));
        return choice;
    }

    private static List<PageElement> createSingletonList(PageElement member) {
        List<PageElement> list=new ArrayList<>();
        list.add(member);
        return list;
    }

    /**
     * Creates a choice between some alternatives. This method takes a copy of the given lists.
     */
    public Choice(List<List<PageElement>> alternatives) {
        for (List<PageElement> alternative : alternatives)
            this.alternatives.add(new ArrayList<>(alternative));
    }

    /**
     * Returns the alternatives of this as a live reference to the alternatives of this.
     * The list and elements may be modified unless this is frozen. This is never null.
     */
    public List<List<PageElement>> alternatives() { return alternatives; }

    /** Convenience shorthand of <code>return alternatives().get(index)</code> */
    public List<PageElement> get(int index) {
        return alternatives.get(index);
    }

    /** Convenience shorthand for <code>if (alternative!=null) alternatives().add(alternative)</code> */
    public void add(List<PageElement> alternative) {
        if (alternative!=null)
            alternatives.add(new ArrayList<>(alternative));
    }

    /** Returns true only if there are no alternatives in this */
    public boolean isEmpty() { return alternatives.size()==0; }

    /** Answers true if this is either a choice between the given class, or between Lists of the given class */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public boolean isChoiceBetween(Class pageTemplateModelElementClass) {
        List firstNonEmpty=null;
        for (List<PageElement> value : alternatives) {
            if (pageTemplateModelElementClass.isAssignableFrom(value.getClass())) return true;
            if (value instanceof List) {
                List listValue=(List)value;
                if (listValue.size()>0)
                    firstNonEmpty=listValue;
            }
        }
        if (firstNonEmpty==null) return false;
        return (pageTemplateModelElementClass.isAssignableFrom(firstNonEmpty.get(0).getClass()));
    }

    @Override
    public void freeze() {
        if (isFrozen()) return;
        super.freeze();
        for (ListIterator<List<PageElement>> i=alternatives.listIterator(); i.hasNext(); ) {
            List<PageElement> alternative=i.next();
            for (PageElement alternativeElement : alternative)
                alternativeElement.freeze();
            i.set(Collections.unmodifiableList(alternative));
        }
        alternatives= Collections.unmodifiableList(alternatives);
    }

    /** Accepts a visitor to this structure */
    @Override
    public void accept(PageTemplateVisitor visitor) {
        visitor.visit(this);
        for (List<PageElement> alternative : alternatives) {
            for (PageElement alternativeElement : alternative)
                alternativeElement.accept(visitor);
        }
    }

    @Override
    public String toString() {
        if (alternatives.isEmpty()) return "(empty choice)";
        if (alternatives.size()==1) return alternatives.get(0).toString();
        return "a choice between " + alternatives;
    }

}
