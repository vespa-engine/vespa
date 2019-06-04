// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "field_index_collection.h"
#include "field_inverter.h"
#include "ordered_field_index_inserter.h"
#include <vespa/searchlib/bitcompression/posocccompression.h>

#include <vespa/vespalib/btree/btreenode.hpp>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreenodestore.hpp>
#include <vespa/vespalib/btree/btreestore.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/btree/btree.hpp>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.memoryindex.field_index_collection");


namespace search {

using index::DocIdAndFeatures;
using index::WordDocElementFeatures;
using index::Schema;

namespace memoryindex {

FieldIndexCollection::FieldIndexCollection(const Schema & schema)
    : _fieldIndexes(),
      _numFields(schema.getNumIndexFields())
{
    for (uint32_t fieldId = 0; fieldId < _numFields; ++fieldId) {
        auto fieldIndex = std::make_unique<FieldIndex>(schema, fieldId);
        _fieldIndexes.push_back(std::move(fieldIndex));
    }
}

FieldIndexCollection::~FieldIndexCollection()
{
}

void
FieldIndexCollection::dump(search::index::IndexBuilder &indexBuilder)
{
    for (uint32_t fieldId = 0; fieldId < _numFields; ++fieldId) {
        indexBuilder.startField(fieldId);
        _fieldIndexes[fieldId]->dump(indexBuilder);
        indexBuilder.endField();
    }
}

vespalib::MemoryUsage
FieldIndexCollection::getMemoryUsage() const
{
    vespalib::MemoryUsage usage;
    for (auto &fieldIndex : _fieldIndexes) {
        usage.merge(fieldIndex->getMemoryUsage());
    }
    return usage;
}

FieldIndexRemover &
FieldIndexCollection::get_remover(uint32_t field_id)
{
    return _fieldIndexes[field_id]->getDocumentRemover();
}

IOrderedFieldIndexInserter &
FieldIndexCollection::get_inserter(uint32_t field_id)
{
    return _fieldIndexes[field_id]->getInserter();
}

index::FieldLengthCalculator &
FieldIndexCollection::get_calculator(uint32_t field_id)
{
    return _fieldIndexes[field_id]->get_calculator();
}

}
}
