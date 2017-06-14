// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <cstdint>

namespace search {

namespace index { class DocIdAndFeatures; }

namespace memoryindex {

/**
 * Interface class for ordered document inserter.
 *
 * Insert order must be properly sorted, by (word, docId)
 */
class IOrderedDocumentInserter {
public:
    virtual ~IOrderedDocumentInserter() {}

    /**
     * Set next word to operate on.
     */
    virtual void setNextWord(const vespalib::stringref word) = 0;

    /**
     * Add (word, docId) tuple with given features.
     */
    virtual void add(uint32_t docId, const index::DocIdAndFeatures &features) = 0;

    /**
     * Remove (word, docId) tuple.
     */
    virtual void remove(uint32_t docId) = 0;

    /*
     * Flush pending changes to postinglist for (_word).
     *
     * _dItr is located at correct position.
     */
    virtual void flush() = 0;

    /*
     * Rewind iterator, to start new pass.
     */
    virtual void rewind() = 0;
};

}
}
