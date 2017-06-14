// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simpletermfielddata.h"

namespace search {
namespace fef {

SimpleTermFieldData::SimpleTermFieldData(uint32_t fieldId)
    : _fieldId(fieldId),
      _docFreq(0),
      _handle(IllegalHandle)
{
}

SimpleTermFieldData::SimpleTermFieldData(const ITermFieldData &rhs)
    : _fieldId(rhs.getFieldId()),
      _docFreq(rhs.getDocFreq()),
      _handle(rhs.getHandle())
{
}

} // namespace fef
} // namespace search
