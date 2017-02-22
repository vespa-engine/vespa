// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "gid_to_lid_change_listener.h"
#include <future>


namespace proton {

GidToLidChangeListener::GidToLidChangeListener(search::ISequencedTaskExecutor &attributeFieldWriter,
                                               std::shared_ptr<search::attribute::ReferenceAttribute> attr,
                                               MonitoredRefCount &refCount,
                                               const vespalib::string &name,
                                               const vespalib::string &docTypeName)
    : _attributeFieldWriter(attributeFieldWriter),
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
GidToLidChangeListener::notifyGidToLidChange(document::GlobalId gid, uint32_t lid)
{
    std::promise<bool> promise;
    std::future<bool> future = promise.get_future();
    _attributeFieldWriter.execute(_attr->getName(),
                                  [this, &promise, gid, lid]() { _attr->notifyGidToLidChange(gid, lid); promise.set_value(true); });
    (void) future.get();
}

void
GidToLidChangeListener::notifyRegistered()
{
    std::promise<bool> promise;
    std::future<bool> future = promise.get_future();
    _attributeFieldWriter.execute(_attr->getName(),
                                  [this, &promise]() { _attr->notifyGidToLidChangeListenerRegistered(); promise.set_value(true); });
    (void) future.get();
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
