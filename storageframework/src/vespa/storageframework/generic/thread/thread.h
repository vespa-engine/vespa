// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::Thread
 * \ingroup thread
 *
 * \brief A wrapper for a thread class.
 *
 * This thread class exist to hide the actual implementation of threads used,
 * and to give some extra information about the threads. This is in turned used
 * by monitoring, to be able to see data about the threads running. One such
 * monitoring tool is the deadlock detector.
 */
#pragma once

#include "runnable.h"
#include <vespa/vespalib/stllike/string.h>
#include <condition_variable>

namespace vespalib {
    class Monitor;
}

namespace storage::framework {

class Thread : public ThreadHandle {
    vespalib::string _id;

public:
    typedef std::unique_ptr<Thread> UP;

    Thread(vespalib::stringref id) : _id(id) {}
    virtual ~Thread() {}

    virtual const vespalib::string& getId() const { return _id; }

    /** Check whether thread have been interrupted or not. */
    virtual bool interrupted() const override = 0;
    /** Check whether thread have been joined or not. */
    virtual bool joined() const  = 0;

    /**
     * Call this function to set interrupt flag, such that later calls to
     * interrupt returns true. If called on already interrupted thread it is a
     * noop.
     */
    virtual void interrupt() = 0;
    /**
     * Call this function to wait until thread has finished processing. If
     * called after thread has already finished, it is a noop.
     */
    virtual void join() = 0;

    virtual void updateParameters(vespalib::duration waitTime,
                                  vespalib::duration maxProcessTime,
                                  int ticksBeforeWait) = 0;

    /**
     * Utility function to interrupt and join a thread, possibly broadcasting
     * through a monitor after the signalling face.
     */
    void interruptAndJoin();

    void interruptAndJoin(std::condition_variable &cv);
};

}
