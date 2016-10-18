// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "tensor_store.h"
#include <vespa/searchlib/btree/datastore.hpp>

namespace search {

namespace attribute {

constexpr size_t MIN_BUFFER_CLUSTERS = 1024;

TensorStore::TensorStore()
    : _store(),
      _type(RefType::align(1),
            MIN_BUFFER_CLUSTERS,
            RefType::offsetSize() / RefType::align(1)),
      _typeId(0)
{
    _store.addType(&_type);
    _store.initActiveBuffers();
}


TensorStore::~TensorStore()
{
    _store.dropBuffers();
}


}  // namespace search::attribute

}  // namespace search
