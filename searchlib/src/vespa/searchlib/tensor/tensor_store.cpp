// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_store.h"

namespace search::tensor {

TensorStore::TensorStore(vespalib::datastore::DataStoreBase &store)
    : _store(store),
      _typeId(0)
{ }

TensorStore::~TensorStore() = default;

}
