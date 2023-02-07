// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "persistencethread.h"
#include "persistencehandler.h"
#include <vespa/storageframework/generic/thread/thread.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".persistence.thread");

namespace storage {

PersistenceThread::PersistenceThread(PersistenceHandler & persistenceHandler, FileStorHandler & fileStorHandler,
                                     uint32_t stripeId, framework::Component & component)
    : _persistenceHandler(persistenceHandler),
      _fileStorHandler(fileStorHandler),
      _stripeId(stripeId),
      _thread()
{
    _thread = component.startThread(*this, 60s, 1s, 1, vespalib::CpuUsage::Category::WRITE);
}

PersistenceThread::~PersistenceThread()
{
    LOG(debug, "Shutting down persistence thread. Waiting for current operation to finish.");
    _thread->interrupt();
    LOG(debug, "Waiting for thread to terminate.");
    _thread->join();
    LOG(debug, "Persistence thread done with destruction");
}

void
PersistenceThread::run(framework::ThreadHandle& thread)
{
    LOG(debug, "Started persistence thread");

    vespalib::duration max_wait_time = vespalib::adjustTimeoutByDetectedHz(100ms);
    while (!thread.interrupted()) {
        vespalib::steady_time now = vespalib::steady_clock::now();
        thread.registerTick(framework::UNKNOWN_CYCLE, now);

        vespalib::steady_time deadline = now + max_wait_time;
        FileStorHandler::LockedMessage lock(_fileStorHandler.getNextMessage(_stripeId, deadline));

        if (lock.lock) {
            _persistenceHandler.processLockedMessage(std::move(lock));
        }
    }
    LOG(debug, "Closing down persistence thread");
}

void
PersistenceThread::flush()
{
    //TODO Only need to check for this stripe.
    while (_fileStorHandler.getQueueSize() != 0) {
        std::this_thread::sleep_for(1ms);
    }
}

} // storage
