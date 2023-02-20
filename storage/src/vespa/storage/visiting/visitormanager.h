// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::VisitorManager
 * @ingroup storageserver
 *
 * @brief Storage module for handling visitors.
 *
 * This module will dispatch iterator commands to the persistence layer, and
 * feed the results to the correct Visitor modules.  As long as there are
 * active visitors, an iterator is running on the persistence layer. New
 * visitors hook into this stream and remember their starting position. The
 * iterator will loop round the database and visitors receive EOF when they are
 * back at their starting position
 *
 * @author Fledsbo
 * @date 2004-3-30
 * @version $Id$
 */

#pragma once

#include "commandqueue.h"
#include "visitor.h"
#include "visitormetrics.h"
#include "visitorthread.h"
#include <vespa/storage/visiting/config-stor-visitor.h>
#include <vespa/storage/common/storagelink.h>
#include <vespa/storageframework/generic/status/htmlstatusreporter.h>
#include <vespa/storageapi/message/datagram.h>
#include <vespa/storageapi/message/internal.h>
#include <vespa/storageapi/message/visitor.h>
#include <vespa/config/helper/ifetchercallback.h>

namespace config {
    class ConfigUri;
    class ConfigFetcher;
}

namespace storage {

class RequestStatusPageReply;

class VisitorManager : public framework::Runnable,
                       public StorageLink,
                       public framework::HtmlStatusReporter,
                       private VisitorMessageHandler,
                       private config::IFetcherCallback<vespa::config::content::core::StorVisitorConfig>,
                       private framework::MetricUpdateHook
{
private:
    StorageComponentRegister& _componentRegister;
    VisitorMessageSessionFactory& _messageSessionFactory;
    std::vector<std::pair<std::shared_ptr<VisitorThread>,
                          std::map<api::VisitorId, std::string>> > _visitorThread;

    struct MessageInfo {
        api::VisitorId id;
        vespalib::system_time timestamp;
        vespalib::duration timeout;
        std::string destination;
    };

    std::map<api::StorageMessage::Id, MessageInfo> _visitorMessages;
    mutable std::mutex      _visitorLock;
    std::condition_variable _visitorCond;
    uint64_t _visitorCounter;
    std::unique_ptr<config::ConfigFetcher> _configFetcher;
    std::shared_ptr<VisitorMetrics> _metrics;
    uint32_t _maxFixedConcurrentVisitors;
    uint32_t _maxVariableConcurrentVisitors;
    uint32_t _maxVisitorQueueSize;
    std::map<std::string, api::VisitorId> _nameToId;
    StorageComponent _component;
    std::unique_ptr<framework::Thread> _thread;
    CommandQueue<api::CreateVisitorCommand> _visitorQueue;
    std::deque<std::pair<std::string, vespalib::steady_time> > _recentlyDeletedVisitors;
    vespalib::duration _recentlyDeletedMaxTime;

    mutable std::mutex _statusLock; // Only one can get status at a time
    mutable std::condition_variable _statusCond;// Notify when done
    mutable std::vector<std::shared_ptr<RequestStatusPageReply> > _statusRequest;
    bool _enforceQueueUse;
    VisitorFactory::Map _visitorFactories;
public:
    VisitorManager(const config::ConfigUri & configUri,
                   StorageComponentRegister&,
                   VisitorMessageSessionFactory&,
                   VisitorFactory::Map external = VisitorFactory::Map(),
                   bool defer_manager_thread_start = false);
    ~VisitorManager() override;

    void onClose() override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    uint32_t getActiveVisitorCount() const;
    void setTimeBetweenTicks(uint32_t time);

    void setMaxConcurrentVisitors(uint32_t count) { // Used in unit testing
        _maxFixedConcurrentVisitors = count;
        _maxVariableConcurrentVisitors = 0;
    }

    // Used in unit testing
    void setMaxConcurrentVisitors(uint32_t fixed, uint32_t variable) {
        _maxFixedConcurrentVisitors = fixed;
        _maxVariableConcurrentVisitors = variable;
    }

    void setMaxVisitorQueueSize(uint32_t count) { // Used in unit testing
        _maxVisitorQueueSize = count;
    }

    /** For unit testing */
    VisitorThread& getThread(uint32_t index) {
        return *_visitorThread[index].first;
    }
    /** For unit testing */
    bool hasPendingMessageState() const;
    // Must be called exactly once iff manager was created with defer_manager_thread_start == true
    void create_and_start_manager_thread();

    void enforceQueueUsage() { _enforceQueueUse = true; }

private:
    using MonitorGuard = std::unique_lock<std::mutex>;
    void configure(std::unique_ptr<vespa::config::content::core::StorVisitorConfig>) override;
    void run(framework::ThreadHandle&) override;

    /**
     * Schedules a visitor for running. onCreateVisitor will typically call
     * this with skipQueue = false, and closed(id) will typically call it with
     * skipQueue = true to schedule next visitor in queue.
     *
     * @return True if successful, false if failed and reply is sent.
     */
    bool scheduleVisitor(const std::shared_ptr<api::CreateVisitorCommand>&,
                         bool skipQueue, MonitorGuard & visitorLock);

    bool onCreateVisitor(const std::shared_ptr<api::CreateVisitorCommand>&) override;

    bool onDown(const std::shared_ptr<api::StorageMessage>& r) override;
    bool onInternalReply(const std::shared_ptr<api::InternalReply>& r) override;
    bool processReply(const std::shared_ptr<api::StorageReply>&);

    /**
     * Internal function that is used for scheduling the highest
     * priority visitor--if any--for running. Called automatically
     * by closed(id). visitorLock must be held at the time of the call,
     * and will in the case of a successful scheduling be unlocked, as
     * scheduleVisitor() is called internally. If more* visitors are
     * to be attempted scheduled, the lock must first be re-acquired.
     *
     * @return true if a visitor was removed from the queue and scheduled,
     * false otherwise.
     */
    bool attemptScheduleQueuedVisitor(MonitorGuard& visitorLock);

    // VisitorMessageHandler implementation
    void send(const std::shared_ptr<api::StorageCommand>& cmd, Visitor& visitor) override;
    void send(const std::shared_ptr<api::StorageReply>& reply) override;
    void closed(api::VisitorId id) override;

    void reportHtmlStatus(std::ostream&, const framework::HttpUrlPath&) const override;

    /**
     * The maximum amount of concurrent visitors for a priority is given
     * by the formula: fixed + variable * ((255 - priority) / 255)
     */
    uint32_t maximumConcurrent(const api::CreateVisitorCommand& cmd) const {
        return _maxFixedConcurrentVisitors + static_cast<uint32_t>(_maxVariableConcurrentVisitors * ((255.0 - cmd.getPriority()) / 255.0));
    }

    void updateMetrics(const MetricLockGuard &) override;
};

}
