// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "componentsdeleter.h"

#include <vespa/log/log.h>
LOG_SETUP(".componentsdeleter");

using namespace std::literals;
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
    while ( ! _parent.areWeDone() ) {
        CallDeleteFun deleteFun = _parent._deleteRequests.pop();
        deleteFun();
    }
}

ComponentsDeleter::ComponentsDeleter() :
    _closed(false),
    _deleterThread(Worker(this))
{}

ComponentsDeleter::~ComponentsDeleter()
{
    close();
    waitForAllComponentsDeleted();
    _deleterThread.join();
}

void
ComponentsDeleter::waitForAllComponentsDeleted()
{
    LOG(debug, "Waiting for all components to be deleted");

    for (int i=0; i<600 && !areWeDone(); ++i) {
            std::this_thread::sleep_for(100ms);
    }
    LOG(debug, "Done waiting for all components to be deleted");
    assert(_trackedComponents.empty());
    assert(_deleteRequests.empty());
}
 
void
ComponentsDeleter::close()
{
    {
        LockGuard guard(_trackedComponentsMutex);
        _closed = true;
    }
    _deleteRequests.push([]() { LOG(debug, "I am the last one, hurry up and shutdown"); });
}

bool
ComponentsDeleter::areWeDone()
{
    LockGuard guard(_trackedComponentsMutex);
    return _closed && _trackedComponents.empty() && _deleteRequests.empty();
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
