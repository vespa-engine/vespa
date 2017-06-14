// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "concurrentqueue.h"

#include <map>
#include <typeinfo>
#include <string>
#include <mutex>
#include <thread>
#include <functional>


namespace filedistribution {
/**
 * Ensures that components are deleted in a separate thread,
 * and that their lifetime is tracked.
 *
 * This prevents situations as e.g. deleting ZKFacade from a zookeeper watcher thread.
 */
class ComponentsDeleter {
    class Worker;
    typedef std::lock_guard<std::mutex> LockGuard;

    std::mutex _trackedComponentsMutex;
    typedef std::map<void*, std::string> TrackedComponentsMap;
    TrackedComponentsMap _trackedComponents;

    typedef std::function<void (void)> CallDeleteFun;
    ConcurrentQueue<CallDeleteFun> _deleteRequests;
    bool _closed;
    std::thread _deleterThread;

    void removeFromTrackedComponents(void* component);

    template <class T>
    void deleteComponent(T* component) {
        removeFromTrackedComponents(component);
        delete component;
    }

    template <class T>
    void requestDelete(T* component) {
        _deleteRequests.push([this, component]() { deleteComponent<T>(component); });
    }

    void waitForAllComponentsDeleted();
    bool areWeDone();
    void close();
  public:
    ComponentsDeleter(const ComponentsDeleter &) = delete;
    ComponentsDeleter & operator = (const ComponentsDeleter &) = delete;
    ComponentsDeleter();

    /*
     *  Waits blocking for up to 60 seconds until all components are deleted.
     *  If it fails, the application is killed.
     */
    ~ComponentsDeleter();

    template <class T>
    std::shared_ptr<T> track(T* t) {
        LockGuard guard(_trackedComponentsMutex);
        if (_closed) {
            return std::shared_ptr<T>(t);
        }

        _trackedComponents[t] = typeid(t).name();
        return std::shared_ptr<T>(t, [this](T * p) { requestDelete<T>(p); });
    }
};
}

