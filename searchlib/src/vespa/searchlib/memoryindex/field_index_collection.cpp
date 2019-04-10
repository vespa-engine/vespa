// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "field_index_collection.h"
#include "fieldinverter.h"
#include <vespa/searchlib/bitcompression/posocccompression.h>

#include <vespa/searchlib/btree/btreenode.hpp>
#include <vespa/searchlib/btree/btreenodeallocator.hpp>
#include <vespa/searchlib/btree/btreenodestore.hpp>
#include <vespa/searchlib/btree/btreestore.hpp>
#include <vespa/searchlib/btree/btreeiterator.hpp>
#include <vespa/searchlib/btree/btreeroot.hpp>
#include <vespa/searchlib/btree/btree.hpp>
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

MemoryUsage
FieldIndexCollection::getMemoryUsage() const
{
    MemoryUsage usage;
    for (auto &fieldIndex : _fieldIndexes) {
        usage.merge(fieldIndex->getMemoryUsage());
    }
    return usage;
}

}
}
