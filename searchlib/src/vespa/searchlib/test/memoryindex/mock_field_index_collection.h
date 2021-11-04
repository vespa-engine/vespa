// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/memoryindex/i_field_index_collection.h>

namespace search::index { class FieldLengthCalculator; }
namespace search::memoryindex { class FieldIndexRemover; }

namespace search::memoryindex::test {

class OrderedFieldIndexInserter;

/*
 * Mockup of field index collection used by unit tests.
 */
class MockFieldIndexCollection : public IFieldIndexCollection {
    FieldIndexRemover&            _remover;
    OrderedFieldIndexInserter&    _inserter;
    index::FieldLengthCalculator& _calculator;

public:
    MockFieldIndexCollection(FieldIndexRemover& remover,
                             OrderedFieldIndexInserter& inserter,
                             index::FieldLengthCalculator& calculator);
    ~MockFieldIndexCollection() override;
    FieldIndexRemover& get_remover(uint32_t) override;
    IOrderedFieldIndexInserter& get_inserter(uint32_t) override;
    index::FieldLengthCalculator& get_calculator(uint32_t) override;
};

}
