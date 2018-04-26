// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "gid_to_lid_change_listener.h"
#include <future>


namespace proton {

GidToLidChangeListener::GidToLidChangeListener(search::ISequencedTaskExecutor &attributeFieldWriter,
                                               std::shared_ptr<search::attribute::ReferenceAttribute> attr,
                                               MonitoredRefCount &refCount,
                                               const vespalib::string &name,
                                               const vespalib::string &docTypeName)
    : _attributeFieldWriter(attributeFieldWriter),
      _executorId(_attributeFieldWriter.getExecutorId(attr->getName())),
      _attr(std::move(attr)),
      _refCount(refCount),
      _name(name),
      _docTypeName(docTypeName)
{
    _refCount.retain();
}
GidToLidChangeListener::~GidToLidChangeListener()
{
    _refCount.release();
}

void
GidToLidChangeListener::notifyPutDone(document::GlobalId gid, uint32_t lid)
{
    std::promise<void> promise;
    auto future = promise.get_future();
    _attributeFieldWriter.executeLambda(_executorId,
                                        [this, &promise, gid, lid]() { _attr->notifyReferencedPut(gid, lid); promise.set_value(); });
    future.wait();
}

void
GidToLidChangeListener::notifyRemove(document::GlobalId gid)
{
    std::promise<void> promise;
    auto future = promise.get_future();
    _attributeFieldWriter.executeLambda(_executorId,
                                        [this, &promise, gid]() { _attr->notifyReferencedRemove(gid); promise.set_value(); });
    future.wait();
}

void
GidToLidChangeListener::notifyRegistered()
{
    std::promise<void> promise;
    auto future = promise.get_future();
    _attributeFieldWriter.executeLambda(_executorId,
                                        [this, &promise]() { _attr->populateTargetLids(); promise.set_value(); });
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
