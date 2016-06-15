// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.dummy;

import com.yahoo.document.fieldset.FieldSet;
import com.yahoo.persistence.spi.Bucket;
import com.yahoo.persistence.spi.PersistenceProvider;
import com.yahoo.persistence.spi.Selection;

import java.util.List;

/**
 * Class to represent an ongoing iterator in dummy persistence.
 */
public class IteratorContext {
    List<Long> timestamps;

    public FieldSet getFieldSet() {
        return fieldSet;
    }

    private FieldSet fieldSet;

    public Bucket getBucket() {
        return bucket;
    }

    private Bucket bucket;

    public Selection getSelection() {
        return selection;
    }

    private Selection selection;

    public PersistenceProvider.IncludedVersions getIncludedVersions() {
        return includedVersions;
    }

    private PersistenceProvider.IncludedVersions includedVersions;

    IteratorContext(Bucket bucket, FieldSet fieldSet, Selection selection,
                    List<Long> timestamps,
                    PersistenceProvider.IncludedVersions versions) {
        this.fieldSet = fieldSet;
        this.bucket = bucket;
        this.selection = selection;
        this.includedVersions = versions;
        this.timestamps = timestamps;
    }

    public List<Long> getTimestamps() { return timestamps; }
}
