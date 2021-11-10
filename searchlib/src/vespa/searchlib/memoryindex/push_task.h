// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/executor.h>
#include <vector>

namespace vespalib {
class IDestructorCallback;
class RetainGuard;
}

namespace search::memoryindex {

class FieldInverter;
class PushContext;
class UrlFieldInverter;

/*
 * Task to push inverted data from a set of field inverters and uri
 * field inverters to to memory index structure.
 */
class PushTask : public vespalib::Executor::Task
{
    using OnWriteDoneType = const std::shared_ptr<vespalib::IDestructorCallback> &;
    const PushContext&                                    _context;
    const std::vector<std::unique_ptr<FieldInverter>>&    _inverters;
    const std::vector<std::unique_ptr<UrlFieldInverter>>& _uri_inverters;
    std::remove_reference_t<OnWriteDoneType>              _on_write_done;
    std::shared_ptr<vespalib::RetainGuard>                _retain;
public:
    PushTask(const PushContext& context, const std::vector<std::unique_ptr<FieldInverter>>& inverters,  const std::vector<std::unique_ptr<UrlFieldInverter>>& uri_inverters, OnWriteDoneType on_write_done, std::shared_ptr<vespalib::RetainGuard> retain);
    ~PushTask() override;
    void run() override;
};

}
