// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/entryref.h>
#include <cstdint>
#include <string>

namespace search::index { class DocIdAndFeatures; }

namespace search::memoryindex {

/**
 * Interface used to insert inverted documents into a FieldIndex,
 * updating the underlying posting lists in that index.
 *
 * Insert order must be properly sorted, first by word, then by docId.
 */
class IOrderedFieldIndexInserter {
public:
    virtual ~IOrderedFieldIndexInserter() = default;

    /**
     * Set next word to operate on.
     */
    virtual void setNextWord(const std::string_view word) = 0;

    /**
     * Add (word, docId) tuple with the given features.
     */
    virtual void add(uint32_t docId, const index::DocIdAndFeatures &features) = 0;

    /**
     * Returns the reference to the current word (only used by unit tests).
     */
    virtual vespalib::datastore::EntryRef getWordRef() const = 0;

    /**
     * Remove (word, docId) tuple.
     */
    virtual void remove(uint32_t docId) = 0;

    /**
     * Flush pending changes for the current word (into the underlying posting list).
     */
    virtual void flush() = 0;

    /*
     * Make current state visible to readers.
     */
    virtual void commit() = 0;

    /**
     * Rewind to prepare for another set of (word, docId) tuples.
     */
    virtual void rewind() = 0;
};

}
