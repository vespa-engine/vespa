// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "field_index_base.h"
#include "i_ordered_field_index_inserter.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace search::memoryindex {

vespalib::asciistream &
operator<<(vespalib::asciistream& os, const FieldIndexBase::WordKey& rhs)
{
    os << "wr(" << rhs._wordRef.ref() << ")";
    return os;
}

FieldIndexBase::FieldIndexBase(const index::Schema& schema, uint32_t fieldId)
    : FieldIndexBase(schema, fieldId, index::FieldLengthInfo())
{
}

FieldIndexBase::FieldIndexBase(const index::Schema& schema, uint32_t fieldId,
                               const index::FieldLengthInfo& info)
    : _wordStore(),
      _numUniqueWords(0),
      _generationHandler(),
      _dict(),
      _featureStore(schema),
      _fieldId(fieldId),
      _remover(_wordStore),
      _inserter(),
      _calculator(info)
{
}

FieldIndexBase::~FieldIndexBase() = default;

}

