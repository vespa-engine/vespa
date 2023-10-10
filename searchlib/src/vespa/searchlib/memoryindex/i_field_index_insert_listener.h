// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once
#include <vespa/vespalib/datastore/entryref.h>

namespace search::memoryindex {

/**
 * Interface used to track which {wordRef, docId} pairs that are inserted into a FieldIndex.
 */
class IFieldIndexInsertListener {
public:
    virtual ~IFieldIndexInsertListener() {}

    /**
     * Called when a {wordRef, docId} tuple is inserted into the field index.
     */
    virtual void insert(vespalib::datastore::EntryRef wordRef, uint32_t docId) = 0;

    /**
     * Called to process the set of {wordRef, docId} tuples inserted since last flush().
     */
    virtual void flush() = 0;
};

}

