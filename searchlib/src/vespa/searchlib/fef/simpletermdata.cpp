// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simpletermdata.h"

namespace search::fef {

SimpleTermData::SimpleTermData()
    : _weight(0),
      _numTerms(0),
      _termIndex(0),
      _uniqueId(0),
      _query_tensor_name(),
      _fields()
{
}

SimpleTermData::SimpleTermData(const ITermData &rhs)
    : _weight(rhs.getWeight()),
      _numTerms(rhs.getPhraseLength()),
      _uniqueId(rhs.getUniqueId()),
      _query_tensor_name(rhs.query_tensor_name()),
      _fields()
{
    for (size_t i(0), m(rhs.numFields()); i < m; ++i) {
        _fields.push_back(SimpleTermFieldData(rhs.field(i)));
    }
}

SimpleTermData::~SimpleTermData() = default;

}
