// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import java.util.Iterator;
import java.util.Set;

/**
 * A hit which holds information on error conditions in a result.
 * An error hit maintains a main error - the main error of the result.
 *
 * @author bratseth
 */
public interface ErrorHit extends Cloneable {

    void setSource(String source);

    /**
     * Adds an error to this. This may change the main error
     * and/or the list of detailed errors
     */
    void addError(ErrorMessage error);

    /** Add all errors from another error hit to this */
    void addErrors(ErrorHit errorHit);

    /**
     * Returns all the detail errors of this error hit, including the main error
     */
    Iterator<? extends ErrorMessage> errorIterator();

    /** Returns a read-only set containing all the error of this, including the main error */
    Set<ErrorMessage> errors();

    /** Returns true - this is a meta hit containing information on other hits */
    boolean isMeta();

    /** Returns true if all errors in this has the given error code */
    boolean hasOnlyErrorCode(int code);

    Object clone();

}
