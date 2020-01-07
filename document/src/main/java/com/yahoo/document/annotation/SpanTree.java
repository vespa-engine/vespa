// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;
import com.yahoo.document.CollectionDataType;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.StructuredDataType;
import com.yahoo.document.datatypes.CollectionFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.MapFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.StructuredFieldValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * A SpanTree holds a root node of a tree of SpanNodes, and a List of Annotations pointing to these nodes
 * or each other.&nbsp;It also has a name.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @see com.yahoo.document.annotation.SpanNode
 * @see com.yahoo.document.annotation.Annotation
 */
public class SpanTree implements Iterable<Annotation>, SpanNodeParent, Comparable<SpanTree> {

    private String name;
    private SpanNode root;
    private AnnotationContainer annotations = new ListAnnotationContainer();
    private StringFieldValue stringFieldValue;

    /**
     * WARNING!&nbsp;Only to be used by deserializers!&nbsp;Creates an empty SpanTree instance.
     */
    public SpanTree() {
    }

    /**
     * Creates a new SpanTree with a given root node.
     *
     * @param name the name of the span tree
     * @param root the root node of the span tree
     * @throws IllegalStateException if the root node is invalid
     */
    public SpanTree(String name, SpanNode root) {
        this.name = name;
        setRoot(root);
    }

    /**
     * Creates a new SpanTree with the given name and an empty SpanList as its root node.
     *
     * @param name the name of the span tree
     */
    public SpanTree(String name) {
        this.name = name;
        setRoot(new SpanList());
    }

    @SuppressWarnings("unchecked")
    public SpanTree(SpanTree otherToCopy) {
        name = otherToCopy.name;
        setRoot(copySpan(otherToCopy.root));
        List<Annotation> annotationsToCopy = new ArrayList<Annotation>(otherToCopy.getAnnotations());
        List<Annotation> newAnnotations = new ArrayList<Annotation>(annotationsToCopy.size());

        for (Annotation otherAnnotationToCopy : annotationsToCopy) {
            newAnnotations.add(new Annotation(otherAnnotationToCopy));
        }

        IdentityHashMap<SpanNode, Integer> originalSpanNodes = getSpanNodes(otherToCopy);
        List<SpanNode> copySpanNodes = getSpanNodes();

        for (int i = 0; i < annotationsToCopy.size(); i++) {
            Annotation originalAnnotation = annotationsToCopy.get(i);
            if (!originalAnnotation.isSpanNodeValid()) {  //returns false also if spanNode is null!
                continue;
            }
            Integer indexOfOriginalSpanNode = originalSpanNodes.get(originalAnnotation.getSpanNode());
            if (indexOfOriginalSpanNode == null) {
                throw new IllegalStateException("Could not clone tree, SpanNode of " + originalAnnotation + " not found.");
            }
            newAnnotations.get(i).setSpanNode(copySpanNodes.get(indexOfOriginalSpanNode));
        }

        IdentityHashMap<Annotation, Integer> originalAnnotations = getAnnotations(annotationsToCopy);

        for (Annotation a : newAnnotations) {
            if (!a.hasFieldValue()) {
                continue;
            }
            setCorrectAnnotationReference(a.getFieldValue(), originalAnnotations, newAnnotations);
        }

        for (Annotation a : newAnnotations) {
            annotate(a);
        }
        for (IndexKey key : otherToCopy.getCurrentIndexes()) {
            createIndex(key);
        }
    }

    private void setCorrectAnnotationReference(FieldValue value, IdentityHashMap<Annotation, Integer> originalAnnotations, List<Annotation> newAnnotations) {
        if (value == null) {
            return;
        }

        if (value.getDataType() instanceof AnnotationReferenceDataType) {
            AnnotationReference ref = (AnnotationReference) value;
            if (ref.getReference() == null) {
                return;
            }
            Integer referenceIndex = originalAnnotations.get(ref.getReference());
            if (referenceIndex == null) {
                throw new IllegalStateException("Cannot find Annotation pointed to by " + ref);
            }
            try {
                Annotation newReference = newAnnotations.get(referenceIndex);
                ref.setReference(newReference);
            } catch (IndexOutOfBoundsException ioobe) {
                throw new IllegalStateException("Cannot find Annotation pointed to by " + ref, ioobe);
            }
        } else if (value.getDataType() instanceof StructuredDataType) {
            setCorrectAnnotationReference((StructuredFieldValue) value, originalAnnotations, newAnnotations);
        } else if (value.getDataType() instanceof CollectionDataType) {
            setCorrectAnnotationReference((CollectionFieldValue) value, originalAnnotations, newAnnotations);
        } else if (value.getDataType() instanceof MapDataType) {
            setCorrectAnnotationReference((MapFieldValue) value, originalAnnotations, newAnnotations);
        }
    }

    private void setCorrectAnnotationReference(StructuredFieldValue struct, IdentityHashMap<Annotation, Integer> originalAnnotations, List<Annotation> newAnnotations) {
        for (Field f : struct.getDataType().getFields()) {
            setCorrectAnnotationReference(struct.getFieldValue(f), originalAnnotations, newAnnotations);
        }
    }

    private void setCorrectAnnotationReference(CollectionFieldValue collection, IdentityHashMap<Annotation, Integer> originalAnnotations, List<Annotation> newAnnotations) {
        Iterator it = collection.fieldValueIterator();
        while (it.hasNext()) {
            setCorrectAnnotationReference((FieldValue) it.next(), originalAnnotations, newAnnotations);
        }
    }

    private void setCorrectAnnotationReference(MapFieldValue map, IdentityHashMap<Annotation, Integer> originalAnnotations, List<Annotation> newAnnotations) {
        for (Object o : map.values()) {
            setCorrectAnnotationReference((FieldValue) o, originalAnnotations, newAnnotations);
        }
    }

    private IdentityHashMap<Annotation, Integer> getAnnotations(List<Annotation> annotationsToCopy) {
        IdentityHashMap<Annotation, Integer> map = new IdentityHashMap<Annotation, Integer>();
        for (int i = 0; i < annotationsToCopy.size(); i++) {
            map.put(annotationsToCopy.get(i), i);
        }
        return map;
    }


    private List<SpanNode> getSpanNodes() {
        ArrayList<SpanNode> nodes = new ArrayList<SpanNode>();
        nodes.add(root);
        Iterator<SpanNode> it = root.childIteratorRecursive();
        while (it.hasNext()) {
            nodes.add(it.next());
        }
        return nodes;
    }

    private static IdentityHashMap<SpanNode, Integer> getSpanNodes(SpanTree otherToCopy) {
        IdentityHashMap<SpanNode, Integer> map = new IdentityHashMap<SpanNode, Integer>();
        int spanNodeCounter = 0;
        map.put(otherToCopy.getRoot(), spanNodeCounter++);
        Iterator<SpanNode> it = otherToCopy.getRoot().childIteratorRecursive();
        while (it.hasNext()) {
            map.put(it.next(), spanNodeCounter++);
        }
        return map;
    }

    private SpanNode copySpan(SpanNode spanTree) {
        if (spanTree instanceof Span) {
            return new Span((Span) spanTree);
        } else if (spanTree instanceof AlternateSpanList) {
            return new AlternateSpanList((AlternateSpanList) spanTree);
        } else if (spanTree instanceof SpanList) {
            return new SpanList((SpanList) spanTree);
        } else if (spanTree instanceof DummySpanNode) {
            return spanTree;  //shouldn't really happen
        } else {
            throw new IllegalStateException("Cannot create copy of " + spanTree + " with class "
            + ((spanTree == null) ? "null" : spanTree.getClass()));
        }
    }

    /**
     * WARNING!&nbsp;Only to be used by deserializers!&nbsp;Sets the name of this SpanTree instance.
     *
     * @param name the name to set for this SpanTree instance.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * WARNING!&nbsp;Only to be used by deserializers!&nbsp;Sets the root of this SpanTree instance.
     *
     * @param root the root to set for this SpanTree instance.
     */
    public void setRoot(SpanNode root) {
        if (!root.isValid()) {
            throw new IllegalStateException("Cannot use invalid node " + root + " as root node.");
        }
        if (root.getParent() != null) {
            if (root.getParent() != this) {
                throw new IllegalStateException(root + " is already a child of " + root.getParent() + ", cannot be root of " + this);
            }
        }
        this.root = root;
        root.setParent(this);
    }

    /**
     * Returns the name of this span tree.
     * @return the name of this span tree.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the root node of this span tree.
     * @return the root node of this span tree.
     */
    public SpanNode getRoot() {
        return root;
    }

    /**
     * Convenience shorthand for <code>(SpanList)getRoot()</code>.
     * This must of course only be used when it is known that the root in this tree actually is a SpanList.
     */
    public SpanList spanList() {
        return (SpanList)root;
    }

    /**
     * Ensures consistency of the tree in case SpanNodes have been removed, and there are still
     * Annotations pointing to them. This method has a maximum upper bound of O(3nm), where n is the
     * total number of Annotations, and m is the number of SpanNodes that had been removed from the tree.
     * The lower bound is Omega(n), if no SpanNodes had been removed from the tree.
     */
    @SuppressWarnings("unchecked")
    public void cleanup() {
        Map<Annotation, Annotation> removedAnnotations = removeAnnotationsThatPointToInvalidSpanNodes();

        //here:
        //iterate through all annotations;
        //if any of those have ONLY an annotationreference as its value,
        //    - null reference
        //    - remove value from annotation
        //    - remove annotation and add it to removedAnnotations map
        if (!removedAnnotations.isEmpty()) {
            Iterator<Annotation> annotationIt = iterator();
            while (annotationIt.hasNext()) {
                Annotation a = annotationIt.next();
                if (!a.hasFieldValue()) {
                    continue;
                }
                FieldValue value = a.getFieldValue();

                if (value instanceof AnnotationReference) {
                    //the annotation "a" has a reference
                    AnnotationReference ref = (AnnotationReference) value;
                    if (removedAnnotations.get(ref.getReference()) != null) {
                        //this reference refers to a dead annotation
                        ref.setReference(null);
                        a.setFieldValue(null);
                        if (!a.isSpanNodeValid()) {
                            //this annotation has no span node, delete it
                            annotationIt.remove();
                            removedAnnotations.put(a, a);
                        }
                    }
                }
            }
        }

        //there may still be references to removed annotations,
        //inside maps, weighted sets, structs, etc.
        //if any of those have such references,
        //   - null reference
        //   - remove annotationref from struct, map, etc.
        //   - apart from this, keep struct, map etc. and annotation
        if (!removedAnnotations.isEmpty()) {
            for (Annotation a : this) {
                if (!a.hasFieldValue()) {
                    continue;
                }
                removeObsoleteReferencesFromFieldValue(a.getFieldValue(), removedAnnotations, true);
            }
        }
        //was any annotations removed from the global list? do we still have references to those annotations
        //that have been removed? if so, remove the references
        removeAnnotationReferencesThatPointToRemovedAnnotations();
    }

    private boolean removeObsoleteReferencesFromFieldValue(FieldValue value, Map<Annotation, Annotation> selectedAnnotations, boolean removeIfPresent) {
        if (value == null) {
            return false;
        }

        if (value.getDataType() instanceof AnnotationReferenceDataType) {
            AnnotationReference ref = (AnnotationReference) value;
            if (removeIfPresent) {
                if (selectedAnnotations.containsValue(ref.getReference())) {
                    //this reference refers to a dead annotation
                    ref.setReference(null);
                    return true;
                }
            } else {
                if (!selectedAnnotations.containsValue(ref.getReference())) {
                    //this reference refers to a dead annotation
                    ref.setReference(null);
                    return true;
                }
            }
        } else if (value.getDataType() instanceof StructuredDataType) {
            removeObsoleteReferencesFromStructuredType((StructuredFieldValue) value, selectedAnnotations, removeIfPresent);
        } else if (value.getDataType() instanceof CollectionDataType) {
            removeObsoleteReferencesFromCollectionType((CollectionFieldValue) value, selectedAnnotations, removeIfPresent);
        } else if (value.getDataType() instanceof MapDataType) {
            removeObsoleteReferencesFromMapType((MapFieldValue) value, selectedAnnotations, removeIfPresent);
        }
        return false;
    }

    private boolean removeObsoleteReferencesFromStructuredType(StructuredFieldValue struct, Map<Annotation, Annotation> selectedAnnotations, boolean removeIfPresent) {
        for (Field f : struct.getDataType().getFields()) {
            FieldValue fValue = struct.getFieldValue(f);
            if (removeObsoleteReferencesFromFieldValue(fValue, selectedAnnotations, removeIfPresent)) {
                struct.removeFieldValue(f);
            }
        }
        return false;
    }

    private boolean removeObsoleteReferencesFromCollectionType(CollectionFieldValue collection, Map<Annotation, Annotation> selectedAnnotations, boolean removeIfPresent) {
        Iterator it = collection.fieldValueIterator();
        while (it.hasNext()) {
            FieldValue fValue = (FieldValue) it.next();
            if (removeObsoleteReferencesFromFieldValue(fValue, selectedAnnotations, removeIfPresent)) {
                it.remove();
            }
        }
        return false;
    }

    private boolean removeObsoleteReferencesFromMapType(MapFieldValue map, Map<Annotation, Annotation> selectedAnnotations, boolean removeIfPresent) {
        Iterator valueIt = map.values().iterator();
        while (valueIt.hasNext()) {
            FieldValue fValue = (FieldValue) valueIt.next();
            if (removeObsoleteReferencesFromFieldValue(fValue, selectedAnnotations, removeIfPresent)) {
                valueIt.remove();
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<Annotation, Annotation> removeAnnotationsThatPointToInvalidSpanNodes() {
        Map<Annotation, Annotation> removedAnnotations = new IdentityHashMap<Annotation, Annotation>();

        Iterator<Annotation> annotationIt = iterator();
        while (annotationIt.hasNext()) {
            Annotation a = annotationIt.next();
            if (a.hasSpanNode() && !a.isSpanNodeValid()) {
                a.setSpanNode(null);
                a.setFieldValue(null);
                removedAnnotations.put(a, a);
                annotationIt.remove();
            }
        }
        return removedAnnotations;
    }

    private boolean hasAnyFieldValues() {
        for (Annotation a : this) {
            if (a.hasFieldValue()) {
                return true;
            }
        }
        return false;
    }
    private void removeAnnotationReferencesThatPointToRemovedAnnotations() {
        if (hasAnyFieldValues()) {
            Map<Annotation, Annotation> annotationsStillPresent = new IdentityHashMap<Annotation, Annotation>();
            for (Annotation a : this) {
                annotationsStillPresent.put(a, a);
            }
            for (Annotation a : this) {
                if (!a.hasFieldValue()) {
                    continue;
                }
                //do we have any references to annotations that are NOT in this global list??
                removeObsoleteReferencesFromFieldValue(a.getFieldValue(), annotationsStillPresent, false);
            }
        }
    }

    private void annotateInternal(SpanNode node, Annotation annotation) {
        annotations.annotate(annotation);
    }

    @SuppressWarnings("unchecked")
    private Collection<Annotation> getAnnotations() {
        return annotations.annotations();
    }

    /**
     * Adds an Annotation to the internal list of annotations for this SpanTree.&nbsp;Use this when
     * adding an Annotation that uses an AnnotationReference, and does not annotate a SpanNode.
     *
     * @param a the Annotation to add
     * @return this, for chaining
     * @see com.yahoo.document.annotation.Annotation
     * @see com.yahoo.document.annotation.AnnotationReference
     * @see com.yahoo.document.annotation.AnnotationReferenceDataType
     */
    public SpanTree annotate(Annotation a) {
        if (a.getSpanNode() == null) {
            annotateInternal(DummySpanNode.INSTANCE, a);
        } else {
            annotateInternal(a.getSpanNode(), a);
        }
        return this;
    }

    /**
     * Adds an Annotation to the internal list of annotations for this SpanTree.&nbsp;Use this when
     * adding an Annotation that shall annotate a SpanNode. Upon return, Annotation.getSpanNode()
     * returns the given node.
     *
     * @param node the node to annotate
     * @param annotation the Annotation to add
     * @return this, for chaining
     * @see com.yahoo.document.annotation.Annotation
     */
    public SpanTree annotate(SpanNode node, Annotation annotation) {
        annotation.setSpanNode(node);
        return annotate(annotation);
    }

    /**
     * Adds an Annotation to the internal list of annotations for this SpanTree.&nbsp;Use this when
     * adding an Annotation that shall annotate a SpanNode. Upon return, Annotation.getSpanNode()
     * returns the given node. This one is unchecked and assumes that the SpanNode is valid and has
     * already been attached to the Annotation.
     *
     * @param node the node to annotate
     * @param annotation the Annotation to add
     * @return this, for chaining
     * @see com.yahoo.document.annotation.Annotation
     */
    public final SpanTree annotateFast(SpanNode node, Annotation annotation) {
        annotateInternal(node, annotation);
        return this;
    }

    /**
     * Adds an Annotation.
     * Convenience shorthand for <code>annotate(node,new Annotation(type,value)</code>
     *
     * @param node the node to annotate
     * @param type the type of the Annotation to add
     * @param value the value of the Annotation to add
     * @return this, for chaining
     * @see com.yahoo.document.annotation.Annotation
     */
    public SpanTree annotate(SpanNode node, AnnotationType type,FieldValue value) {
        return annotate(node, new Annotation(type, value));
    }

    /**
     * Creates an Annotation based on the given AnnotationType, and adds it to the internal list of
     * annotations for this SpanTree (convenience method).&nbsp;Use this when
     * adding an Annotation (that does not have a FieldValue) that shall annotate a SpanNode.
     * Upon return, Annotation.getSpanNode()
     * returns the given node.
     *
     * @param node the node to annotate
     * @param type the AnnotationType to create an Annotation from
     * @return this, for chaining
     * @see com.yahoo.document.annotation.Annotation
     * @see com.yahoo.document.annotation.AnnotationType
     */
    public SpanTree annotate(SpanNode node, AnnotationType type) {
        Annotation a = new Annotation(type);
        return annotate(node, a);
    }

    /**
     * Removes an Annotation from the internal list of annotations.
     *
     * @param a the annotation to remove
     * @return true if the Annotation was successfully removed, false otherwise
     */
    public boolean remove(Annotation a) {
        return getAnnotations().remove(a);
    }

    /**
     * Returns the total number of annotations in the tree.
     *
     * @return the total number of annotations in the tree.
     */
    public int numAnnotations() {
        return annotations.annotations().size();
    }

    /**
     * Clears all Annotations for a given SpanNode.
     *
     * @param node the SpanNode to clear all Annotations for.
     */
    public void clearAnnotations(SpanNode node) {
        Iterator<Annotation> annIt = iterator(node);
        while (annIt.hasNext()) {
            annIt.next();
            annIt.remove();
        }
    }

    /**
     * Clears all Annotations for a given SpanNode and its child nodes.
     *
     * @param node the SpanNode to clear all Annotations for.
     */
    public void clearAnnotationsRecursive(SpanNode node) {
        Iterator<Annotation> annIt = iteratorRecursive(node);
        while (annIt.hasNext()) {
            annIt.next();
            annIt.remove();
        }
    }

    /**
     * Returns an Iterator over all annotations in this tree.&nbsp;Note that the iteration order is non-deterministic.
     * @return an Iterator over all annotations in this tree.
     */
    @SuppressWarnings("unchecked")
    public Iterator<Annotation> iterator() {
        return annotations.annotations().iterator();
    }

    /**
     * Returns an Iterator over all annotations that annotate the given node.
     *
     * @param node the node to return annotations for.
     * @return an Iterator over all annotations that annotate the given node.
     */
    @SuppressWarnings("unchecked")
    public Iterator<Annotation> iterator(SpanNode node) {
        return annotations.iterator(node);
    }

    /**
     * Returns a recursive Iterator over all annotations that annotate the given node and its subnodes.
     *
     * @param node the node to recursively return annotations for.
     * @return a recursive Iterator over all annotations that annotate the given node and its subnodes.
     */
    @SuppressWarnings("unchecked")
    public Iterator<Annotation> iteratorRecursive(SpanNode node) {
        return annotations.iteratorRecursive(node);
    }

    /**
     * Returns itself.&nbsp;Needed for this class to be able to be a parent of SpanNodes.
     *
     * @return this SpanTree instance.
     */
    @Override
    public SpanTree getSpanTree() {
        return this;
    }

    /**
     * Sets the StringFieldValue that this SpanTree belongs to.&nbsp;This is called by
     * {@link StringFieldValue#setSpanTree(SpanTree)} and there is no need for the user to call this
     * except in unit tests.
     *
     * @param stringFieldValue the StringFieldValue that this SpanTree should belong to (might be null to clear the current value)
     */
    public void setStringFieldValue(StringFieldValue stringFieldValue) {
        this.stringFieldValue = stringFieldValue;
    }

    /**
     * Returns the StringFieldValue that this SpanTree belongs to.
     *
     * @return the StringFieldValue that this SpanTree belongs to, if any, otherwise null.
     */
    @Override
    public StringFieldValue getStringFieldValue() {
        return stringFieldValue;
    }

    public void createIndex(IndexKey key) {
        if (key == IndexKey.SPAN_NODE && annotations instanceof ListAnnotationContainer) {
            AnnotationContainer tmpAnnotations = new SpanNode2AnnotationContainer();
            tmpAnnotations.annotateAll(annotations.annotations());
            annotations = tmpAnnotations;
        } else if (key == IndexKey.ANNOTATION_TYPE && annotations instanceof ListAnnotationContainer) {
            AnnotationContainer tmpAnnotations = new AnnotationType2AnnotationContainer();
            tmpAnnotations.annotateAll(annotations.annotations());
            annotations = tmpAnnotations;
        } else {
            throw new IllegalArgumentException("Multiple indexes not yet supported. Use clearIndex() or clearIndexes() first.");
        }
    }

    public void clearIndex(IndexKey key) {
        if (key == IndexKey.SPAN_NODE && annotations instanceof SpanNode2AnnotationContainer) {
            clearIndex();
        } else if (key == IndexKey.ANNOTATION_TYPE && annotations instanceof AnnotationType2AnnotationContainer) {
            clearIndex();
        }
    }

    public void clearIndexes() {
        if (!(annotations instanceof ListAnnotationContainer)) {
            clearIndex();
        }
    }

    private void clearIndex() {
        AnnotationContainer tmpAnnotations = new ListAnnotationContainer();
        tmpAnnotations.annotateAll(annotations.annotations());
        annotations = tmpAnnotations;
    }

    public Collection<IndexKey> getCurrentIndexes() {
        if (annotations instanceof AnnotationType2AnnotationContainer)
            return ImmutableList.of(IndexKey.ANNOTATION_TYPE);
        if (annotations instanceof SpanNode2AnnotationContainer)
            return ImmutableList.of(IndexKey.SPAN_NODE);
        return ImmutableList.of();
    }

    @Override
    public String toString() {
        return "SpanTree '" + name + "'";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SpanTree)) return false;

        SpanTree tree = (SpanTree) o;
        if (!annotationsEquals(tree)) return false;
        if (!name.equals(tree.name)) return false;
        if (!root.equals(tree.root)) return false;

        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean annotationsEquals(SpanTree tree) {
        List<Annotation> annotationCollection = new LinkedList<Annotation>(getAnnotations());
        List<Annotation> otherAnnotations = new LinkedList<Annotation>(tree.getAnnotations());

        return annotationCollection.size() == otherAnnotations.size() &&
                ImmutableMultiset.copyOf(annotationCollection).equals(ImmutableMultiset.copyOf(otherAnnotations));
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + root.hashCode();
        result = 31 * result + annotations.hashCode();
        return result;
    }

    @Override
    public int compareTo(SpanTree spanTree) {
        int comp = name.compareTo(spanTree.name);
        if (comp != 0) {
            comp = root.compareTo(spanTree.root);
        }
        return comp;
    }

    public enum IndexKey {
        SPAN_NODE,
        ANNOTATION_TYPE
    }
}
