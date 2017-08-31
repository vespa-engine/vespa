// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "gid_to_lid_change_handler.h"
#include "i_gid_to_lid_change_listener.h"
#include <vespa/searchlib/common/lambdatask.h>
#include <vespa/searchcorespi/index/i_thread_service.h>
#include <vespa/document/base/globalid.h>
#include <cassert>
#include <vespa/vespalib/stllike/hash_map.hpp>

using search::makeLambdaTask;


namespace proton {

GidToLidChangeHandler::GidToLidChangeHandler()
    : _lock(),
      _listeners(),
      _closed(false),
      _pendingRemove()

{
}


GidToLidChangeHandler::~GidToLidChangeHandler()
{
    assert(_closed);
    assert(_listeners.empty());
    assert(_pendingRemove.empty());
}

void
GidToLidChangeHandler::notifyPut(GlobalId gid, uint32_t lid)
{
    for (const auto &listener : _listeners) {
        listener->notifyPut(gid, lid);
    }
}

void
GidToLidChangeHandler::notifyRemove(GlobalId gid)
{
    for (const auto &listener : _listeners) {
        listener->notifyRemove(gid);
    }
}

void
GidToLidChangeHandler::notifyPut(GlobalId gid, uint32_t lid, SerialNum serialNum)
{
    lock_guard guard(_lock);
    auto itr = _pendingRemove.find(gid);
    if (itr != _pendingRemove.end()) {
        assert(itr->second > serialNum);
        return; // Document has already been removed later on
    }
    notifyPut(gid, lid);
}

void
GidToLidChangeHandler::notifyRemove(GlobalId gid, SerialNum serialNum)
{
    lock_guard guard(_lock);
    auto insRes = _pendingRemove.insert(std::make_pair(gid, serialNum));
    if (!insRes.second) {
        assert(insRes.first->second < serialNum);
        insRes.first->second = serialNum;
    } else {
        notifyRemove(gid);
    }
}

void
GidToLidChangeHandler::notifyRemoveDone(GlobalId gid, SerialNum serialNum)
{
    lock_guard guard(_lock);
    auto itr = _pendingRemove.find(gid);
    assert(itr != _pendingRemove.end() && itr->second >= serialNum);
    if (itr->second == serialNum) {
        _pendingRemove.erase(itr);
    }
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
        _listeners.back()->notifyRegistered();
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
