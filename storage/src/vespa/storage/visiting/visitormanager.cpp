// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "visitormanager.h"
#include "messages.h"
#include "dumpvisitorsingle.h"
#include "countvisitor.h"
#include "testvisitor.h"
#include "recoveryvisitor.h"
#include "reindexing_visitor.h"
#include <vespa/storageframework/generic/thread/thread.h>
#include <vespa/config/subscription/configuri.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/config/helper/configfetcher.hpp>
#include <vespa/storage/common/statusmessages.h>
#include <vespa/vespalib/util/string_escape.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".visitor.manager");

namespace storage {

VisitorManager::VisitorManager(const config::ConfigUri & configUri,
                               StorageComponentRegister& componentRegister,
                               VisitorMessageSessionFactory& messageSF,
                               VisitorFactory::Map externalFactories,
                               bool defer_manager_thread_start)
    : StorageLink("Visitor Manager"),
      framework::HtmlStatusReporter("visitorman", "Visitor Manager"),
      _componentRegister(componentRegister),
      _messageSessionFactory(messageSF),
      _visitorThread(),
      _visitorMessages(),
      _visitorLock(),
      _visitorCond(),
      _visitorCounter(0),
      _configFetcher(std::make_unique<config::ConfigFetcher>(configUri.getContext())),
      _metrics(std::make_shared<VisitorMetrics>()),
      _maxFixedConcurrentVisitors(1),
      _maxVariableConcurrentVisitors(0),
      _maxVisitorQueueSize(1024),
      _nameToId(),
      _component(componentRegister, "visitormanager"),
      _visitorQueue(_component.getClock()),
      _recentlyDeletedVisitors(),
      _recentlyDeletedMaxTime(5s),
      _statusLock(),
      _statusCond(),
      _statusRequest(),
      _enforceQueueUse(false),
      _visitorFactories(std::move(externalFactories))
{
    _configFetcher->subscribe<vespa::config::content::core::StorVisitorConfig>(configUri.getConfigId(), this);
    _configFetcher->start();
    _component.registerMetric(*_metrics);
    if (!defer_manager_thread_start) {
        create_and_start_manager_thread();
    }
    _component.registerMetricUpdateHook(*this, 5s);
    _visitorFactories["dumpvisitor"]       = std::make_shared<DumpVisitorSingleFactory>();
    _visitorFactories["dumpvisitorsingle"] = std::make_shared<DumpVisitorSingleFactory>();
    _visitorFactories["testvisitor"]       = std::make_shared<TestVisitorFactory>();
    _visitorFactories["countvisitor"]      = std::make_shared<CountVisitorFactory>();
    _visitorFactories["recoveryvisitor"]   = std::make_shared<RecoveryVisitorFactory>();
    _visitorFactories["reindexingvisitor"] = std::make_shared<ReindexingVisitorFactory>();
    _component.registerStatusPage(*this);
}

VisitorManager::~VisitorManager() {
    closeNextLink();
    LOG(debug, "Deleting link %s.", toString().c_str());
    if (_thread) {
        _thread->interrupt();
        _visitorCond.notify_all();
        _thread->join();
    }
    _visitorThread.clear();
}

void
VisitorManager::create_and_start_manager_thread()
{
    assert(!_thread);
    _thread = _component.startThread(*this, 30s, 1s, 1, vespalib::CpuUsage::Category::READ);
}

void
VisitorManager::updateMetrics(const MetricLockGuard &)
{
    _metrics->queueSize.addValue(static_cast<int64_t>(_visitorQueue.relaxed_atomic_size()));
}

void
VisitorManager::onClose()
{
        // Avoid getting config during shutdown
    _configFetcher->close();
    {
        std::lock_guard sync(_visitorLock);
        for (auto& enqueued : _visitorQueue) {
            auto reply = std::make_shared<api::CreateVisitorReply>(*enqueued._command);
            reply->setResult(api::ReturnCode(api::ReturnCode::ABORTED, "Shutting down storage node."));
            sendUp(reply);
        }
        _visitorQueue.clear();
    }
    for (auto& visitor_thread : _visitorThread) {
        visitor_thread.first->shutdown();
    }
}

void
VisitorManager::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "VisitorManager";
}

void
VisitorManager::configure(std::unique_ptr<vespa::config::content::core::StorVisitorConfig> config)
{
    std::lock_guard sync(_visitorLock);
    if (config->defaultdocblocksize % 512 != 0) {
        throw config::InvalidConfigException(
                "The default docblock size needs to be a multiplum of the "
                "disk block size. (512b)");
    }

    // Do some sanity checking of input. Cannot haphazardly mix and match
    // old and new max concurrency config values
    if (config->maxconcurrentvisitors == 0
        && config->maxconcurrentvisitorsFixed == 0)
    {
        throw config::InvalidConfigException(
                "Maximum concurrent visitor count cannot be 0.");
    }
    else if (config->maxconcurrentvisitorsFixed == 0
                 && config->maxconcurrentvisitorsVariable != 0)
    {
        throw config::InvalidConfigException(
                "Cannot specify 'variable' parameter for max concurrent "
                " visitors without also specifying 'fixed'.");
    }

    uint32_t maxConcurrentVisitorsFixed;
    uint32_t maxConcurrentVisitorsVariable;

    // Concurrency parameter fixed takes precedence over legacy maxconcurrent
    if (config->maxconcurrentvisitorsFixed > 0) {
        maxConcurrentVisitorsFixed = config->maxconcurrentvisitorsFixed;
        maxConcurrentVisitorsVariable = config->maxconcurrentvisitorsVariable;
    } else {
       maxConcurrentVisitorsFixed = config->maxconcurrentvisitors;
       maxConcurrentVisitorsVariable = 0;
    }

    bool liveUpdate = !_visitorThread.empty();
    if (liveUpdate) {
        if (_visitorThread.size() != static_cast<uint32_t>(config->visitorthreads)) {
            LOG(warning, "Ignoring config change requesting %u visitor "
                         "threads, still running %u. Restart storage to apply "
                         "change.",
                         config->visitorthreads,
                         (uint32_t) _visitorThread.size());
        }

        if (_maxFixedConcurrentVisitors != maxConcurrentVisitorsFixed
            || _maxVariableConcurrentVisitors != maxConcurrentVisitorsVariable)
        {
            LOG(info, "Altered max concurrent visitors setting from "
                "(fixed=%u, variable=%u) to (fixed=%u, variable=%u).",
                _maxFixedConcurrentVisitors, _maxVariableConcurrentVisitors,
                maxConcurrentVisitorsFixed, maxConcurrentVisitorsVariable);
        }

        if (_maxVisitorQueueSize != static_cast<uint32_t>(config->maxvisitorqueuesize)) {
            LOG(info, "Altered max visitor queue size setting from %u to %u.",
                      _maxVisitorQueueSize, config->maxvisitorqueuesize);
        }
    } else {
        if (config->visitorthreads == 0) {
            throw config::InvalidConfigException(
                    "No visitor threads configured. If you don't want visitors "
                    "to run, don't use visitormanager.", VESPA_STRLOC);
        }
        _metrics->initThreads(config->visitorthreads);
        for (int32_t i=0; i<config->visitorthreads; ++i) {
            _visitorThread.emplace_back(
                    // Naked new due to a lot of private inheritance in VisitorThread and VisitorManager
                    std::shared_ptr<VisitorThread>(new VisitorThread(i, _componentRegister, _messageSessionFactory,
                                                                     _visitorFactories, *_metrics->threads[i], *this)),
                    std::map<api::VisitorId, std::string>());
        }
    }
    _maxFixedConcurrentVisitors = maxConcurrentVisitorsFixed;
    _maxVariableConcurrentVisitors = maxConcurrentVisitorsVariable;
    _maxVisitorQueueSize = config->maxvisitorqueuesize;

    auto cmd = std::make_shared<PropagateVisitorConfig>(*config);
    for (auto& thread : _visitorThread) {
        thread.first->processMessage(0, cmd);
    }
}

void
VisitorManager::run(framework::ThreadHandle& thread)
{
    LOG(debug, "Started visitor manager thread with pid %d.", getpid());
    using CQ = CommandQueue<api::CreateVisitorCommand>;
    std::vector<CQ::CommandEntry> timedOut;
    // Run forever, dump messages in the visitor queue that times out.
    while (true) {
        thread.registerTick(framework::PROCESS_CYCLE);
        {
            std::lock_guard waiter(_visitorLock);
            if (thread.interrupted()) {
                break;
            }
            timedOut = _visitorQueue.releaseTimedOut();
        }
        const auto currentTime = _component.getClock().getMonotonicTime();
        for (auto& timed_out_entry : timedOut) {
            // TODO is this really tracking what the metric description implies it's tracking...?
            _metrics->queueTimeoutWaitTime.addValue(vespalib::to_s(currentTime - timed_out_entry._deadline) * 1000.0); // Double metric in millis
            std::shared_ptr<api::StorageReply> reply(timed_out_entry._command->makeReply());
            reply->setResult(api::ReturnCode(api::ReturnCode::BUSY, "Visitor timed out in visitor queue"));
            sendUp(reply);
        }
        {
            std::unique_lock waiter(_visitorLock);
            if (thread.interrupted()) {
                break;
            } else if (_visitorQueue.empty()) {
                _visitorCond.wait_for(waiter, 1000ms);
                thread.registerTick(framework::WAIT_CYCLE);
            } else {
                auto time_diff = _visitorQueue.tbegin()->_deadline - currentTime;
                time_diff = (time_diff < 1000ms) ? time_diff : 1000ms;
                if (time_diff.count() > 0) {
                    _visitorCond.wait_for(waiter, time_diff);
                    thread.registerTick(framework::WAIT_CYCLE);
                }
            }
        }
    }
    LOG(debug, "Stopped visitor manager thread with pid %d.", getpid());
}

namespace {
    template<typename T>
    uint32_t getLeastLoadedThread(const T& t, uint32_t& totalCount) {
        uint32_t min = 0xFFFFFFFF;
        totalCount = 0;
        for (uint32_t i=0; i<t.size(); ++i) {
            totalCount += t[i].second.size();
            if (t[i].second.size() < min) {
                min = t[i].second.size();
            }
        }
        return min;
    }
}

uint32_t
VisitorManager::getActiveVisitorCount() const
{
    std::lock_guard sync(_visitorLock);
    uint32_t totalCount = 0;
    for (const auto& visitor_thread : _visitorThread) {
        totalCount += visitor_thread.second.size();
    }
    return totalCount;
}

/** For unit testing that we don't leak memory from message tracking. */
bool
VisitorManager::hasPendingMessageState() const
{
    std::lock_guard sync(_visitorLock);
    return !_visitorMessages.empty();
}

void
VisitorManager::setTimeBetweenTicks(uint32_t time)
{
    std::lock_guard sync(_visitorLock);
    for (auto& visitor_thread : _visitorThread) {
        visitor_thread.first->setTimeBetweenTicks(time);
    }
}

bool
VisitorManager::scheduleVisitor(
        const std::shared_ptr<api::CreateVisitorCommand>& cmd, bool skipQueue,
        MonitorGuard& visitorLock)
{
    api::VisitorId id;
    using NameToIdMap = std::map<std::string, api::VisitorId>;
    using NameIdPair  = std::pair<std::string, api::VisitorId>;
    std::pair<NameToIdMap::iterator, bool> newEntry;
    {
        uint32_t totCount;
        uint32_t minLoadCount = getLeastLoadedThread(_visitorThread, totCount);
        if (!skipQueue) {
            if (_enforceQueueUse || totCount >= maximumConcurrent(*cmd)) {
                api::CreateVisitorCommand::SP failCommand;

                if (cmd->getQueueTimeout() > vespalib::duration::zero() && _maxVisitorQueueSize > 0) {
                    if (_visitorQueue.size() < _maxVisitorQueueSize) {
                            // Still room in the queue
                        _visitorQueue.add(cmd);
                        _visitorCond.notify_one();
                    } else {
                        // If tail of priority queue has a lower priority than
                        // the new visitor, evict it and insert the new one. If
                        // not, immediately return with a busy reply
                        std::shared_ptr<api::CreateVisitorCommand> tail(_visitorQueue.peekLowestPriorityCommand());
                            // Lower int ==> higher pri
                        if (cmd->getPriority() < tail->getPriority()) {
                            auto evictCommand = _visitorQueue.releaseLowestPriorityCommand();
                            assert(tail == evictCommand.first);
                            _visitorQueue.add(cmd);
                            _visitorCond.notify_one();
                            auto now = _component.getClock().getMonotonicTime();
                            // TODO is this really tracking what the metric description implies it's tracking...?
                            _metrics->queueEvictedWaitTime.addValue(vespalib::to_s(now - evictCommand.second) * 1000.0); // Double metric in millis
                            failCommand = evictCommand.first;
                        } else {
                            failCommand = cmd;
                            _metrics->queueFull.inc();
                        }
                    }
                } else {
                    // No queueing allowed; must return busy for new command
                    failCommand = cmd;
                }
                visitorLock.unlock();

                if (failCommand) {
                    auto reply = std::make_shared<api::CreateVisitorReply>(*failCommand);
                    std::ostringstream ost;
                    if (cmd->getQueueTimeout() <= vespalib::duration::zero()) {
                        ost << "Already running the maximum amount ("
                            << maximumConcurrent(*failCommand)
                            << ") of visitors for this priority ("
                            << static_cast<uint32_t>(failCommand->getPriority())
                            << "), and queue timeout is 0.";
                    } else if (_maxVisitorQueueSize == 0) {
                        ost << "Already running the maximum amount ("
                            << maximumConcurrent(*failCommand)
                            << ") of visitors for this priority ("
                            << static_cast<uint32_t>(failCommand->getPriority())
                            << "), and maximum queue size is 0.";
                    } else {
                        ost << "Queue is full and a higher priority visitor was received, taking precedence.";
                    }
                    reply->setResult(api::ReturnCode(api::ReturnCode::BUSY, ost.str()));
                    send(reply);
                }
                return false;
            } else {
                _metrics->queueSkips.inc();
            }
        }
        while (true) {
            id = ++_visitorCounter;
            std::map<api::VisitorId, std::string>& usedIds(_visitorThread[id % _visitorThread.size()].second);
            if (usedIds.size() == minLoadCount &&
                usedIds.find(id) == usedIds.end())
            {
                newEntry = _nameToId.insert(NameIdPair(cmd->getInstanceId(), id));
                if (newEntry.second) {
                    usedIds[id] = cmd->getInstanceId();
                }
                break;
            }
        }
    }
    visitorLock.unlock();
    if (!newEntry.second) {
        auto reply = std::make_shared<api::CreateVisitorReply>(*cmd);
        std::ostringstream ost;
        ost << "Already running a visitor named " << cmd->getInstanceId()
            << ". Not creating visitor.";
        reply->setResult(api::ReturnCode(api::ReturnCode::EXISTS, ost.str()));
        send(reply);
        return false;
    }
    cmd->setVisitorId(id);
    _visitorThread[id % _visitorThread.size()].first->processMessage(id, cmd);
    return true;
}

bool
VisitorManager::onCreateVisitor(const std::shared_ptr<api::CreateVisitorCommand>& cmd)
{
    MonitorGuard sync(_visitorLock);
    scheduleVisitor(cmd, false, sync);
    return true;
}

bool
VisitorManager::onDown(const std::shared_ptr<api::StorageMessage>& r)
{
    std::shared_ptr<api::StorageReply> reply(std::dynamic_pointer_cast<api::StorageReply>(r));

    if (reply && processReply(reply)) {
        return true;
    } else {
        return StorageLink::onDown(r);
    }
}

bool
VisitorManager::onInternalReply(const std::shared_ptr<api::InternalReply>& r)
{
    switch(r->getType()) {
        case RequestStatusPageReply::ID:
            {
                std::shared_ptr<RequestStatusPageReply> reply(std::dynamic_pointer_cast<RequestStatusPageReply>(r));
                assert(reply.get());
                std::lock_guard waiter(_statusLock);
                _statusRequest.push_back(reply);
                _statusCond.notify_one();
                return true;
            }
        case PropagateVisitorConfigReply::ID:
            {
                return true; // Ignore replies if any.
            }
        default:
            return processReply(r);
    }
}

bool
VisitorManager::processReply(const std::shared_ptr<api::StorageReply>& reply)
{
    api::VisitorId id;
    {
        std::lock_guard sync(_visitorLock);
        auto it = _visitorMessages.find(reply->getMsgId());
        if (it == _visitorMessages.end()) return false;
        id = it->second.id;
        _visitorMessages.erase(it);
    }
    _visitorThread[id % _visitorThread.size()].first->processMessage(id, reply);
    return true;
}

void
VisitorManager::send(const std::shared_ptr<api::StorageCommand>& cmd, Visitor& visitor)
{
    assert(cmd->getType() == api::MessageType::INTERNAL);
    // Only add to internal state if not destroy iterator command, as
    // these are considered special-cased fire-and-forget commands
    // that don't have replies.
    if (static_cast<const api::InternalCommand&>(*cmd).getType() != DestroyIteratorCommand::ID) {
        MessageInfo inf;
        inf.id = visitor.getVisitorId();
        inf.timestamp = _component.getClock().getSystemTime();
        inf.timeout = cmd->getTimeout();

        if (cmd->getAddress()) {
            inf.destination = cmd->getAddress()->toString();
        }

        std::lock_guard sync(_visitorLock);
        _visitorMessages[cmd->getMsgId()] = inf;
    }
    mbus::Trace & trace = cmd->getTrace();
    MBUS_TRACE(trace, 6, "Requesting data from persistence layer: " + cmd->toString());
    LOG(spam, "Sending visitor command %s down.", cmd->getType().getName().c_str());
    sendDown(cmd);
}

void
VisitorManager::send(const std::shared_ptr<api::StorageReply>& reply)
{
    if (reply->getType() == api::MessageType::INTERNAL_REPLY) {
        LOG(spam, "Received an internal reply");
        std::shared_ptr<api::InternalReply> rep(std::dynamic_pointer_cast<api::InternalReply>(reply));
        assert(rep.get());
        if (onInternalReply(rep)) return;
    }
    LOG(spam, "Sending visitor reply %s up.", reply->getType().getName().c_str());
    sendUp(reply);
}

// Attempt to schedule a new visitor. visitorLock must be held at
// the time of the call and will be unlocked if scheduling takes
// place. Returns true if a visitor was scheduled, false otherwise.
bool
VisitorManager::attemptScheduleQueuedVisitor(MonitorGuard& visitorLock)
{
    if (_visitorQueue.empty()) return false;

    uint32_t totCount;
    getLeastLoadedThread(_visitorThread, totCount);
    auto cmd = _visitorQueue.peekNextCommand();
    assert(cmd.get());
    if (totCount < maximumConcurrent(*cmd)) {
        auto cmd2 = _visitorQueue.releaseNextCommand();
        assert(cmd == cmd2.first);
        scheduleVisitor(cmd, true, visitorLock);
        auto now = _component.getClock().getMonotonicTime();
        // TODO is this really tracking what the metric description implies it's tracking...?
        _metrics->queueWaitTime.addValue(vespalib::to_s(now - cmd2.second) * 1000.0); // Double metric in millis
        // visitorLock is unlocked at this point
        return true;
    }
    return false;
}

void
VisitorManager::closed(api::VisitorId id)
{
    std::unique_lock sync(_visitorLock);
    auto& usedIds(_visitorThread[id % _visitorThread.size()].second);

    auto it = usedIds.find(id);
    if (it == usedIds.end()) {
        LOG(warning, "VisitorManager::closed() called multiple times for the "
                     "same visitor. This was not intended.");
        return;
    }
    vespalib::steady_time now(_component.getClock().getMonotonicTime());
    _recentlyDeletedVisitors.emplace_back(it->second, now);
    _nameToId.erase(it->second);
    usedIds.erase(it);
    while ((_recentlyDeletedVisitors.front().second + _recentlyDeletedMaxTime) < now) {
        _recentlyDeletedVisitors.pop_front();
    }

    // Schedule as many visitors as we are allowed to for the highest
    // prioritized queued commands
    bool scheduled = attemptScheduleQueuedVisitor(sync);
    while (scheduled) {
        // At this point, sync is unlocked, so we have to re-acquire
        // the lock
        std::unique_lock resync(_visitorLock);
        scheduled = attemptScheduleQueuedVisitor(resync);
    }
}

/**
 * The string in page is just searched through using string::find. Terms found
 * are printed.. Known terms:
 *
 *   visitor - Print info on visitor given
 *   allvisitors - Print all info on all visitors
 *
 *   verbose - If set, print extra details.
 */
void
VisitorManager::reportHtmlStatus(std::ostream& out,
                                 const framework::HttpUrlPath& path) const
{
    using vespalib::xml_attribute_escaped;
    using vespalib::xml_content_escaped;

    bool showStatus = !path.hasAttribute("visitor");
    bool verbose = path.hasAttribute("verbose");
    bool showAll = path.hasAttribute("allvisitors");

        // Print menu
    out << "<font size=\"-1\">[ <a href=\"../\">Back to top</a>"
        << " | <a href=\"?" << (verbose ? "verbose" : "")
        << "\">Main visitor manager status page</a>"
        << " | <a href=\"?allvisitors" << (verbose ? "&verbose" : "")
        << "\">Show all visitors</a>"
        << " | <a href=\"?" << (verbose ? "notverbose" : "verbose");
    if (!showStatus) out << "&visitor=" << xml_attribute_escaped(path.get("visitor", std::string("")));
    if (showAll) out << "&allvisitors";
    out << "\">" << (verbose ? "Less verbose" : "More verbose") << "</a>\n"
        << " ]</font><br><br>\n";

    uint32_t visitorCount = 0;
    if (showStatus) {
        std::lock_guard sync(_visitorLock);
        if (verbose) {
            out << "<h3>Currently running visitors</h3>\n";
            for (uint32_t i=0; i<_visitorThread.size(); ++i) {
                visitorCount += _visitorThread[i].second.size();
                out << "Thread " << i << ":";
                if (_visitorThread[i].second.empty()) {
                    out << " none";
                } else {
                    for (const auto& id_and_visitor : _visitorThread[i].second) {
                        out << " " << xml_content_escaped(id_and_visitor.second)
                            << " (" << id_and_visitor.first << ")";
                    }
                }
                out << "<br>\n";
            }
            out << "<h3>Queued visitors</h3>\n<ul>\n";

            const auto now = _component.getClock().getMonotonicTime();
            for (const auto& enqueued : _visitorQueue) {
                auto& cmd = enqueued._command;
                assert(cmd);
                out << "<li>"
                    << xml_content_escaped(cmd->getInstanceId()) << " - "
                    << vespalib::count_ms(cmd->getQueueTimeout()) << ", remaining timeout "
                    << vespalib::count_ms(enqueued._deadline - now) << " ms\n";
            }
            if (_visitorQueue.empty()) {
                out << "None\n";
            }
            out << "</ul>\n";
            if (!_visitorMessages.empty()) {
                out << "<h3>Waiting for the following visitor replies</h3>"
                    << "\n<table><tr>"
                    << "<th>Storage API message id</th>"
                    << "<th>Visitor id</th>"
                    << "<th>Timestamp</th>"
                    << "<th>Timeout</th>"
                    << "<th>Destination</th>"
                    << "</tr>\n";
                for (const auto & entry : _visitorMessages) {
                    out << "<tr>"
                        << "<td>" << entry.first << "</td>"
                        << "<td>" << entry.second.id << "</td>"
                        << "<td>" << vespalib::to_string(entry.second.timestamp) << "</td>"
                        << "<td>" << vespalib::count_ms(entry.second.timeout) << "</td>"
                        << "<td>" << xml_content_escaped(entry.second.destination) << "</td>"
                        << "</tr>\n";
                }
                out << "</table>\n";
            } else {
                out << "<h3>Not waiting for any visitor replies</h3>\n";
            }
        }
        out << "\n<p>Running " << visitorCount << " visitors. Max concurrent "
            << "visitors: fixed = " << _maxFixedConcurrentVisitors
            << ", variable = " << _maxVariableConcurrentVisitors
            << ", waiting visitors " << _visitorQueue.size() << "<br>\n";
    }
    // Only one can access status at a time as _statusRequest only holds
    // answers from one request at a time
    std::unique_lock sync(_statusLock);
    // Send all sub-requests
    uint32_t parts = _visitorThread.size();
    for (uint32_t i=0; i<parts; ++i) {
        auto cmd = std::make_shared<RequestStatusPage>(path);
        std::ostringstream token;
        token << "Visitor thread " << i;
        cmd->setSortToken(token.str());
        _visitorThread[i].first->processMessage(0, cmd);
    }
    // Wait for all replies to come back
    _statusCond.wait(sync, [&]() { return (_statusRequest.size() >= parts);});
    std::sort(_statusRequest.begin(), _statusRequest.end(), StatusReqSorter());

    // Create output
    for (const auto & request : _statusRequest) {
        out << "<h2>" << request->getSortToken()
            << "</h2>\n" << request->getStatus() << "\n";
    }
    _statusRequest.clear();
}

} // storage
