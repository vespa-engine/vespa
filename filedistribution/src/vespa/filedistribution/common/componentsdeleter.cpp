// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include "componentsdeleter.h"

#include <vespa/log/log.h>
LOG_SETUP(".componentsdeleter");

#include <boost/foreach.hpp>


using namespace filedistribution;

struct ComponentsDeleter::Worker {
    ComponentsDeleter& _parent;

    Worker(ComponentsDeleter* parent)
        :_parent(*parent) {}

    void operator()();
};


void
ComponentsDeleter::Worker::operator()()
{
    while (!boost::this_thread::interruption_requested()) {
        try {
            CallDeleteFun deleteFun = _parent._deleteRequests.pop();
            boost::this_thread::disable_interruption di;
            deleteFun();
        } catch(const std::exception& e) {
            LOG(error, e.what());
        }
    }
}

ComponentsDeleter::ComponentsDeleter()
    :_deleterThread(Worker(this))
{}

ComponentsDeleter::~ComponentsDeleter()
{
    waitForAllComponentsDeleted();
    _deleterThread.interrupt();
    _deleterThread.join();
}

void
ComponentsDeleter::waitForAllComponentsDeleted()
{
    LOG(debug, "Waiting for all components to be deleted");

    for (int i=0; i<600 && !allComponentsDeleted(); ++i) {
            boost::this_thread::sleep(boost::posix_time::milliseconds(100));
    }
    LOG(debug, "Done waiting for all components to be deleted");

    logNotDeletedComponents();

    if (!allComponentsDeleted())
        kill(getpid(), SIGKILL);
}

bool
ComponentsDeleter::allComponentsDeleted()
{
    LockGuard guard(_trackedComponentsMutex);
    return _trackedComponents.empty();
}

void
ComponentsDeleter::logNotDeletedComponents()
{
    LockGuard guard(_trackedComponentsMutex);
    BOOST_FOREACH(TrackedComponentsMap::value_type component, _trackedComponents) {
        LOG(info, "Timed out waiting for component '%s' to be deleted", component.second.c_str());
    }
}

void
ComponentsDeleter::removeFromTrackedComponents(void* component) {
    LockGuard guard(_trackedComponentsMutex);
    if (_trackedComponents.count(component))
        LOG(debug, "Deleting '%s'", _trackedComponents[component].c_str());

    size_t numErased = _trackedComponents.erase(component);
    assert(numErased == 1);
    (void) numErased;
}
