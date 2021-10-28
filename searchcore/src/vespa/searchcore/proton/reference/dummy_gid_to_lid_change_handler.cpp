// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dummy_gid_to_lid_change_handler.h"
#include "i_pending_gid_to_lid_changes.h"

namespace proton {

DummyGidToLidChangeHandler::DummyGidToLidChangeHandler() = default;

DummyGidToLidChangeHandler::~DummyGidToLidChangeHandler() = default;

void
DummyGidToLidChangeHandler::notifyPut(IDestructorCallbackSP, GlobalId, uint32_t, SerialNum)
{
}

void
DummyGidToLidChangeHandler::notifyRemoves(IDestructorCallbackSP, const std::vector<GlobalId> &, SerialNum)
{
}

std::unique_ptr<IPendingGidToLidChanges>
DummyGidToLidChangeHandler::grab_pending_changes()
{
    return {};
}

void
DummyGidToLidChangeHandler::addListener(std::unique_ptr<IGidToLidChangeListener>)
{
}

void
DummyGidToLidChangeHandler::removeListeners(const vespalib::string &,
                                            const std::set<vespalib::string> &)
{
}

} // namespace proton
