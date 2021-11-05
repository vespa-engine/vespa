// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bundled_fields_context.h"

namespace search::memoryindex {

BundledFieldsContext::BundledFieldsContext(vespalib::ISequencedTaskExecutor::ExecutorId id)
    : _id(id),
      _fields(),
      _uri_fields()
{
}

BundledFieldsContext::~BundledFieldsContext() = default;

void
BundledFieldsContext::add_field(uint32_t field_id)
{
    _fields.emplace_back(field_id);
}

void
BundledFieldsContext::add_uri_field(uint32_t uri_field_id)
{
    _uri_fields.emplace_back(uri_field_id);
}

}
