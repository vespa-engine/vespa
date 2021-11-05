// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mock_field_index_collection.h"
#include "ordered_field_index_inserter.h"

namespace search::memoryindex::test {

MockFieldIndexCollection::MockFieldIndexCollection(FieldIndexRemover& remover,
                                                   OrderedFieldIndexInserterBackend& inserter_backend,
                                                   index::FieldLengthCalculator& calculator)
    : _remover(remover),
      _inserter_backend(inserter_backend),
      _calculator(calculator)
{
}

MockFieldIndexCollection::~MockFieldIndexCollection() = default;

FieldIndexRemover&
MockFieldIndexCollection::get_remover(uint32_t)
{
    return _remover;
}

IOrderedFieldIndexInserter&
MockFieldIndexCollection::get_inserter(uint32_t field_id)
{
    if (_inserters.size() <= field_id) {
        _inserters.resize(field_id + 1);
    }
    auto& inserter = _inserters[field_id];
    if (!inserter) {
        inserter = std::make_unique<OrderedFieldIndexInserter>(_inserter_backend, field_id);
    }
    return *inserter;
}

index::FieldLengthCalculator&
MockFieldIndexCollection::get_calculator(uint32_t)
{
    return _calculator;
}

}
