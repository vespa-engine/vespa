// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_executor.h"
#include "i_attribute_manager.h"
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/vespalib/util/isequencedtaskexecutor.h>
#include <future>

using search::AttributeVector;

namespace proton {

AttributeExecutor::AttributeExecutor(std::shared_ptr<IAttributeManager> mgr,
                                     std::shared_ptr<AttributeVector> attr)
    : _mgr(std::move(mgr)),
      _attr(std::move(attr))
{
}

AttributeExecutor::~AttributeExecutor() = default;

void
AttributeExecutor::run_sync(std::function<void()> task) const
{
    vespalib::string name = _attr->getNamePrefix();
    auto& writer = _mgr->getAttributeFieldWriter();
    std::promise<void> promise;
    auto future = promise.get_future();
    auto id = writer.getExecutorIdFromName(name);
    writer.execute(id, [&task, promise=std::move(promise)]() mutable { task(); promise.set_value(); });
    future.wait();
}

} // namespace proton
