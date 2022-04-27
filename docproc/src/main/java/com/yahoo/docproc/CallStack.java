// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

import com.yahoo.component.ComponentId;
import com.yahoo.docproc.jdisc.metric.NullMetric;
import com.yahoo.jdisc.Metric;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * A stack of the processors to call next in this processing. To push which
 * processor to call next, call addNext, to get and remove the next processor,
 * call pop.
 *
 * This is not thread safe.
 *
 * @author bratseth
 */
public class CallStack {

    /** The name of this stack, or null if it is not named */
    private String name;

    /** The Call objects of this stack */
    private final List<Call> elements = new java.util.LinkedList<>();

    /** The last element popped from the call stack, if any */
    private Call lastPopped = null;

    /** Used for metrics in Call */
    private final Metric metric;

    public CallStack() {
        this(new NullMetric());
    }

    public CallStack(String name) {
        this(name, new NullMetric());
    }

    /** Creates an empty stack */
    public CallStack(Metric metric) {
        this.name = null;
        this.metric = metric;
    }
    /** Creates an empty stack with a name */
    public CallStack(String name, Metric metric) {
        this.name = name;
        this.metric = metric;
    }

    /**
     * Creates a stack from another stack (starting at the next of the given
     * callstack) This does a deep copy of the stack.
     */
    public CallStack(CallStack stackToCopy) {
        name = stackToCopy.name;
        for (Iterator<Call> i = stackToCopy.iterator(); i.hasNext();) {
            Call callToCopy = i.next();
            elements.add((Call) callToCopy.clone());
        }
        this.metric = stackToCopy.metric;
    }

    /**
     * Creates a stack (with a given name) based on a collection of document processors, which are added to the stack
     * in the iteration order of the collection.
     *
     * @param name the name of the stack
     * @param docprocs the document processors to call
     */
    public CallStack(String name, Collection<DocumentProcessor> docprocs, Metric metric) {
        this(name, metric);
        for (DocumentProcessor docproc : docprocs) {
            addLast(docproc);
        }
    }

    /** Returns the name of this stack, or null if it is not named */
    public String getName() {
        return name;
    }

    /** Sets the name of this stack */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Push an element as the <i>next</i> element on this stack
     *
     * @return this for convenience
     */
    public CallStack addNext(Call call) {
        elements.add(0, call);
        return this;
    }

    /**
     * Push an element as the <i>next</i> element on this stack
     *
     * @return this for convenience
     */
    public CallStack addNext(DocumentProcessor processor) {
        return addNext(new Call(processor, name, metric));
    }

    /**
     * Push multiple elements as the <i>next</i> elements on this stack
     *
     * @return this for convenience
     */
    public CallStack addNext(CallStack callStack) {
        elements.addAll(0, callStack.elements);
        return this;
    }

    /**
     * Adds an element as the <i>last</i> element on this stack
     *
     * @return this for convenience
     */
    public CallStack addLast(Call call) {
        elements.add(call);
        return this;
    }

    /**
     * Adds an element as the <i>last</i> element on this stack
     *
     * @return this for convenience
     */
    public CallStack addLast(DocumentProcessor processor) {
        return addLast(new Call(processor, name, metric));
    }

    /**
     * Adds multiple elements as the <i>last</i> elements on this stack
     *
     * @return this for convenience
     */
    public CallStack addLast(CallStack callStack) {
        elements.addAll(callStack.elements);
        return this;
    }

    /**
     * Adds an element just before the first occurence of some other element on
     * the stack. This can not be called during an iteration.
     *
     * @param before
     *            the call to add this before. If this call is not present (the
     *            same object instance), new processor is added as the last
     *            element
     * @param call the call to add
     * @return this for convenience
     */
    public CallStack addBefore(Call before, Call call) {
        int insertPosition = elements.indexOf(before);
        if (insertPosition < 0) {
            addLast(call);
        } else {
            elements.add(insertPosition, call);
        }
        return this;
    }

    /**
     * Adds an element just before the first occurence of some element on the
     * stack. This can not be called during an iteration.
     *
     * @param before
     *            the call to add this before. If this call is not present (the
     *            same object instance), the new processor is added as the last
     *            element
     * @param processor the processor to add
     * @return this for convenience
     */
    public CallStack addBefore(Call before, DocumentProcessor processor) {
        return addBefore(before, new Call(processor, name, metric));
    }

    /**
     * Adds multiple elements just before the first occurence of some element on
     * the stack. This can not be called during an iteration.
     *
     * @param before the call to add this before. If this call is not present (the
     *               same object instance), the new processor is added as the last element
     * @param callStack the calls to add
     * @return this for convenience
     */
    public CallStack addBefore(Call before, CallStack callStack) {
        int insertPosition = elements.indexOf(before);
        if (insertPosition < 0) {
            addLast(callStack);
        } else {
            elements.addAll(insertPosition, callStack.elements);
        }
        return this;
    }

    /**
     * Adds an element just after the first occurence of some other element on
     * the stack. This can not be called during an iteration.
     *
     * @param after
     *            the call to add this before. If this call is not present, (the
     *            same object instance), the new processor is added as the last
     *            element
     * @param call
     *            the call to add
     * @return this for convenience
     */
    public CallStack addAfter(Call after, Call call) {
        int insertPosition = elements.indexOf(after);
        if (insertPosition < 0) {
            addLast(call);
        } else {
            elements.add(insertPosition + 1, call);
        }
        return this;
    }

    /**
     * Adds an element just after the first occurence of some other element on
     * the stack. This can not be called during an iteration.
     *
     * @param after the call to add this after. If this call is not present, (the
     *              same object instance), the new processor is added as the last element
     * @param processor the processor to add
     * @return this for convenience
     */
    public CallStack addAfter(Call after, DocumentProcessor processor) {
        return addAfter(after, new Call(processor, name, metric));
    }

    /**
     * Adds multiple elements just after another given element on the stack.
     * This can not be called during an iteration.
     *
     * @param after the call to add this before. If this call is not present, (the
     *              same object instance), the new processor is added as the last element
     * @param callStack the calls to add
     * @return this for convenience
     */
    public CallStack addAfter(Call after, CallStack callStack) {
        int insertPosition = elements.indexOf(after);
        if (insertPosition < 0) {
            addLast(callStack);
        } else {
            elements.addAll(insertPosition + 1, callStack.elements);
        }
        return this;
    }

    /**
     * Removes the given call. Does nothing if the call is not present.
     *
     * @param call
     *            the call to remove
     * @return this for convenience
     */
    public CallStack remove(Call call) {
        for (ListIterator<Call> i = iterator(); i.hasNext();) {
            Call current = i.next();
            if (current == call) {
                i.remove();
            }
        }
        return this;
    }

    /**
     * Returns whether this stack has this call (left)
     *
     * @param call
     *            the call to check
     * @return true if the call is present, false otherwise
     */
    public boolean contains(Call call) {
        for (ListIterator<Call> i = iterator(); i.hasNext();) {
            Call current = i.next();
            if (current == call) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the next call to this processor id, or null if no such calls are left
     */
    public Call findCall(ComponentId processorId) {
        for (Iterator<Call> i = iterator(); i.hasNext();) {
            Call call = i.next();
            if (call.getDocumentProcessorId().equals(processorId)) {
                return call;
            }
        }
        return null;
    }

    /**
     * Returns the next call to this processor, or null if no such calls are
     * left
     */
    public Call findCall(DocumentProcessor processor) {
        return findCall(processor.getId());
    }

    /**
     * Returns and removes the next element, or null if there are no more elements
     */
    public Call pop() {
        if (elements.isEmpty()) return null;
        lastPopped = elements.remove(0);
        return lastPopped;
    }

    /**
     * Returns the next element without removing it, or null if there are no
     * more elements
     */
    public Call peek() {
        if (elements.isEmpty()) return null;
        return elements.get(0);
    }

    /**
     * Returns the element that was last popped from this stack, or null if none
     * have been popped or the stack is empty
     */
    public Call getLastPopped() {
        return lastPopped;
    }

    public void clear() {
        elements.clear();
    }

    /**
     * Returns a modifiable ListIterator over all the remaining elements of this
     * stack, starting by the next element
     */
    public ListIterator<Call> iterator() {
        return elements.listIterator();
    }

    /** Returns the number of remaining elements in this stack */
    public int size() {
        return elements.size();
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("callstack");
        if (name != null) {
            b.append(" ");
            b.append(name);
        }
        b.append(":");
        for (Iterator<Call> i = iterator(); i.hasNext();) {
            b.append("\n");
            b.append("  ");
            b.append(i.next().toString());
        }
        b.append("\n");
        return b.toString();
    }

    public Metric getMetric() {
        return metric;
    }

}
