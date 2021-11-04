// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ordered_field_index_inserter.h"
#include "ordered_field_index_inserter_backend.h"

namespace search::memoryindex::test {

OrderedFieldIndexInserter::OrderedFieldIndexInserter(OrderedFieldIndexInserterBackend& backend, uint32_t field_id)
    : _backend(backend),
      _field_id(field_id)
{
}

OrderedFieldIndexInserter::~OrderedFieldIndexInserter() = default;

void
OrderedFieldIndexInserter::setNextWord(const vespalib::stringref word)
{
    _backend.setNextWord(word);
}

void
OrderedFieldIndexInserter::add(uint32_t docId, const index::DocIdAndFeatures &features)
{
    _backend.add(docId, features);
}

vespalib::datastore::EntryRef
OrderedFieldIndexInserter::getWordRef() const
{
    return vespalib::datastore::EntryRef();
}

void
OrderedFieldIndexInserter::remove(uint32_t docId)
{
    _backend.remove(docId);
}

void
OrderedFieldIndexInserter::flush()
{
}

void
OrderedFieldIndexInserter::commit()
{
}

void
OrderedFieldIndexInserter::rewind()
{
    _backend.rewind(_field_id);
}

}

