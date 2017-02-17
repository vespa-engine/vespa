// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "gid_to_lid_change_listener.h"
#include <future>


namespace proton {

GidToLidChangeListener::GidToLidChangeListener(search::ISequencedTaskExecutor &attributeFieldWriter,
                                               std::shared_ptr<search::attribute::ReferenceAttribute> attr,
                                               MonitoredRefCount &refCount)
    : _attributeFieldWriter(attributeFieldWriter),
      _attr(std::move(attr)),
      _refCount(refCount)
{
    _refCount.retain();
}
GidToLidChangeListener::~GidToLidChangeListener()
{
    _refCount.release();
}

void
GidToLidChangeListener::notifyGidToLidChange(document::GlobalId gid, uint32_t lid)
{
    std::promise<bool> promise;
    std::future<bool> future = promise.get_future();
    _attributeFieldWriter.execute(_attr->getName(),
                                  [this, &promise, gid, lid]() { _attr->notifyGidToLidChange(gid, lid); promise.set_value(true); });
    (void) future.get();
}

} // namespace proton
