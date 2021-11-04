// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/memoryindex/i_ordered_field_index_inserter.h>

namespace search::memoryindex::test {

class OrderedFieldIndexInserterBackend;

/*
 * Test version of ordered field index inserter that uses a backend to create
 * a string representation used to validate correct use of ordered field index inserter.
 */
class OrderedFieldIndexInserter : public IOrderedFieldIndexInserter {
    OrderedFieldIndexInserterBackend& _backend;
    uint32_t _field_id;
public:
    OrderedFieldIndexInserter(OrderedFieldIndexInserterBackend& backend, uint32_t field_id);
    ~OrderedFieldIndexInserter() override;
    void setNextWord(const vespalib::stringref word) override;
    void add(uint32_t docId, const index::DocIdAndFeatures &features) override;
    vespalib::datastore::EntryRef getWordRef() const override;
    void remove(uint32_t docId) override;
    void flush() override;
    void commit() override;
    void rewind() override;
};

}
