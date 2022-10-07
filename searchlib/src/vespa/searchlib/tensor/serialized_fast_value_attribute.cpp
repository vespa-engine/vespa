// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "serialized_fast_value_attribute.h"
#include <vespa/eval/eval/value.h>
#include <vespa/searchcommon/attribute/config.h>

#include <vespa/log/log.h>

LOG_SETUP(".searchlib.tensor.serialized_fast_value_attribute");

using namespace vespalib;
using namespace vespalib::eval;

namespace search::tensor {

SerializedFastValueAttribute::SerializedFastValueAttribute(stringref name, const Config &cfg)
  : TensorAttribute(name, cfg, _tensorBufferStore),
    _tensor_type(cfg.tensorType()),
    _tensorBufferStore(_tensor_type, {}, 1000u)
{
}


SerializedFastValueAttribute::~SerializedFastValueAttribute()
{
    getGenerationHolder().clearHoldLists();
    _tensorStore.clearHoldLists();
}

void
SerializedFastValueAttribute::setTensor(DocId docId, const vespalib::eval::Value &tensor)
{
    checkTensorType(tensor);
    EntryRef ref = _tensorBufferStore.store_tensor(tensor);
    assert(ref.valid());
    setTensorRef(docId, ref);
}

std::unique_ptr<Value>
SerializedFastValueAttribute::getTensor(DocId docId) const
{
    EntryRef ref;
    if (docId < getCommittedDocIdLimit()) {
        ref = acquire_entry_ref(docId);
    }
    return _tensorBufferStore.get_tensor(ref);
}

}
