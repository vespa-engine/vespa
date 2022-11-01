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
    _tensorBufferStore(cfg.tensorType(), get_memory_allocator(), 1000u)
{
}


SerializedFastValueAttribute::~SerializedFastValueAttribute()
{
    getGenerationHolder().reclaim_all();
    _tensorStore.reclaim_all_memory();
}

}
