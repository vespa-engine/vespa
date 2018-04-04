// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::Component
 * \ingroup component
 *
 * \brief Application component class
 *
 * The component class is used to give a component of an application a set of
 * generic tools without depending on the implementation of these.
 *
 * This class should not depend on the actual implementation of what it serves.
 * Neither in the header file nor the object file. Implementations will be
 * provided by the application server.
 *
 * Services given should all be generic features. Application specific stuff
 * we should handle in another way. The types of services offered are split in
 * two.
 *
 *   1. Services component implementation has that it registers in the component
 *      such that application server can access this. Metrics and status
 *      reporting are two such services.
 *   2. Services provided through application server, that the application
 *      server will inject into the component before it is opened for use.
 *      Clock, thread pool and memory management are examples of such services.
 *
 * The services offered with a short summary of what they provide are as
 * follows:
 *
 *  - Status reporters can register themselves as a reported in the component.
 *    A status server, for instance serving status information through a web
 *    server can thus fetch status pages wanted by clients and serve them.
 *    Status reporters thus don't need to know how status information is used.
 *
 *  - A metric set can be registered, with a path for where in the application
 *    metric set it should exist. This way, the components do not have to know
 *    about metric management and the implementation of the metric manager.
 *
 *  - A metric update hook can be registered. This will be called by the metric
 *    implementation at regular intervals or just before snapshotting/reporting.
 *
 *  - A clock object is given. Using a common clock component all over the
 *    application makes us able to fake the clock in testing environments.
 *    Fetching current time is also a somewhat expensive operations we might
 *    do often, so having this common object to fetch it, we can easily
 *    optimize clock fetching as we see fit later.
 *
 *  - A thread pool is given. This makes us able to use a thread pool.
 *    (Allthough currently we don't really need a thread pool, as threads
 *    typically live for the whole lifetime of the server. But currently we are
 *    forced to use a thread pool due to fastos.) Another feature of this is
 *    that the thread interface has built in information needed to detect
 *    deadlocks and report status about thread behavior, such that deadlock
 *    detecting and thread status can be shown without the threads themselves
 *    depending on how this is done.
 *
 *  - A memory manager may also be provided, allowing components to request
 *    memory from a global pool, in order to let the application prioritize
 *    where to use memory. Again, this removes the dependency of how it is
 *    actually implemented to the components using it.
 *
 * Currently it is assumed that components are set up at application
 * initialization time, and that they live as long as the application. Thus no
 * unregister functionality is provided. Services that use registered status
 * reporters or metric sets will shut down before the component is deleted,
 * such that the component can be safely deleted without any unregistering
 * needed.
 */
#pragma once

#include "managedcomponent.h"
#include <vespa/storageframework/generic/thread/runnable.h>
#include <vespa/storageframework/generic/thread/thread.h>
#include <vespa/storageframework/generic/clock/clock.h>
#include <vespa/vespalib/util/sync.h>
#include <atomic>

namespace storage::framework {

class ComponentRegister;

struct ComponentStateListener {
    virtual ~ComponentStateListener() = default;

    virtual void onOpen() {}
    virtual void onClose() {}
};

class Component : private ManagedComponent
{
    ComponentRegister* _componentRegister;
    vespalib::string _name;
    const StatusReporter* _status;
    metrics::Metric* _metric;
    ThreadPool* _threadPool;
    MetricRegistrator* _metricReg;
    std::pair<MetricUpdateHook*, SecondTime> _metricUpdateHook;
    Clock* _clock;
    ComponentStateListener* _listener;
    std::atomic<UpgradeFlags> _upgradeFlag;

    UpgradeFlags loadUpgradeFlag() const {
        return _upgradeFlag.load(std::memory_order_relaxed);
    }

    // ManagedComponent implementation
    metrics::Metric* getMetric() override { return _metric; }
    std::pair<MetricUpdateHook*, SecondTime> getMetricUpdateHook() override { return _metricUpdateHook; }
    const StatusReporter* getStatusReporter() override { return _status; }
    void setMetricRegistrator(MetricRegistrator& mr) override;
    void setClock(Clock& c) override { _clock = &c; }
    void setThreadPool(ThreadPool& tp) override { _threadPool = &tp; }
    void setUpgradeFlag(UpgradeFlags flag) override {
        assert(_upgradeFlag.is_lock_free());
        _upgradeFlag.store(flag, std::memory_order_relaxed);
    }
    void open() override;
    void close() override;

public:
    typedef std::unique_ptr<Component> UP;

    Component(ComponentRegister&, vespalib::stringref name);
    virtual ~Component();

    /**
     * Register a component state listener, getting callbacks when components
     * are started and stopped. An application might want to create all
     * components before starting to do it's work. And it might stop doing work
     * before starting to remove components. Using this listener, components
     * may get callbacks in order to do some initialization after all components
     * are set up, and to do some cleanups before other components are being
     * removed.
     */
    void registerComponentStateListener(ComponentStateListener&);
    /**
     * Register a status page, which might be visible to others through a
     * component showing status of components. Only one status page can be
     * registered per component. Use URI parameters in order to distinguish
     * multiple pages.
     */
    void registerStatusPage(const StatusReporter&);

    /**
     * Register a metric (typically a metric set) used by this component. Only
     * one metric set can be registered per component. Register a metric set in
     * order to register many metrics within the component.
     */
    void registerMetric(metrics::Metric&);

    /**
     * Register a metric update hook. Only one per component. Note that the
     * update hook will only be called if there actually is a metric mananger
     * component registered in the application.
     */
    void registerMetricUpdateHook(MetricUpdateHook&, SecondTime period);

    /**
     * If you need to modify the metric sets that have been registered, you need
     * to hold the metric manager lock while you do it.
     */
    vespalib::MonitorGuard getMetricManagerLock();

    /** Get the name of the component. Must be a unique name. */
    const vespalib::string& getName() const override { return _name; }

    /**
     * Get the thread pool for this application. Note that this call will fail
     * before the application has registered a threadpool. Applications are
     * encouraged to register a threadpool before adding components to avoid
     * needing components to wait before accessing threadpool.
     */
    ThreadPool& getThreadPool() const;

    /**
     * Get the clock used in this application. This function will fail before
     * the application has registered a clock implementation. Applications are
     * encourated to register a clock implementation before adding components to
     * avoid needing components to delay using it.
     */
    Clock& getClock() const { return *_clock; }

    /**
     * Helper functions for components wanting to start a single thread.
     * If max wait time is not set, we assume process time includes waiting.
     * If max process time is not set, deadlock detector cannot detect deadlocks
     * in this thread. (Thus one is not required to call registerTick())
     */
    Thread::UP startThread(Runnable&,
                           MilliSecTime maxProcessTime = MilliSecTime(0),
                           MilliSecTime waitTime = MilliSecTime(0),
                           int ticksBeforeWait = 1);

    // Check upgrade flag settings. Note that this flag may change at any time.
    // Thus the results of these functions should not be cached.
    bool isUpgradingToMajorVersion() const
        { return (loadUpgradeFlag() == UPGRADING_TO_MAJOR_VERSION); }
    bool isUpgradingToMinorVersion() const
        { return (loadUpgradeFlag() == UPGRADING_TO_MINOR_VERSION); }
    bool isUpgradingFromMajorVersion() const
        { return (loadUpgradeFlag() == UPGRADING_FROM_MAJOR_VERSION); }
    bool isUpgradingFromMinorVersion() const
        { return (loadUpgradeFlag() == UPGRADING_FROM_MINOR_VERSION); }

    void requestShutdown(vespalib::stringref reason);

};

}
