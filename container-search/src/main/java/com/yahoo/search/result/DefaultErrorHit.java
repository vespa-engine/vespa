// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import com.yahoo.collections.ArraySet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A hit which holds a list of error conditions in a result.
 *
 * @author bratseth
 * @author Steinar Knutsen
 */
public class DefaultErrorHit extends Hit implements ErrorHit, Cloneable {

    // TODO: Check that nobody implements ErrorHit, rename this to ErrorHit, and make an empty, deprecated subclass DefaultErrorHit
    
    /**
     * A list of unique error messages, where the first is considered the "main"
     * error. It should always contain at least one error.
     */
    private List<ErrorMessage> errors = new ArrayList<>();

    /**
     * Creates an error hit with one error
     *
     * @param source the name of the source or backend of this hit
     * @param error an initial error to add to this hit, cannot be null
     */
    public DefaultErrorHit(String source, ErrorMessage error) {
        super("error:" + source, new Relevance(Double.POSITIVE_INFINITY), source);
        addError(error);
    }

    /**
     * Creates an error hit with a list of errors
     *
     * @param source the name of the source or backend of this hit
     * @param errors a list of errors for this to hold. The list will not be modified or retained.
     */
    public DefaultErrorHit(String source, List<ErrorMessage> errors) {
        super("error:" + source, new Relevance(Double.POSITIVE_INFINITY), source);
        for (ErrorMessage error : errors)
            addError(error);
    }

    public void setSource(String source) {
        super.setSource(source);
        for (Iterator<ErrorMessage> i = errorIterator(); i.hasNext();) {
            ErrorMessage error = i.next();

            if (error.getSource() == null) {
                error.setSource(source);
            }
        }
    }

    /**
     * This is basically a way of making a list simulate a set.
     */
    private void removeAndAdd(ErrorMessage error) {
        errors.remove(error);
        errors.add(error);
    }

    /** Adds an error to this */
    public void addError(ErrorMessage error) {
        if (error.getSource() == null)
            error.setSource(getSource());
        removeAndAdd(error);
    }


    /** Add all errors from another error hit to this */
    public void addErrors(ErrorHit errorHit) {
        if (this == errorHit) return;
        for (Iterator<? extends ErrorMessage> i = errorHit.errorIterator(); i.hasNext();) {
            addError(i.next());
        }
    }

    /**
     * Returns all the detail errors of this error hit, not including the main error.
     * The iterator is modifiable.
     */
    public Iterator<ErrorMessage> errorIterator() {
        return errors.iterator();
    }

    /** Returns a read-only set containing all the error of this */
    public Set<ErrorMessage> errors() {
        Set<ErrorMessage> s = new ArraySet<>(errors.size());
        s.addAll(errors);
        return s;
    }

    @Override
    public String toString() {
        return "Error: " + errors.get(0).toString();
    }

    /** Returns true - this is a meta hit containing information on other hits */
    public boolean isMeta() {
        return true;
    }

    /**
     * Returns true if all errors in this have the given code
     */
    public boolean hasOnlyErrorCode(int code) {
        for (ErrorMessage error : errors) {
            if (error.getCode() != code)
                return false;
        }
        return true;
    }

    public DefaultErrorHit clone() {
        DefaultErrorHit clone = (DefaultErrorHit) super.clone();

        clone.errors = new ArrayList<>(this.errors);
        return clone;
    }

}
