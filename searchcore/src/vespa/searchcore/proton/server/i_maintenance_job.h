// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/time.h>
#include <memory>

namespace proton {

class IBlockableMaintenanceJob;
class IMaintenanceJobRunner;
struct DocumentDBTaggedMetrics;

/**
 * Interface for a maintenance job that is executed after "delay" seconds and
 * then every "interval" seconds.
 */
class IMaintenanceJob
{
private:
    const vespalib::string     _name;
    const vespalib::duration   _delay;
    const vespalib::duration   _interval;

public:
    typedef std::unique_ptr<IMaintenanceJob> UP;

    IMaintenanceJob(const vespalib::string &name,
                    vespalib::duration delay,
                    vespalib::duration interval)
        : _name(name),
          _delay(delay),
          _interval(interval)
    {}

    virtual ~IMaintenanceJob() = default;

    virtual const vespalib::string &getName() const { return _name; }
    virtual vespalib::duration getDelay() const { return _delay; }
    virtual vespalib::duration getInterval() const { return _interval; }
    virtual bool isBlocked() const { return false; }
    virtual IBlockableMaintenanceJob *asBlockable() { return nullptr; }
    virtual void onStop() {}
    virtual void updateMetrics(DocumentDBTaggedMetrics &) {}

    /**
     * Register maintenance job runner, in case event passed to the
     * job causes it to want to be run again.
     */
    virtual void registerRunner(IMaintenanceJobRunner *runner) {
        (void) runner;
    }

    /**
     * Run this maintenance job every "interval" seconds in an external executor thread.
     * It is first executed after "delay" seconds.
     *
     * Return true if the job was finished (it will be executed again in "interval" seconds).
     * Return false if the job was not finished and need to be executed again immediately. This
     * should be used to split up a large job to avoid starvation of other tasks that also are
     * executed on the external executor thread.
     */
    virtual bool run() = 0;
};

} // namespace proton

