// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "visitormanager.h"
#include "messages.h"
#include "dumpvisitorsingle.h"
#include "countvisitor.h"
#include "testvisitor.h"
#include "recoveryvisitor.h"
#include <vespa/storage/common/statusmessages.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/documentapi/loadtypes/loadtypeset.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".visitor.manager");

namespace storage {

VisitorManager::VisitorManager(const config::ConfigUri & configUri,
                               StorageComponentRegister& componentRegister,
                               VisitorMessageSessionFactory& messageSF,
                               const VisitorFactory::Map& externalFactories)
    : StorageLink("Visitor Manager"),
      framework::HtmlStatusReporter("visitorman", "Visitor Manager"),
      _componentRegister(componentRegister),
      _messageSessionFactory(messageSF),
      _visitorThread(),
      _visitorMessages(),
      _visitorLock(),
      _visitorCounter(0),
      _configFetcher(configUri.getContext()),
      _metrics(new VisitorMetrics),
      _maxFixedConcurrentVisitors(1),
      _maxVariableConcurrentVisitors(0),
      _maxVisitorQueueSize(1024),
      _nameToId(),
      _component(componentRegister, "visitormanager"),
      _visitorQueue(_component.getClock()),
      _recentlyDeletedVisitors(),
      _recentlyDeletedMaxTime(5 * 1000 * 1000),
      _statusLock(),
      _statusMonitor(),
      _statusRequest(),
      _enforceQueueUse(false),
      _visitorFactories(externalFactories)
{
    _configFetcher.subscribe<vespa::config::content::core::StorVisitorConfig>(configUri.getConfigId(), this);
    _configFetcher.start();
    _component.registerMetric(*_metrics);
    framework::MilliSecTime maxProcessTime(30 * 1000);
    framework::MilliSecTime waitTime(1000);
    _thread = _component.startThread(*this, maxProcessTime, waitTime);
    _component.registerMetricUpdateHook(*this, framework::SecondTime(5));
    _visitorFactories["dumpvisitor"].reset(new DumpVisitorSingleFactory);
    _visitorFactories["dumpvisitorsingle"].reset(new DumpVisitorSingleFactory);
    _visitorFactories["testvisitor"].reset(new TestVisitorFactory);
    _visitorFactories["countvisitor"].reset(new CountVisitorFactory);
    _visitorFactories["recoveryvisitor"].reset(new RecoveryVisitorFactory);
    _component.registerStatusPage(*this);
}

VisitorManager::~VisitorManager() {
    closeNextLink();
    LOG(debug, "Deleting link %s.", toString().c_str());
    if (_thread.get() != 0) {
        _thread->interrupt();
        {
            vespalib::MonitorGuard waiter(_visitorLock);
            waiter.signal();
        }
        _thread->join();
    }
    _visitorThread.clear();
}

void
VisitorManager::updateMetrics(const MetricLockGuard &)
{
    _metrics->queueSize.addValue(_visitorQueue.size());
}

void
VisitorManager::onClose()
{
        // Avoid getting config during shutdown
    _configFetcher.close();
    {
        vespalib::MonitorGuard sync(_visitorLock);
        for (CommandQueue<api::CreateVisitorCommand>::iterator it
                = _visitorQueue.begin(); it != _visitorQueue.end(); ++it)
        {
            std::shared_ptr<api::CreateVisitorReply> reply(
                    new api::CreateVisitorReply(*it->_command));
            reply->setResult(api::ReturnCode(
                        api::ReturnCode::ABORTED,
                        "Shutting down storage node."));
            sendUp(reply);
        }
        _visitorQueue.clear();
    }
    for (uint32_t i=0; i<_visitorThread.size(); ++i) {
        _visitorThread[i].first->shutdown();
    }
}

void
VisitorManager::print(std::ostream& out, bool verbose,
              const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "VisitorManager";
}

void
VisitorManager::configure(std::unique_ptr<vespa::config::content::core::StorVisitorConfig> config)
{
    vespalib::MonitorGuard sync(_visitorLock);
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

    bool liveUpdate = (_visitorThread.size() > 0);
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
        _metrics->initThreads(config->visitorthreads, _component.getLoadTypes()->getMetricLoadTypes());
        for (int32_t i=0; i<config->visitorthreads; ++i) {
            _visitorThread.push_back(std::make_pair(
                    std::shared_ptr<VisitorThread>(
                        new VisitorThread(i, _componentRegister,
                                          _messageSessionFactory,
                                          _visitorFactories,
                                          *_metrics->threads[i], *this)),
                    std::map<api::VisitorId, std::string>()));
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
    typedef CommandQueue<api::CreateVisitorCommand> CQ;
    std::list<CQ::CommandEntry> timedOut;
        // Run forever, dump messages in the visitor queue that times out.
    while (true) {
        thread.registerTick(framework::PROCESS_CYCLE);
        {
            vespalib::LockGuard waiter(_visitorLock);
            if (thread.interrupted()) {
                break;
            }
            timedOut = _visitorQueue.releaseTimedOut();
        }
        framework::MicroSecTime currentTime(
                _component.getClock().getTimeInMicros());
        for (std::list<CQ::CommandEntry>::iterator it = timedOut.begin();
             it != timedOut.end(); ++it)
        {
            _metrics->queueTimeoutWaitTime.addValue(
                    currentTime.getTime() - it->_time);
            std::shared_ptr<api::StorageReply> reply(
                    it->_command->makeReply().release());
            reply->setResult(api::ReturnCode(api::ReturnCode::BUSY,
                                        "Visitor timed out in visitor queue"));
            sendUp(reply);
        }
        {
            vespalib::MonitorGuard waiter(_visitorLock);
            if (thread.interrupted()) {
                break;
            } else if (_visitorQueue.empty()) {
                waiter.wait(1000);
                thread.registerTick(framework::WAIT_CYCLE);
            } else {
                uint64_t timediff = (_visitorQueue.tbegin()->_time
                                     - currentTime.getTime())
                                            / 1000000;
                timediff = std::min(timediff, uint64_t(1000));
                if (timediff > 0) {
                    waiter.wait(timediff);
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
    vespalib::MonitorGuard sync(_visitorLock);
    uint32_t totalCount = 0;
    for (uint32_t i=0; i<_visitorThread.size(); ++i) {
        totalCount += _visitorThread[i].second.size();
    }
    return totalCount;
}

/** For unit testing that we don't leak memory from message tracking. */
bool
VisitorManager::hasPendingMessageState() const
{
    vespalib::MonitorGuard sync(_visitorLock);
    return !_visitorMessages.empty();
}

void
VisitorManager::setTimeBetweenTicks(uint32_t time)
{
    vespalib::MonitorGuard sync(_visitorLock);
    for (uint32_t i=0; i<_visitorThread.size(); ++i) {
        _visitorThread[i].first->setTimeBetweenTicks(time);
    }
}

bool
VisitorManager::scheduleVisitor(
        const std::shared_ptr<api::CreateVisitorCommand>& cmd, bool skipQueue,
        vespalib::MonitorGuard& visitorLock)
{
    api::VisitorId id;
    typedef std::map<std::string, api::VisitorId> NameToIdMap;
    typedef std::pair<std::string, api::VisitorId> NameIdPair;
    std::pair<NameToIdMap::iterator, bool> newEntry;
    {
        uint32_t totCount;
        uint32_t minLoadCount = getLeastLoadedThread(_visitorThread, totCount);
        if (!skipQueue) {
            if (_enforceQueueUse || totCount >= maximumConcurrent(*cmd)) {
                api::CreateVisitorCommand::SP failCommand;

                if (cmd->getQueueTimeout() != 0 && _maxVisitorQueueSize > 0) {
                    if (_visitorQueue.size() < _maxVisitorQueueSize) {
                            // Still room in the queue
                        _visitorQueue.add(cmd);
                        visitorLock.signal();
                    } else {
                        // If tail of priority queue has a lower priority than
                        // the new visitor, evict it and insert the new one. If
                        // not, immediately return with a busy reply
                        std::shared_ptr<api::CreateVisitorCommand> tail(
                                _visitorQueue.peekLowestPriorityCommand());
                            // Lower int ==> higher pri
                        if (cmd->getPriority() < tail->getPriority()) {
                            std::pair<api::CreateVisitorCommand::SP,
                                      time_t> evictCommand(
                                _visitorQueue.releaseLowestPriorityCommand());
                            assert(tail == evictCommand.first);
                            _visitorQueue.add(cmd);
                            visitorLock.signal();
                            framework::MicroSecTime t(
                                    _component.getClock().getTimeInMicros());
                            _metrics->queueEvictedWaitTime.addValue(
                                    t.getTime() - evictCommand.second);
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

                if (failCommand.get() != 0) {
                    std::shared_ptr<api::CreateVisitorReply> reply(
                            new api::CreateVisitorReply(*failCommand));
                    std::ostringstream ost;
                    if (cmd->getQueueTimeout() == 0) {
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
                        ost << "Queue is full and a higher priority visitor was received, "
                               "taking precedence.";
                    }
                    reply->setResult(api::ReturnCode(api::ReturnCode::BUSY,
                                                     ost.str()));
                    send(reply);
                }
                return false;
            } else {
                _metrics->queueSkips.inc();
            }
        }
        while (true) {
            id = ++_visitorCounter;
            std::map<api::VisitorId, std::string>& usedIds(
                    _visitorThread[id % _visitorThread.size()].second);
            if (usedIds.size() == minLoadCount &&
                usedIds.find(id) == usedIds.end())
            {
                newEntry = _nameToId.insert(NameIdPair(cmd->getInstanceId(),
                                                       id));
                if (newEntry.second) {
                    usedIds[id] = cmd->getInstanceId();
                }
                break;
            }
        }
    }
    visitorLock.unlock();
    if (!newEntry.second) {
        std::shared_ptr<api::CreateVisitorReply> reply(
                new api::CreateVisitorReply(*cmd));
        std::ostringstream ost;
        ost << "Already running a visitor named " << cmd->getInstanceId()
            << ". Not creating visitor.";
        reply->setResult(api::ReturnCode(api::ReturnCode::EXISTS,
                                         ost.str()));
        send(reply);
        return false;
    }
    cmd->setVisitorId(id);
    _visitorThread[id % _visitorThread.size()].first->processMessage(id, cmd);
    return true;
}

bool
VisitorManager::onCreateVisitor(
        const std::shared_ptr<api::CreateVisitorCommand>& cmd)
{
    vespalib::MonitorGuard sync(_visitorLock);
    scheduleVisitor(cmd, false, sync);
    return true;
}

bool
VisitorManager::onDown(const std::shared_ptr<api::StorageMessage>& r)
{
    std::shared_ptr<api::StorageReply> reply(
            std::dynamic_pointer_cast<api::StorageReply>(r));

    if (reply.get() != 0 && processReply(reply)) {
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
                std::shared_ptr<RequestStatusPageReply> reply(
                        std::dynamic_pointer_cast<RequestStatusPageReply>(r));
                assert(reply.get());
                vespalib::MonitorGuard waiter(_statusMonitor);
                _statusRequest.push_back(reply);
                waiter.signal();
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
        vespalib::MonitorGuard sync(_visitorLock);
        std::map<api::StorageMessage::Id, MessageInfo>::iterator it
                = _visitorMessages.find(reply->getMsgId());
        if (it == _visitorMessages.end()) return false;
        id = it->second.id;
        _visitorMessages.erase(it);
    }
    _visitorThread[id % _visitorThread.size()].first->processMessage(id, reply);
    return true;
}

void
VisitorManager::send(const std::shared_ptr<api::StorageCommand>& cmd,
                     Visitor& visitor)
{
    assert(cmd->getType() == api::MessageType::INTERNAL);
    // Only add to internal state if not destroy iterator command, as
    // these are considered special-cased fire-and-forget commands
    // that don't have replies.
    if (static_cast<const api::InternalCommand&>(*cmd).getType()
        != DestroyIteratorCommand::ID)
    {
        MessageInfo inf;
        inf.id = visitor.getVisitorId();
        inf.timestamp = _component.getClock().getTimeInSeconds().getTime();
        inf.timeout = cmd->getTimeout();

        if (cmd->getAddress()) {
            inf.destination = cmd->getAddress()->toString();
        }

        vespalib::MonitorGuard sync(_visitorLock);
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
        std::shared_ptr<api::InternalReply> rep(
                std::dynamic_pointer_cast<api::InternalReply>(reply));
        assert(rep.get());
        if (onInternalReply(rep)) return;
    }
    LOG(spam, "Sending visitor reply %s up.",
              reply->getType().getName().c_str());
    sendUp(reply);
}

// Attempt to schedule a new visitor. visitorLock must be held at
// the time of the call and will be unlocked if scheduling takes
// place. Returns true if a visitor was scheduled, false otherwise.
bool
VisitorManager::attemptScheduleQueuedVisitor(vespalib::MonitorGuard& visitorLock)
{
    if (_visitorQueue.empty()) return false;

    uint32_t totCount;
    getLeastLoadedThread(_visitorThread, totCount);
    std::shared_ptr<api::CreateVisitorCommand> cmd(
            _visitorQueue.peekNextCommand());
    assert(cmd.get());
    if (totCount < maximumConcurrent(*cmd)) {
        std::pair<api::CreateVisitorCommand::SP, time_t> cmd2(
                _visitorQueue.releaseNextCommand());
        assert(cmd == cmd2.first);
        scheduleVisitor(cmd, true, visitorLock);
        framework::MicroSecTime time(_component.getClock().getTimeInMicros());
        _metrics->queueWaitTime.addValue(time.getTime() - cmd2.second);
        // visitorLock is unlocked at this point
        return true;
    }
    return false;
}

void
VisitorManager::closed(api::VisitorId id)
{
    vespalib::MonitorGuard sync(_visitorLock);
    std::map<api::VisitorId, std::string>& usedIds(
            _visitorThread[id % _visitorThread.size()].second);

    std::map<api::VisitorId, std::string>::iterator it = usedIds.find(id);
    if (it == usedIds.end()) {
        LOG(warning, "VisitorManager::closed() called multiple times for the "
                     "same visitor. This was not intended.");
        return;
    }
    framework::MicroSecTime time(_component.getClock().getTimeInMicros());
    _recentlyDeletedVisitors.push_back(
            std::make_pair(it->second, time));
    _nameToId.erase(it->second);
    usedIds.erase(it);
    while (_recentlyDeletedVisitors.front().second + _recentlyDeletedMaxTime
            < time)
    {
        _recentlyDeletedVisitors.pop_front();
    }

    // Schedule as many visitors as we are allowed to for the highest
    // prioritized queued commands
    bool scheduled = attemptScheduleQueuedVisitor(sync);
    while (scheduled) {
        // At this point, sync is unlocked, so we have to re-acquire
        // the lock
        vespalib::MonitorGuard resync(_visitorLock);
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
    bool showStatus = !path.hasAttribute("visitor");
    bool verbose = path.hasAttribute("verbose");
    bool showAll = path.hasAttribute("allvisitors");

        // Print menu
    out << "<font size=\"-1\">[ <a href=\"/\">Back to top</a>"
        << " | <a href=\"?" << (verbose ? "verbose" : "")
        << "\">Main visitor manager status page</a>"
        << " | <a href=\"?allvisitors" << (verbose ? "&verbose" : "")
        << "\">Show all visitors</a>"
        << " | <a href=\"?" << (verbose ? "notverbose" : "verbose");
    if (!showStatus) out << "&visitor=" << path.get("visitor", std::string(""));
    if (showAll) out << "&allvisitors";
    out << "\">" << (verbose ? "Less verbose" : "More verbose") << "</a>\n"
        << " ]</font><br><br>\n";

    uint32_t visitorCount = 0;
    if (showStatus) {
        vespalib::MonitorGuard sync(_visitorLock);
        if (verbose) {
            out << "<h3>Currently running visitors</h3>\n";
            for (uint32_t i=0; i<_visitorThread.size(); ++i) {
                visitorCount += _visitorThread[i].second.size();
                out << "Thread " << i << ":";
                if (_visitorThread[i].second.size() == 0) {
                    out << " none";
                } else {
                    for (std::map<api::VisitorId,std::string>::const_iterator it
                             = _visitorThread[i].second.begin();
                         it != _visitorThread[i].second.end(); it++)
                    {
                        out << " " << it->second << " (" << it->first << ")";
                    }
                }
                out << "<br>\n";
            }
            out << "<h3>Queued visitors</h3>\n<ul>\n";

            framework::MicroSecTime time(
                    _component.getClock().getTimeInMicros());
            for (CommandQueue<api::CreateVisitorCommand>::const_iterator it
                    = _visitorQueue.begin(); it != _visitorQueue.end(); ++it)
            {
                std::shared_ptr<api::CreateVisitorCommand> cmd(
                            it->_command);
                assert(cmd.get());
                out << "<li>" << cmd->getInstanceId() << " - "
                    << cmd->getQueueTimeout() << ", remaining timeout "
                    << (it->_time - time.getTime()) / 1000000 << " ms\n";
            }
            if (_visitorQueue.empty()) {
                out << "None\n";
            }
            out << "</ul>\n";
            if (_visitorMessages.size() > 0) {
                out << "<h3>Waiting for the following visitor replies</h3>"
                    << "\n<table><tr>"
                    << "<th>Storage API message id</th>"
                    << "<th>Visitor id</th>"
                    << "<th>Timestamp</th>"
                    << "<th>Timeout</th>"
                    << "<th>Destination</th>"
                    << "</tr>\n";
                for (std::map<api::StorageMessage::Id,
                              MessageInfo>::const_iterator it
                        = _visitorMessages.begin();
                     it != _visitorMessages.end(); ++it)
                {
                    out << "<tr>"
                        << "<td>" << it->first << "</td>"
                        << "<td>" << it->second.id << "</td>"
                        << "<td>" << it->second.timestamp << "</td>"
                        << "<td>" << it->second.timeout << "</td>"
                        << "<td>" << it->second.destination << "</td>"
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
    vespalib::LockGuard sync(_statusLock);
    vespalib::MonitorGuard waiter(_statusMonitor);
        // Send all subrequests
    uint32_t parts = _visitorThread.size();
    for (uint32_t i=0; i<parts; ++i) {
        std::shared_ptr<RequestStatusPage> cmd(new RequestStatusPage(path));
        std::ostringstream token;
        token << "Visitor thread " << i;
        cmd->setSortToken(token.str());
        _visitorThread[i].first->processMessage(0, cmd);
    }
        // Wait for all replies to come back
    while (_statusRequest.size() < parts) {
        waiter.wait();
    }
    std::sort(_statusRequest.begin(), _statusRequest.end(), StatusReqSorter());

        // Create output
    for (uint32_t i=0; i<_statusRequest.size(); ++i) {
        out << "<h2>" << _statusRequest[i]->getSortToken()
            << "</h2>\n" << _statusRequest[i]->getStatus() << "\n";
    }
    _statusRequest.clear();
}

} // storage
