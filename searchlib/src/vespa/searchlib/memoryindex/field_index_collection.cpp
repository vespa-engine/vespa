// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "field_index_collection.h"
#include "field_inverter.h"
#include "ordered_field_index_inserter.h"
#include <vespa/searchlib/bitcompression/posocccompression.h>
#include <vespa/searchlib/index/i_field_length_inspector.h>
#include <vespa/vespalib/btree/btree.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/btree/btreenode.hpp>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreenodestore.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/btree/btreestore.hpp>
#include <vespa/vespalib/util/exceptions.h>

namespace search {

using index::DocIdAndFeatures;
using index::IFieldLengthInspector;
using index::Schema;
using index::WordDocElementFeatures;

namespace memoryindex {

FieldIndexCollection::FieldIndexCollection(const Schema& schema, const IFieldLengthInspector& inspector)
    : _fieldIndexes(),
      _numFields(schema.getNumIndexFields())
{
    for (uint32_t fieldId = 0; fieldId < _numFields; ++fieldId) {
        const auto& field = schema.getIndexField(fieldId);
        if (field.use_interleaved_features()) {
            _fieldIndexes.push_back(std::make_unique<FieldIndex<true>>(schema, fieldId,
                                                                       inspector.get_field_length_info(field.getName())));
        } else {
            _fieldIndexes.push_back(std::make_unique<FieldIndex<false>>(schema, fieldId,
                                                                        inspector.get_field_length_info(field.getName())));
        }
    }
}

FieldIndexCollection::~FieldIndexCollection() = default;

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
