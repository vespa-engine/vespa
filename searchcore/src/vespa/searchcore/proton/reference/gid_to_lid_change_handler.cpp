// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "gid_to_lid_change_handler.h"
#include "i_gid_to_lid_change_listener.h"
#include <vespa/searchlib/common/lambdatask.h>
#include <vespa/searchcorespi/index/i_thread_service.h>
#include <vespa/document/base/globalid.h>
#include <assert.h>

using search::makeLambdaTask;


namespace proton {

GidToLidChangeHandler::GidToLidChangeHandler(searchcorespi::index::IThreadService *master)
    : _lock(),
      _listeners(),
      _master(master)
{
}


GidToLidChangeHandler::~GidToLidChangeHandler()
{
    assert(_master == nullptr);
    assert(_listeners.empty());
}

void
GidToLidChangeHandler::notifyGidToLidChange(document::GlobalId gid, uint32_t lid)
{
    for (const auto &listener : _listeners) {
        listener->notifyGidToLidChange(gid, lid);
    }
}

void
GidToLidChangeHandler::close()
{
    lock_guard guard(_lock);
    if (_master != nullptr) {
        assert(_master->isCurrentThread());
        _master = nullptr;
        _listeners.clear();
    }
}

void
GidToLidChangeHandler::addListener(std::unique_ptr<IGidToLidChangeListener> listener)
{
    lock_guard guard(_lock);
    if (_master) {
        auto self(shared_from_this());
        _master->execute(makeLambdaTask([self,listener(std::move(listener))]() mutable { self->performAddListener(std::move(listener)); }));
    } else {
        assert(_listeners.empty());
    }
}


void
GidToLidChangeHandler::performAddListener(std::unique_ptr<IGidToLidChangeListener> listener)
{
    lock_guard guard(_lock);
    if (_master) {
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

void
GidToLidChangeHandler::removeListeners(const vespalib::string &docTypeName,
                                       const std::set<vespalib::string> &keepNames)
{
    lock_guard guard(_lock);
    if (_master) {
        auto self(shared_from_this());
        _master->execute(makeLambdaTask([self,docTypeName,keepNames]() mutable { self->performRemoveListener(docTypeName, keepNames); }));
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
GidToLidChangeHandler::performRemoveListener(const vespalib::string &docTypeName,
                                             const std::set<vespalib::string> &keepNames)
{
    lock_guard guard(_lock);
    if (_master) {
        auto itr = _listeners.begin();
        while (itr != _listeners.end()) {
            if (shouldRemoveListener(**itr, docTypeName, keepNames)) {
                itr = _listeners.erase(itr);
            } else {
                ++itr;
            }
        }
    } else {
        assert(_listeners.empty());
    }
}

} // namespace proton
