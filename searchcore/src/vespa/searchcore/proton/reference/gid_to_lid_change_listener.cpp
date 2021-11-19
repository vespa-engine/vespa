// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "gid_to_lid_change_listener.h"
#include <vespa/vespalib/util/gate.h>
#include <future>

using vespalib::RetainGuard;

namespace proton {

GidToLidChangeListener::GidToLidChangeListener(vespalib::ISequencedTaskExecutor & executor,
                                               std::shared_ptr<search::attribute::ReferenceAttribute> attr,
                                               RetainGuard retainGuard,
                                               const vespalib::string &name,
                                               const vespalib::string &docTypeName)
    : _executor(executor),
      _executorId(_executor.getExecutorIdFromName(attr->getNamePrefix())),
      _attr(std::move(attr)),
      _retainGuard(std::move(retainGuard)),
      _name(name),
      _docTypeName(docTypeName)
{ }

GidToLidChangeListener::~GidToLidChangeListener()
{
    vespalib::Gate gate;
    _executor.executeLambda(_executorId, [&gate]() { gate.countDown(); });
    gate.await();
}

void
GidToLidChangeListener::notifyPutDone(IDestructorCallbackSP context, document::GlobalId gid, uint32_t lid)
{
    _executor.executeLambda(_executorId,
                            [this, context=std::move(context), gid, lid]() {
                                            (void) context;
                                            _attr->notifyReferencedPut(gid, lid);
                                        });
}

void
GidToLidChangeListener::notifyRemove(IDestructorCallbackSP context, document::GlobalId gid)
{
    _executor.executeLambda(_executorId,
                            [this, context = std::move(context), gid]() {
                                            (void) context;
                                            _attr->notifyReferencedRemove(gid);
                                        });
}

void
GidToLidChangeListener::notifyRegistered(const std::vector<document::GlobalId>& removes)
{
    std::promise<void> promise;
    auto future = promise.get_future();
    _executor.executeLambda(_executorId,
                            [this, &promise, removes(std::move(removes))]() {
                                            _attr->populateTargetLids(removes);
                                            promise.set_value();
                                        });
    future.wait();
}

const vespalib::string &
GidToLidChangeListener::getName() const
{
    return _name;
}

const vespalib::string &
GidToLidChangeListener::getDocTypeName() const
{
    return _docTypeName;
}

} // namespace proton
