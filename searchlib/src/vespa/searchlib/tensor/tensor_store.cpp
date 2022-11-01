// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_store.h"

namespace search::tensor {

TensorStore::TensorStore(vespalib::datastore::DataStoreBase &store)
    : _store(store),
      _compaction_spec()
{ }

TensorStore::~TensorStore() = default;

const DenseTensorStore*
TensorStore::as_dense() const
{
    return nullptr;
}

DenseTensorStore*
TensorStore::as_dense()
{
    return nullptr;
}

}
