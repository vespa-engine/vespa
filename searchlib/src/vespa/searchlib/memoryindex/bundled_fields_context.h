// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/isequencedtaskexecutor.h>

namespace search::memoryindex {

/*
 * Base class for PushContext and InvertContext, with mapping to
 * the fields and uri fields handled by this context. Fields using
 * the same thread appear in the same context.
 */
class BundledFieldsContext
{
    vespalib::ISequencedTaskExecutor::ExecutorId _id;
    std::vector<uint32_t>                        _fields;
    std::vector<uint32_t>                        _uri_fields;
protected:
    BundledFieldsContext(vespalib::ISequencedTaskExecutor::ExecutorId id);
    ~BundledFieldsContext();
public:
    void add_field(uint32_t field_id);
    void add_uri_field(uint32_t uri_field_id);
    void set_id(vespalib::ISequencedTaskExecutor::ExecutorId id) { _id = id; }
    vespalib::ISequencedTaskExecutor::ExecutorId get_id() const noexcept { return _id; }
    const std::vector<uint32_t>& get_fields() const noexcept { return _fields; }
    const std::vector<uint32_t>& get_uri_fields() const noexcept { return _uri_fields; }
};

}
