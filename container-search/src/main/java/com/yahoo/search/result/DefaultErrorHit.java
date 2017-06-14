// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import com.yahoo.collections.ArraySet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A hit which holds information on error conditions in a result.
 * En error hit maintains a main error - the main error of the result.
 *
 * @author bratseth
 * @author Steinar Knutsen
 */
public class DefaultErrorHit extends Hit implements ErrorHit, Cloneable {

    /**
     * A list of unique error messages, where the first is considered the "main"
     * error. It should always contain at least one error.
     */
    private List<ErrorMessage> errors = new ArrayList<>();

    /**
     * Creates an error hit with a main error
     *
     * @param source the name of the source or backend of this hit
     * @param error an initial main error to add to this hit, cannot be null
     */
    public DefaultErrorHit(String source, ErrorMessage error) {
        super("error:" + source, new Relevance(Double.POSITIVE_INFINITY), source);
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
     * Returns the main error of this result, never null.
     *
     * @deprecated since 5.18, use {@link #errors()}
     */
    @Override
    @Deprecated
    public ErrorMessage getMainError() {
        return errors.get(0);
    }

    /**
     * This is basically a way of making a list simulate a set.
     */
    private void removeAndAdd(ErrorMessage error) {
        errors.remove(error);
        errors.add(error);
    }

    /**
     * Adds an error to this. This may change the main error
     * and/or the list of detailed errors
     */
    public void addError(ErrorMessage error) {
        if (error.getSource() == null) {
            error.setSource(getSource());
        }
        removeAndAdd(error);
    }


    /** Add all errors from another error hit to this */
    public void addErrors(ErrorHit errorHit) {
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
