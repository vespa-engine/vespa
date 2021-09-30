// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text.interpretation;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * An interpretation of a text.
 *
 * This class it the main class to use when when querying and modifying annotations for a text.
 *
 * The interpretation consists of a tree of annotations, with the nodes in tree being Spans. An annotation
 * is defined by its annotationClass ("person"), and by a key/value map of
 * parameters for that annotationClass (if the person is an actor or other notable person).
 *
 * This class is the main class for querying and setting annotations, where modifying the span tree
 * is not needed.
 *
 * @see Span
 * @author Arne Bergene Fossaa
 */
public class Interpretation {

    private final Modification modification;
    private double probability;
    private final Span rootSpan;

    public final static AnnotationClass INTERPRETATION_CLASS = new AnnotationClass("interpretation");

    /**
     * Creates a new interpretation and a new modification from the text,
     * with the probability set to the default value(0.0).
     */
    public Interpretation(String text) {
        this(text,0.0);
    }

    /**
     * Creates a new interpretation and a new modification from the text, with the given probability.
     */
    public Interpretation(String text, double probabilty) {
        this(new Modification(text),probabilty);
    }

    /**
     * Creates a new interpretation based on the modification, with the probability set to the default value(0.0).
     */
    public Interpretation(Modification modification) {
        this(modification,0.0);
    }

    /**
     * Creates an interpretation based on the modification given.
     */
    public Interpretation(Modification modification,double probability) {
        this.modification = modification;
        rootSpan = new Span(modification);
        setProbability(probability);
    }

    public Modification getModification() {
        return modification;
    }

    /**
     * The probability that this interpretation is correct.
     * @return a value between 0.0 and 1.0 that gives the probability that this interpretation is correct
     */
    public double getProbability() {
        return probability;
    }

    /**
     * Sets he probability that this interpretation is the correct. The value is not normalized,
     * meaning that it can have a value larger than 1.0.
     *
     * The value is used when sorting interpretations.
     */
    public void setProbability(double probability) {
        if (probability < 0) {
            probability = 0.0;
        } else if (probability > 1.0) {
            probability = 1.0;
        }
        this.probability = probability;

    }

    /** Returns the root of the tree representation of the interpretation */
    public Span root() { return rootSpan; }

    // Wrapper methods for Span

    /**
     * Return the annotation with the given annotationclass (and create it if necessary).
     *
     * @param annotationClass The class of the annotation
     */
    public Annotations annotate(String annotationClass)  {
        return annotate(new AnnotationClass(annotationClass));
    }

    /**
     * Return the annotation with the given annotationclass (and create it if necessary).
     *
     * @param annotationClass The class of the annotation
     */
    public Annotations annotate(AnnotationClass annotationClass) {
        return rootSpan.annotate(annotationClass);
    }

    /**
     * Sets a key/value pair for an annotation. If an annotation of the class does not
     * exist, a new is created.
     *
     * A shortcut for annotate(annotationClass).put(key,value)
     *
     * @param annotationClass class of the annotation
     * @param key key of the property to set on the annotation
     * @param value value of the property to set on the annotation
     */
    public void annotate(String annotationClass, String key, Object value) {
        annotate(new AnnotationClass(annotationClass)).put(key,value);
    }

    /**
     * Sets a key/value pair for an annotation. If an annotation of the class does not
     * exist, a new is created.
     *
     * A shortcut for annotate(annotationClass).put(key,value)
     *
     * @param annotationClass class of the annotation
     * @param key key of the property to set on the annotation
     * @param value value of the property to set on the annotation
     */
     public void annotate(AnnotationClass annotationClass, String key, Object value) {
        annotate(annotationClass).put(key,value);
    }

    /**
     * Returns the annotation with the given annotationClass (and create it if necessary).
     *
     * @param from start of the substring
     * @param to end of the substring
     * @param annotationClass  class of the annotation
     */
    public Annotations annotate(int from, int to, String annotationClass) {
        return annotate(from,to,new AnnotationClass(annotationClass));
    }

    /**
     * Returns the annotation with the given annotationClass (and create it if necessary).
     *
     * @param from start of the substring
     * @param to end of the substring
     * @param annotationClass  class of the annotation
     */
    public Annotations annotate(int from, int to, AnnotationClass annotationClass) {
        return rootSpan.annotate(from,to,annotationClass);
    }

    /**
     * Sets a key/value pair for an annotation of a substring. If an annotation of the class
     * does not exist, a new is created.
     *
     * A shortcut for annotate(from, to, annotationClass, key, value)
     *
     * @param from start of the substring
     * @param to end of the substring
     * @param annotationClass class of the annotation
     * @param key key of property to set on annotation
     * @param value value of property to set on annotation
     */
    public void annotate(int from, int to, String annotationClass, String key, Object value) {
        annotate(from, to,new AnnotationClass(annotationClass)).put(key, value);
    }

    /**
     * Sets a key/value pair for an annotation of a substring. If an annotation of the class
     * does not exist, a new is created.
     *
     * A shortcut for annotate(from, to, annotationClass, key, value)
     *
     * @param from start of the substring
     * @param to end of the substring
     * @param annotationClass class of the annotation
     * @param key key of property to set on annotation
     * @param value value of property to set on annotation
     */
    public void annotate(int from, int to, AnnotationClass annotationClass, String key, Object value) {
        annotate(from, to, annotationClass).put(key, value);
    }

    /**
     * Gets all annotations mentioned in the query. This will also return all subannotations, even those that
     * override their parents
     */
    public Map<AnnotationClass,List<Annotations>> getAll() {
        return rootSpan.getAllAnnotations();
    }

    /**
     * Returns a list of all annotations of the given class that exists in the text. This will also return
     * all subannotations, even those that override their parents.
     * If there are none, an empty list is returned, never null. The returned list should not be modified.
     */
    public List<Annotations> getAll(String annotationClass) {
        return getAll(new AnnotationClass(annotationClass));
    }

    /**
     * Returns a list of all annotations of the given class that exists in the text. This will also return
     * all subannotations, even those that override their parent.
     * If there are none, an empty list is returned, never null. The returned list should not be modified.
     */
    public List<Annotations> getAll(AnnotationClass annotationClass) {
        // TODO: This implementation is very inefficient because it unnecessarily collects for all classes
        return getAll().getOrDefault(annotationClass, List.of());
    }

    /**
     * Returns the annotation marked with the annotationClass.
     *
     * This is different from annotate(annotationClass) because a new annotation
     * will not be created if it does not exist.
     *
     * @param annotationClass class of the annotation
     * @return an annotation with the given class, null if it does not exists
     */
    public Annotations get(String annotationClass) {
        return get(new AnnotationClass(annotationClass));
    }

    /**
     * Returns the annotation marked with the annotationClass.
     *
     * This is different from annotate(annotationClass) because a new annotation
     * will not be created if it does not exist.
     *
     * @param annotationClass class of the annotation
     * @return an annotation with the given class, null if it does not exists
     */
    public Annotations get(AnnotationClass annotationClass) {
        return rootSpan.getAnnotation(annotationClass);
    }

    /**
     * Gets the value of a property set on an annotation.
     * If the annotation or the key/value pair does not exists, null
     * is returned.
     */
    public Object get(String annotationClass,String key) {
        return get(new AnnotationClass(annotationClass),key);
    }

    /**
     * Gets the value of a property set on an annotation.
     * If the annotation or the key/value pair does not exists, null
     * is returned.
     */
    public Object get(AnnotationClass annotationClass,String key) {
        Annotations annotations = get(annotationClass);
        if(annotations != null) {
            return annotations.get(key);
        } else {
            return null;
        }
    }

    /**
     * Equivalent to <code>get(from,to,new AnnotationClass(annotationClass))</code>
     */
    public Annotations get(int from, int to, String annotationClass)  {
        return get(from, to, new AnnotationClass(annotationClass));
    }

     /**
     * Gets an annotation that is set on a substring.
     *
     * This function first tries to find an annotation of annotationClass that
     * describe the range (from,to). If that does not exist, it tries to find the smallest range
     * which both contain (from,to) and has an annotation of annotationClass.
     * If that does not exist, null is returned.
     *
     * For example, if these annotations has been set for the text "new york city":
     * i.annotate(0,3,"token") //new
     * i.annotate(4,8,"token") //york
     * i.annotate(9,13,"city") //tokem
     * i.annotate(0,8,"city") //new york
     * i.annotate(0,13,"city") //new york city
     *
     * then:
     *
     * i.get(0,3,"token") //returns "token" - annotation for"new"
     * i.get(0,3,"city") //returns "city" - annotation for "new york"
     * i.get(9,13,"city") //returns "city" - annotation for "new york city"
     *
     * @param from start of the substring
     * @param to end of the substring
     * @param annotationClass class of the annotation
     * @return the anno
     */
    public Annotations get(int from, int to, AnnotationClass annotationClass ) {
        return rootSpan.getAnnotation(from, to, annotationClass);
    }

    /**
     * Get the value of a property set on a substring annotation.
     *
     * If the annotation or the key/value pair does not exists, null
     * is returned.
     *
     */
    public Object get(int from, int to, String annotationClass, String key) {
        Annotations annotations = get(from, to, annotationClass);
        if (annotations != null) {
            return annotations.get(key);
        } else {
            return null;
        }
    }

    /**
     * Gets all the annotationclasses that describes the text.
     */
    public Set<AnnotationClass> getClasses() {
        return rootSpan.getClasses();
    }

    /**
     * Gets all annotationclasses that describe a substring
     */
    public Set<AnnotationClass> getClasses(int from,int to) {
        return rootSpan.getClasses(from,to);
    }


    /**
     * Gets the lowermost spans (usually the spans marked with token).
     */
    public List<Span> getTokens() {
        return rootSpan.getTokens();
    }

    /**
     * Returns all spans that consists of the term given. If no span with that term exists,
     * the empty list is returned.
     */
    public List<Span> getTermSpans(String term) {
        return rootSpan.getTermSpans(term);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        Map<AnnotationClass, List<Annotations>> annotations = getAll();
        Iterator<Map.Entry<AnnotationClass,List<Annotations>>> mapIterator = annotations.entrySet().iterator();
        while (mapIterator.hasNext()) {
            Map.Entry<AnnotationClass, List<Annotations>> entry = mapIterator.next();
            Iterator<Annotations> annoIterator = entry.getValue().iterator();
            sb.append(entry.getKey()).append(" : [");

            while (annoIterator.hasNext()) {
                Annotations annotation = annoIterator.next();
                sb.append("\"").append(annotation.getSubString()).append("\"");
                dumpAnnotation(sb, annotation);
                if(annoIterator.hasNext()) {
                    sb.append(",");
                }
            }
            sb.append("]");
            if(mapIterator.hasNext()) {
                sb.append(", ");
            }
        }
        sb.append(")");
        return sb.toString();
    }

    private void dumpAnnotation(StringBuilder sb, Annotations annotations) {
        if (annotations.getMap().size() > 0) {
            sb.append(" : {");
            Iterator<Map.Entry<String,Object>> valueIterator = annotations.getMap().entrySet().iterator();
            while(valueIterator.hasNext()) {
                Map.Entry<String,Object> value = valueIterator.next();
                sb.append(value.getKey()).append(" : ").append(value.getValue());
                if(valueIterator.hasNext()) {
                    sb.append(", ");
                }
            }
            sb.append("}");
        }
    }

}
