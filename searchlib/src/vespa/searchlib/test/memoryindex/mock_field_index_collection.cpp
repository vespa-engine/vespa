// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mock_field_index_collection.h"
#include "ordered_field_index_inserter.h"

namespace search::memoryindex::test {

MockFieldIndexCollection::MockFieldIndexCollection(FieldIndexRemover& remover,
                                                   OrderedFieldIndexInserter& inserter,
                                                   index::FieldLengthCalculator& calculator)
    : _remover(remover),
      _inserter(inserter),
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
MockFieldIndexCollection::get_inserter(uint32_t)
{
    return _inserter;
}

index::FieldLengthCalculator&
MockFieldIndexCollection::get_calculator(uint32_t)
{
    return _calculator;
}

}
