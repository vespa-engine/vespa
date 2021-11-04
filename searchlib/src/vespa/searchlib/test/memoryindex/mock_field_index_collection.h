// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/memoryindex/i_field_index_collection.h>
#include <memory>
#include <vector>

namespace search::index { class FieldLengthCalculator; }
namespace search::memoryindex { class FieldIndexRemover; }

namespace search::memoryindex::test {

class OrderedFieldIndexInserterBackend;

/*
 * Mockup of field index collection used by unit tests.
 */
class MockFieldIndexCollection : public IFieldIndexCollection {
    FieldIndexRemover&            _remover;
    OrderedFieldIndexInserterBackend& _inserter_backend;
    index::FieldLengthCalculator& _calculator;
    std::vector<std::unique_ptr<IOrderedFieldIndexInserter>> _inserters;

public:
    MockFieldIndexCollection(FieldIndexRemover& remover,
                             OrderedFieldIndexInserterBackend& inserter_backend,
                             index::FieldLengthCalculator& calculator);
    ~MockFieldIndexCollection() override;
    FieldIndexRemover& get_remover(uint32_t) override;
    IOrderedFieldIndexInserter& get_inserter(uint32_t field_id) override;
    index::FieldLengthCalculator& get_calculator(uint32_t) override;
};

}
