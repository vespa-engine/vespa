// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simpletermfielddata.h"

namespace search::fef {

SimpleTermFieldData::SimpleTermFieldData(uint32_t fieldId)
    : _fieldId(fieldId),
      _matching_doc_count(0),
      _total_doc_count(1),
      _handle(IllegalHandle)
{
}

SimpleTermFieldData::SimpleTermFieldData(const ITermFieldData &rhs)
    : _fieldId(rhs.getFieldId()),
      _matching_doc_count(rhs.get_matching_doc_count()),
      _total_doc_count(rhs.get_total_doc_count()),
      _handle(rhs.getHandle())
{
}

}
