// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text.interpretation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Span is a description of a part of a text, modeled as a tree.
 *
 * A span is defined by the range (from,to) and by a set of annotations on that range. It also contains a set
 * of child nodes that all have the restriction
 * <code>child.from &gt;= parent.from &amp;&amp; child.to &lt;= parent.to &amp;&amp; (child.to-child.from) &lt; (parent.to-parent.from)</code>
 * This means that all spans on a text can be modeled as a tree, where all child spans are guaranteed to be contained
 * inside its parent span.
 * <p>
 * A span will usually be used indirectly through Interpretation.
 *
 * @author Arne Bergene Fossaa
 */
public class Span {

    private final Modification modification;
    private List<Span> subSpans = null; //Lazy because of a large number of leaf nodes
    private final Map<AnnotationClass, Annotations> annotations = new HashMap<>();
    private Span parent; //Yes, this _should_ be final, but might be changed when adding an annotation
    private final int from;
    private final int to;

    /**
     * Creates a new root span based on the modfication
     */
    Span(final Modification modification)  {
        this.modification = modification;
        this.parent = null;
        this.from = 0;
        this.to = modification.getText().length();
    }

    // This constructor is private to ensure that all child spans for a span is contained inside it.
    private Span(int from, int to, Span parent) {
        this.parent = parent;
        this.modification = parent.modification;
        this.from = from;
        this.to = to;
    }

    /**
     * Returns the text that this spans is
     */
    public String getText() {
        return modification.getText().substring(from, to);
    }

    @Override
    public String toString() {
        return "SPAN: " + getText();
    }

    public Annotations annotate(AnnotationClass clazz) {
        Annotations annotations = this.annotations.get(clazz);
        if (!this.annotations.containsKey(clazz)) {
            annotations = new Annotations(this);
            this.annotations.put(clazz, annotations);
        }
        return annotations;
    }

    /**
     * This will either create or get the annotation of the class annotation
     */
    public Annotations annotate(int from, int to, AnnotationClass clazz) {
        return addAnnotation(from, to, clazz);
    }

    /**
     * Returns all annotations that are contained in either this subspan or in any of its subannotations
     */
    public Map<AnnotationClass, List<Annotations>> getAllAnnotations() {
        Map<AnnotationClass, List<Annotations>> result = new HashMap<>();
        getAllAnnotations(result);
        return result;
    }

    /**
     * Returns all spans, either this or any of the spans that are inherits this span that match the given term
     */
    public List<Span> getTermSpans(String term) {
        List<Span> spans = new ArrayList<>();
        getTermSpans(term, spans);
        return spans;
    }

    /**
     * Returns the annotations with a specific class for the area defined by this span
     * <p>
     *
     * This function will query its parent to find any annotation that is set for an area that this span is contained
     * in. If there are conflicts (several annotations defined with the same annotation class), the annotation
     * that is defined for the smallest area (furthest down in the tree), is used.
     */
    public Annotations getAnnotation(AnnotationClass clazz) {
        return getAnnotation(from, to, clazz);
    }

    /**
     * Returns the annotations with a specific class for the area defined by (from,to).
     *
     * This function will query its parent to find any annotation that is set for an area that this span is contained
     * in. If there are conflicts (several annotations defined with the same annotation class), the annotation
     * that is defined for the smallest area (furthest down in the tree), is used.
     *
     * @throws RuntimeException if (from,to) is not contained in the span
     */
    public Annotations getAnnotation(int from, int to, AnnotationClass clazz) {
        if (from < this.from || to > this.to) {
            throw new RuntimeException("Trying to get a range that is outside this span");
        }
        if (this.parent != null) {
            return parent.getAnnotation(from, to, clazz);
        } else {
            return getBestAnnotation(from, to, clazz );

        }
    }

    /**
     * Returns all AnnotationClasses that are defined for this span and any of its superspans.
     */
    public Set<AnnotationClass> getClasses() {
        return getClasses(from, to);
    }

    /**
     * Returns all AnnotationClasses that are defined for the range (from,to).
     *
     * @throws RuntimeException if (from, to) is not contained in the span
     */
    public Set<AnnotationClass> getClasses(int from, int to) {
        if(from < this.from || to > this.to) {
            throw new RuntimeException("Trying to get a range that is outside this span");
        }
        if (this.parent != null) {
            return parent.getClasses(from, to);
        } else {
            HashSet<AnnotationClass> classes = new HashSet<>();
            getAnnotationClasses(from, to, classes);
            return classes;
        }
    }

    /**
     * Returns an unmodifiable list of all spans below this span that is a leaf node
     */
    public List<Span> getTokens() {
        List<Span> spans = new ArrayList<>();
        getTokens(spans);
        return Collections.unmodifiableList(spans);
    }

    /**
     * Returns true if this class
     */
    public boolean hasClass(AnnotationClass clazz) {
        return getClasses().contains(clazz);
    }

    /**
     * Returns all spans that are directly childrens of this span. If the span is a leaf, the empty
     * list will be returned. The list is unmodifable.
     */
    public List<Span> getSubSpans() {
        return subSpans == null ?
                Collections.<Span>emptyList() :
                Collections.unmodifiableList(subSpans);
    }

    /** hack */
    public int getFrom() { return from; }

    /** hack */
    public int getTo() { return to; }

    // Needed by addAnnotation
    private List<Span> getRemovableSubSpan() {
        return subSpans == null ?
                Collections.<Span>emptyList() :
                subSpans;
    }

    private void addSubSpan(Span span) {
       if (subSpans == null) {
           subSpans = new ArrayList<>();
       }
       subSpans.add(span);
   }

    /**
     * How this works:
     *
     * First we check if any excisting subannotation can contain this annotation. If so, we leave it to them to add
     * the new annotation.
     *
     * Then we check if the new annotation intersects any of the excisting annotations. That is illegal to do
     *
     * We then add all subannotations that are strictly contained in the new annotation to the new annotation.
     */
    private Annotations addAnnotation(int from, int to, AnnotationClass clazz) {
        if (equalsRange(from, to)) {
            // We simply add everything from the new span to this
            if (annotations.containsKey(clazz)) {
                return annotations.get(clazz);
            } else {
                Annotations nAnnotations = new Annotations(this);
                annotations.put(clazz,nAnnotations);
                return nAnnotations;
            }
        }

        // We then check if any of the children intersects
        for (Span subSpan : getSubSpans()) {
            if (subSpan.intersects(from, to)) {
                throw new RuntimeException("Trying to add span that intersects already excisting span");
            } else if (subSpan.contains(from, to)) {
                return subSpan.addAnnotation(from, to, clazz);
            }
        }

        // We now know that we have to add the new span to this span
        Span span = new Span(from, to, this);
        Annotations nAnnotations = new Annotations(span);
        span.annotations.put(clazz,nAnnotations);
        addSubSpan(span);

        // We then add any subannotation that is inside the span
        Iterator<Span> subIterator = getRemovableSubSpan().iterator();

        while (subIterator.hasNext()) {
            Span subSpan = subIterator.next();
            if (subSpan.contains(from, to)) {
                return subSpan.addAnnotation(from, to, clazz);
            } else if (subSpan.isInside(from, to)) {
                // Take over the subannotation
                subSpan.parent = span;
                span.addSubSpan(subSpan);
                subIterator.remove();
            }
        }
        return nAnnotations;
    }

    private boolean contains(int from, int to) {
        return this.from <= from && this.to >= to;
    }

    private boolean isInside(int from, int to) {
        return this.from >= from && this.to <= to;
    }

    private boolean intersects(int from, int to) {
        return (this.from < from && this.to > from && this.to < to)
                || (this.from < to && this.to > to && this.from > from);
    }

    private boolean equalsRange(int from, int to) {
        return this.from == from && this.to == to;
    }

    private void getAllAnnotations(Map<AnnotationClass, List<Annotations>> results) {
        for(Map.Entry<AnnotationClass, Annotations> entry : annotations.entrySet()) {
            List<Annotations> anList = results.get(entry.getKey());
            if (anList == null) {
                anList = new ArrayList<>();
                results.put(entry.getKey(), anList);
            }
            anList.add(entry.getValue());
        }

        for(Span subSpan : getSubSpans()) {
            subSpan.getAllAnnotations(results);
        }
    }

    private void getTermSpans(String term, List<Span> spans) {
        if(term.equalsIgnoreCase(this.getText())) {
            spans.add(this);
        }
        for(Span subSpan : getSubSpans()) {
            subSpan.getTermSpans(term, spans);
        }
    }


    private void getAnnotationClasses(int from, int to, Set<AnnotationClass> classes) {
        if (!contains(from, to)) {
            return;
        }
        classes.addAll(annotations.keySet());
        for (Span subSpan : getSubSpans()) {
            subSpan.getAnnotationClasses(from, to, classes);
        }
    }

    private void getTokens(List<Span> spans) {
        if (getSubSpans().size() == 0) {
            spans.add(this);
        } else {
            for (Span subSpan : getSubSpans()) {
                subSpan.getTokens(spans);
            }

        }
    }

    private Annotations getBestAnnotation(int from, int to, AnnotationClass clazz) {
        if (!contains(from, to)) {
            return null;
        }
        // First yourself, then the subs
        Annotations annotations = this.annotations.get(clazz);
        for (Span subSpan : getSubSpans()) {
            Annotations subAnnotations = subSpan.getBestAnnotation(from, to, clazz);
            if (subAnnotations != null) {
                annotations = subAnnotations;
            }
        }
        return annotations;
    }

}
