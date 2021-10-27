// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/memoryindex/field_index_collection.h>
#include <vespa/searchlib/memoryindex/ordered_field_index_inserter.h>

namespace search::memoryindex::test {

/**
 * Test class used to populate a FieldIndex.
 */
class WrapInserter {
private:
    IOrderedFieldIndexInserter& _inserter;

public:
    WrapInserter(FieldIndexCollection& field_indexes, uint32_t field_id)
        : _inserter(field_indexes.getFieldIndex(field_id)->getInserter())
    {
    }

    WrapInserter(IFieldIndex& field_index)
            : _inserter(field_index.getInserter())
    {
    }

    WrapInserter& word(vespalib::stringref word_) {
        _inserter.setNextWord(word_);
        return *this;
    }

    WrapInserter& add(uint32_t doc_id, const index::DocIdAndFeatures& features) {
        _inserter.add(doc_id, features);
        return *this;
    }

    WrapInserter& add(uint32_t doc_id) {
        index::DocIdAndPosOccFeatures features;
        features.addNextOcc(0, 0, 1, 1);
        return add(doc_id, features);
    }

    WrapInserter& remove(uint32_t doc_id) {
        _inserter.remove(doc_id);
        return *this;
    }

    WrapInserter& flush() {
        _inserter.flush();
        return *this;
    }

    WrapInserter& rewind() {
        _inserter.rewind();
        return *this;
    }

    vespalib::datastore::EntryRef getWordRef() {
        return _inserter.getWordRef();
    }
};

}
