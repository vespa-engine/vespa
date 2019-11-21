// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simpletermdata.h"

namespace search {
namespace fef {

SimpleTermData::SimpleTermData()
    : _weight(0),
      _numTerms(0),
      _termIndex(0),
      _uniqueId(0),
      _fields()
{
}

SimpleTermData::SimpleTermData(const ITermData &rhs)
    : _weight(rhs.getWeight()),
      _numTerms(rhs.getPhraseLength()),
      _uniqueId(rhs.getUniqueId()),
      _fields()
{
    for (size_t i(0), m(rhs.numFields()); i < m; ++i) {
        _fields.push_back(SimpleTermFieldData(rhs.field(i)));
    }
}

} // namespace fef
} // namespace search
