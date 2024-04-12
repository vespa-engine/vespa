// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
      _clock(component.getClock()),
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
        vespalib::steady_time now = _clock.getMonotonicTime();
        thread.registerTick(framework::UNKNOWN_CYCLE, now);

        vespalib::steady_time deadline = now + max_wait_time;
        auto batch = _fileStorHandler.next_message_batch(_stripeId, now, deadline);
        if (!batch.empty()) {
            // Special-case single message batches, as actually scheduling a full batch has more
            // overhead due to extra bookkeeping state and deferred reply-sending.
            if (batch.size() == 1) {
                _persistenceHandler.processLockedMessage(batch.release_as_single_msg());
            } else {
                _persistenceHandler.process_locked_message_batch(std::move(batch.lock), batch.messages);
            }
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
