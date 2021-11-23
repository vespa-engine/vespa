// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "gid_to_lid_change_handler.h"
#include "i_gid_to_lid_change_listener.h"
#include "pending_gid_to_lid_changes.h"
#include <vespa/vespalib/util/lambdatask.h>
#include <cassert>
#include <vespa/vespalib/stllike/hash_map.hpp>

using vespalib::makeLambdaTask;

namespace proton {

GidToLidChangeHandler::GidToLidChangeHandler()
    : _lock(),
      _listeners(),
      _closed(false),
      _pendingRemove(),
      _pending_changes()
{
}

GidToLidChangeHandler::~GidToLidChangeHandler()
{
    assert(_closed);
    assert(_listeners.empty());
    assert(_pendingRemove.empty());
}

void
GidToLidChangeHandler::notifyPutDone(IDestructorCallbackSP context, GlobalId gid, uint32_t lid)
{
    for (const auto &listener : _listeners) {
        listener->notifyPutDone(context, gid, lid);
    }
}

void
GidToLidChangeHandler::notifyRemove(IDestructorCallbackSP context, GlobalId gid)
{
    for (const auto &listener : _listeners) {
        listener->notifyRemove(context, gid);
    }
}

void
GidToLidChangeHandler::notifyPut(IDestructorCallbackSP context, GlobalId gid, uint32_t lid, SerialNum serial_num)
{
    lock_guard guard(_lock);
    _pending_changes.emplace_back(std::move(context), gid, lid, serial_num, false);
}

void
GidToLidChangeHandler::notifyPutDone(IDestructorCallbackSP context, GlobalId gid, uint32_t lid, SerialNum serialNum)
{
    lock_guard guard(_lock);
    auto itr = _pendingRemove.find(gid);
    if (itr != _pendingRemove.end()) {
        auto &entry = itr->second;
        assert(entry.removeSerialNum != serialNum);
        if (entry.removeSerialNum > serialNum) {
            return; // Document has already been removed later on
        }
        assert(entry.putSerialNum != serialNum);
        if (entry.putSerialNum > serialNum) {
            return; // Document has already been put later on
        }
        entry.putSerialNum = serialNum;
    }
    notifyPutDone(std::move(context), gid, lid);
}

void
GidToLidChangeHandler::notifyRemoves(IDestructorCallbackSP context, const std::vector<GlobalId> & gids, SerialNum serialNum)
{
    lock_guard guard(_lock);
    _pending_changes.reserve(vespalib::roundUp2inN(_pending_changes.size() + gids.size()));
    for (const GlobalId & gid : gids) {
        auto insRes = _pendingRemove.insert(std::make_pair(gid, PendingRemoveEntry(serialNum)));
        if (!insRes.second) {
            auto &entry = insRes.first->second;
            assert(entry.removeSerialNum < serialNum);
            assert(entry.putSerialNum < serialNum);
            if (entry.removeSerialNum < entry.putSerialNum) {
                notifyRemove(std::move(context), gid);
            }
            entry.removeSerialNum = serialNum;
            ++entry.refCount;
        } else {
            notifyRemove(std::move(context), gid);
        }
        _pending_changes.emplace_back(IDestructorCallbackSP(), gid, 0, serialNum, true);
    }
}

void
GidToLidChangeHandler::notifyRemoveDone(GlobalId gid, SerialNum serialNum)
{
    lock_guard guard(_lock);
    auto itr = _pendingRemove.find(gid);
    assert(itr != _pendingRemove.end());
    auto &entry = itr->second;
    assert(entry.removeSerialNum >= serialNum);
    if (entry.refCount == 1) {
        _pendingRemove.erase(itr);
    } else {
        --entry.refCount;
    }
}

std::unique_ptr<IPendingGidToLidChanges>
GidToLidChangeHandler::grab_pending_changes()
{
    lock_guard guard(_lock);
    if (_pending_changes.empty()) {
        return {};
    }
    return std::make_unique<PendingGidToLidChanges>(*this, std::move(_pending_changes));
}

void
GidToLidChangeHandler::close()
{
    Listeners deferredDelete;
    {
        lock_guard guard(_lock);
        _closed = true;
        _listeners.swap(deferredDelete);
    }
}

void
GidToLidChangeHandler::addListener(std::unique_ptr<IGidToLidChangeListener> listener)
{
    lock_guard guard(_lock);
    if (!_closed) {
        const vespalib::string &docTypeName = listener->getDocTypeName();
        const vespalib::string &name = listener->getName();
        for (const auto &oldlistener : _listeners) {
            if (oldlistener->getDocTypeName() == docTypeName && oldlistener->getName() == name) {
                return;
            }
        }
        _listeners.emplace_back(std::move(listener));
        std::vector<GlobalId> removes;
        for (auto& change : _pending_changes) {
            if (change.is_remove()) {
                removes.emplace_back(change.get_gid());
            }
        }
        _listeners.back()->notifyRegistered(removes);
    } else {
        assert(_listeners.empty());
    }
}

namespace {

bool shouldRemoveListener(const IGidToLidChangeListener &listener,
                          const vespalib::string &docTypeName,
                          const std::set<vespalib::string> &keepNames)
{
    return ((listener.getDocTypeName() == docTypeName) &&
            (keepNames.find(listener.getName()) == keepNames.end()));
}

}

void
GidToLidChangeHandler::removeListeners(const vespalib::string &docTypeName,
                                       const std::set<vespalib::string> &keepNames)
{
    Listeners deferredDelete;
    {
        lock_guard guard(_lock);
        if (!_closed) {
            auto itr = _listeners.begin();
            while (itr != _listeners.end()) {
                if (shouldRemoveListener(**itr, docTypeName, keepNames)) {
                    deferredDelete.emplace_back(std::move(*itr));
                    itr = _listeners.erase(itr);
                } else {
                    ++itr;
                }
            }
        } else {
            assert(_listeners.empty());
        }
    }
}

} // namespace proton
