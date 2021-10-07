// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simpletermfielddata.h"

namespace search::fef {

SimpleTermFieldData::SimpleTermFieldData(uint32_t fieldId)
    : ITermFieldData(fieldId),
      _handle(IllegalHandle)
{
}

SimpleTermFieldData::SimpleTermFieldData(const ITermFieldData &rhs)
    : ITermFieldData(rhs),
      _handle(rhs.getHandle())
{
}

}
