// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.api;

import com.yahoo.vespa.http.client.Result;
import com.yahoo.vespa.http.client.core.Document;
import net.jcip.annotations.Immutable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The result of an operation written to an OutputStream returned by
 * {@link com.yahoo.vespa.http.client.Session#stream(CharSequence)}. A Result refers to a single document,
 * but may contain more than one Result.Detail instances, as these pertains to a
 * single endpoint, and a Result may wrap data for multiple endpoints.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.20
 */
@Immutable
final public class ResultImpl extends Result {
    private final Document document;
    private final boolean success;
    private final boolean _transient;
    private final boolean isConditionNotMet;
    private final List<Detail> details;
    private final String localTrace;

    public ResultImpl(Document document, Collection<Detail> values, StringBuilder localTrace) {
        this.document = document;
        this.details = Collections.unmodifiableList(new ArrayList<>(values));
        boolean totalSuccess = true;
        boolean totalTransient = true;
        boolean isConditionNotMet = true;
        for (Detail d : details) {
            if (!d.isSuccess()) {totalSuccess = false; }
            if (!d.isTransient()) {totalTransient = false; }
            if (!d.isConditionNotMet()) { isConditionNotMet = false; }
        }
        this.success = totalSuccess;
        this._transient = totalTransient;
        this.isConditionNotMet = isConditionNotMet;
        this.localTrace = localTrace == null ? null : localTrace.toString();
    }

    @Override
    public String getDocumentId() {
        return document.getDocumentId();
    }

    @Override
    public CharSequence getDocumentDataAsCharSequence() {
        return document.getDataAsString();
    }

    @Override
    public Object getContext() {
        return document.getContext();
    }

    @Override
    public boolean isSuccess() {
        return success;
    }

    @Override
    public boolean isTransient() {
        return _transient;
    }

    @Override
    public boolean isConditionNotMet() { return isConditionNotMet; }


    @Override
    public List<Detail> getDetails() { return details; }

    @Override
    public boolean hasLocalTrace() {
        return localTrace != null;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("Result for '").append(document.getDocumentId());
        if (localTrace != null) {
            b.append(localTrace);
        }
        return b.toString();
    }
}
