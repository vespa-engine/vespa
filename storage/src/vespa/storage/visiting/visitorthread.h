// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class VisitorThread
 * @ingroup visiting
 *
 * @brief Thread running visitors.
 *
 * This thread ensures that everything concerning one visitor runs in a
 * single thread. This simplifies the visitors as they don't have to
 * worry about locking, and it is a lot easier to abort visitors when you
 * know other threads isn't using the visitors.
 */

#pragma once

#include "visitor.h"
#include "visitormetrics.h"
#include "visitormessagesessionfactory.h"
#include <vespa/storage/persistence/messages.h>
#include <vespa/storage/common/storagecomponent.h>
#include <vespa/storageframework/generic/metric/metricupdatehook.h>
#include <vespa/storageapi/messageapi/messagehandler.h>
#include <vespa/metrics/metrictimer.h>
#include <vespa/vespalib/util/document_runnable.h>
#include <atomic>
#include <deque>

namespace storage {

namespace framework { class HttpUrlPath; }

class VisitorThread : public framework::Runnable,
                      private api::MessageHandler,
                      private framework::MetricUpdateHook
{
    typedef std::map<std::string, std::shared_ptr<VisitorEnvironment> > LibMap;
    LibMap _libs;

    typedef std::map<api::VisitorId, std::shared_ptr<Visitor> > VisitorMap;
    VisitorMap _visitors;
    std::deque<std::pair<api::VisitorId, framework::SecondTime> > _recentlyCompleted;

    struct Event {
        enum Type {
            MBUS,
            PERSISTENCE,
            NONE
        };

        api::VisitorId _visitorId;
        std::shared_ptr<api::StorageMessage> _message;
        mbus::Reply::UP _mbusReply;

        metrics::MetricTimer _timer;
        Type _type;

        Event() noexcept : _visitorId(0), _message(), _timer(), _type(NONE) {}
        Event(Event&& other) noexcept;
        Event& operator= (Event&& other) noexcept;
        Event(const Event& other) = delete;
        Event& operator= (const Event& other) = delete;
        Event(api::VisitorId visitor, mbus::Reply::UP reply);
        Event(api::VisitorId visitor, const std::shared_ptr<api::StorageMessage>& msg);
        ~Event();

        bool empty() const noexcept {
            return (_type == NONE);
        }
    };

    std::deque<Event>       _queue;
    std::mutex              _lock;
    std::condition_variable _cond;

    VisitorMap::iterator _currentlyRunningVisitor;
    VisitorMessageHandler& _messageSender;
    VisitorThreadMetrics& _metrics;
    uint32_t _threadIndex;
    uint32_t _disconnectedVisitorTimeout;
    uint32_t _ignoreNonExistingVisitorTimeLimit;
    uint32_t _defaultParallelIterators;
    uint32_t _iteratorsPerBucket;
    uint32_t _defaultPendingMessages;
    uint32_t _defaultDocBlockSize;
    uint32_t _visitorMemoryUsageLimit;
    framework::MilliSecTime _defaultDocBlockTimeout;
    framework::MilliSecTime _defaultVisitorInfoTimeout;
    std::atomic<uint32_t> _timeBetweenTicks;
    StorageComponent _component;
    framework::Thread::UP _thread;
    VisitorMessageSessionFactory& _messageSessionFactory;
    VisitorFactory::Map& _visitorFactories;

public:
    VisitorThread(uint32_t threadIndex,
                  StorageComponentRegister&,
                  VisitorMessageSessionFactory&,
                  VisitorFactory::Map&,
                  VisitorThreadMetrics& metrics,
                  VisitorMessageHandler& sender);
    ~VisitorThread() override;

    void processMessage(api::VisitorId visitorId, const std::shared_ptr<api::StorageMessage>& msg);
    void shutdown();
    void setTimeBetweenTicks(uint32_t time) { _timeBetweenTicks.store(time, std::memory_order_relaxed); }
    void handleMessageBusReply(std::unique_ptr<mbus::Reply> reply, Visitor& visitor);

    /** For unit tests needing to pause thread. */
    std::mutex & getQueueMonitor() { return _lock; }

    const VisitorThreadMetrics& getMetrics() const noexcept {
        return _metrics;
    }

private:
    void run(framework::ThreadHandle&) override;
    /**
     * Attempt to fetch an event from the visitor thread's queue. If an event
     * was available, pop it from the queue and return it. If not, return
     * an empty event. This may be checked with the .empty() method on
     * the returned event object.
     */
    Event popNextQueuedEventIfAvailable();
    void tick();
    void trimRecentlyCompletedList(framework::SecondTime currentTime);
    void handleNonExistingVisitorCall(const Event& entry, api::ReturnCode& code);

    std::shared_ptr<Visitor> createVisitor(vespalib::stringref libName,
                                           const vdslib::Parameters& params,
                                           vespalib::asciistream & error);

    bool onCreateVisitor(const std::shared_ptr<api::CreateVisitorCommand>&) override;

    bool onVisitorReply(const std::shared_ptr<api::StorageReply>& reply);
    bool onInternal(const std::shared_ptr<api::InternalCommand>&) override;
    bool onInternalReply(const std::shared_ptr<api::InternalReply>&) override;

    /** Deletes a visitor instance. */
    void close();
    void getStatus(vespalib::asciistream & out, const framework::HttpUrlPath& path) const;
    void updateMetrics(const MetricLockGuard &) override;

};

} // storage
