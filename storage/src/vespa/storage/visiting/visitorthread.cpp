// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "visitorthread.h"
#include "messages.h"
#include <vespa/storageframework/generic/thread/thread.h>
#include <vespa/storageframework/generic/clock/clock.h>
#include <vespa/document/base/exceptions.h>
#include <vespa/document/select/bodyfielddetector.h>
#include <vespa/document/select/parser.h>
#include <vespa/messagebus/rpcmessagebus.h>
#include <vespa/storageapi/message/datagram.h>
#include <vespa/storage/common/statusmessages.h>
#include <vespa/storage/config/config-stor-server.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".visitor.thread");

using storage::api::ReturnCode;

namespace storage {

VisitorThread::Event::Event(Event&& other) noexcept
    : _visitorId(other._visitorId),
      _message(std::move(other._message)),
      _mbusReply(std::move(other._mbusReply)),
      _timer(other._timer),
      _type(other._type)
{
}

VisitorThread::Event::~Event() = default;

VisitorThread::Event&
VisitorThread::Event::operator= (Event&& other) noexcept
{
    _visitorId = other._visitorId;
    _message = std::move(other._message);
    _mbusReply = std::move(other._mbusReply);
    _timer = other._timer;
    _type = other._type;
    return *this;
}

VisitorThread::Event::Event(api::VisitorId visitor, std::shared_ptr<api::StorageMessage> msg)
    : _visitorId(visitor),
      _message(std::move(msg)),
      _timer(),
      _type(Type::PERSISTENCE)
{
}

VisitorThread::Event::Event(api::VisitorId visitor, mbus::Reply::UP reply)
    : _visitorId(visitor),
      _mbusReply(std::move(reply)),
      _timer(),
      _type(Type::MBUS)
{
}

namespace {
    vespalib::string getThreadName(uint32_t i) {
        vespalib::asciistream name;
        name << "Visitor thread " << i;
        return name.str();
    }
}

VisitorThread::VisitorThread(uint32_t threadIndex,
                             StorageComponentRegister& componentRegister,
                             VisitorMessageSessionFactory& messageSessionFac,
                             VisitorFactory::Map& visitorFactories,
                             VisitorThreadMetrics& metrics,
                             VisitorMessageHandler& sender)
    : _visitors(),
      _recentlyCompleted(),
      _queue(),
      _lock(),
      _cond(),
      _currentlyRunningVisitor(_visitors.end()),
      _messageSender(sender),
      _metrics(metrics),
      _threadIndex(threadIndex),
      _disconnectedVisitorTimeout(0), // Need config to set values
      _ignoreNonExistingVisitorTimeLimit(0),
      _defaultParallelIterators(0),
      _iteratorsPerBucket(1),
      _defaultPendingMessages(0),
      _defaultDocBlockSize(0),
      _visitorMemoryUsageLimit(UINT32_MAX),
      _defaultDocBlockTimeout(180s),
      _defaultVisitorInfoTimeout(60s),
      _timeBetweenTicks(1000),
      _component(componentRegister, getThreadName(threadIndex)),
      _messageSessionFactory(messageSessionFac),
      _visitorFactories(visitorFactories)
{
    _thread = _component.startThread(*this, 30s, 1s, 1, vespalib::CpuUsage::Category::READ);
    _component.registerMetricUpdateHook(*this, 5s);
}

VisitorThread::~VisitorThread()
{
    if (_thread) {
        _thread->interruptAndJoin(_cond);
    }
}

void
VisitorThread::updateMetrics(const MetricLockGuard &) {
    std::lock_guard sync(_lock);
    _metrics.queueSize.addValue(_queue.size());
}

void
VisitorThread::shutdown()
{
        // Stop event thread
    if (_thread) {
        _thread->interruptAndJoin(_cond);
        _thread.reset();
    }

    // Answer all queued up commands and clear queue
    {
        std::lock_guard sync(_lock);
        for (const Event & event : _queue) {
            if (event._message.get()) {
                if (!event._message->getType().isReply()
                    && (event._message->getType() != api::MessageType::INTERNAL
                        || dynamic_cast<const api::InternalCommand&>(*event._message).getType() != PropagateVisitorConfig::ID))
                {
                    std::shared_ptr<api::StorageReply> reply(
                            dynamic_cast<api::StorageCommand&>(*event._message).makeReply());
                    reply->setResult(api::ReturnCode(api::ReturnCode::ABORTED, "Shutting down storage node."));
                    _messageSender.send(reply);
                }
            }
        }
        _queue.clear();
    }
    // Close all visitors. Send create visitor replies
    for (auto it = _visitors.begin(); it != _visitors.end();) {
        LOG(debug, "Force-closing visitor %s as we're shutting down.",
            it->second->getVisitorName().c_str());
        _currentlyRunningVisitor = it++;
        _currentlyRunningVisitor->second->forceClose();
        close();
    }
}

void
VisitorThread::processMessage(api::VisitorId id,
                              const std::shared_ptr<api::StorageMessage>& msg)
{
    {
        std::unique_lock sync(_lock);
        _queue.emplace_back(id, msg);
    }
    _cond.notify_one();
}

VisitorThread::Event
VisitorThread::popNextQueuedEventIfAvailable()
{
    std::lock_guard guard(_lock);
    if (!_queue.empty()) {
        Event e(std::move(_queue.front()));
        _queue.pop_front();
        return e;
    }
    return {};
}

void
VisitorThread::run(framework::ThreadHandle& thread)
{
    LOG(debug, "Started visitor thread with pid %d.", getpid());
    // Loop forever. Process the visiting input message queue and periodicly
    // give visitors something to trigger of.
    Event entry;
    while (!thread.interrupted()) {
        thread.registerTick(framework::PROCESS_CYCLE);

        // Get next message from input queue
        entry = popNextQueuedEventIfAvailable();
        if (entry.empty()) {
            // If none, give visitors something to trigger of.
            tick();
            std::unique_lock guard(_lock);
            if (_queue.empty()) {
                _cond.wait_for(guard, std::chrono::milliseconds(_timeBetweenTicks.load(std::memory_order_relaxed)));
                thread.registerTick(framework::WAIT_CYCLE);
            }
            continue;
        } else {
            // Don't count propagate visitor commands as actual visitor
            // commands. (Not counting it makes metric be unused and
            // disappear when no visiting is done)
            if (entry._message.get() &&
                (entry._message->getType() != api::MessageType::INTERNAL
                 || dynamic_cast<api::InternalCommand&>(*entry._message).getType() != PropagateVisitorConfig::ID))
            {
                entry._timer.stop(_metrics.averageQueueWaitingTime);
            }
        }

        bool handled = false;
        ReturnCode result(ReturnCode::OK);
        try{
            _currentlyRunningVisitor = _visitors.find(entry._visitorId);

            if (entry._message.get()) {
                // If visitor doesn't exist, log failure only if it wasn't
                // recently deleted
                if ((_currentlyRunningVisitor == _visitors.end()) &&
                    (entry._message->getType() != api::MessageType::VISITOR_CREATE) &&
                    (entry._message->getType() != api::MessageType::INTERNAL))
                {
                    handleNonExistingVisitorCall(entry, result);
                } else {
                    handled = entry._message->callHandler(*this, entry._message);
                }
            } else {
                if (_currentlyRunningVisitor == _visitors.end()) {
                    handleNonExistingVisitorCall(entry, result);
                } else {
                    _currentlyRunningVisitor->second->handleDocumentApiReply(std::move(entry._mbusReply), _metrics);
                    if (_currentlyRunningVisitor->second->isCompleted()) {
                        close();
                    }
                    handled = true;
                }
            }

            if (!handled) {
                result = ReturnCode(ReturnCode::IGNORED, "Unwanted");
            }
        } catch (std::exception& e) {
            vespalib::asciistream ost;
            ost << "Failed to handle visitor message:" << e.what();
            LOG(warning, "Failed handling visitor message: %s", ost.str().data());
            result = ReturnCode(ReturnCode::INTERNAL_FAILURE, ost.str());
            if (entry._message.get() && entry._message->getType() == api::MessageType::VISITOR_CREATE) {
                _messageSender.closed(entry._visitorId);
                _metrics.failedVisitors.inc(1);
            }
        }
        _currentlyRunningVisitor = _visitors.end();

        if (!handled && entry._message.get() &&
            !entry._message->getType().isReply())
        {
            auto& cmd = dynamic_cast<api::StorageCommand&>(*entry._message);
            std::shared_ptr<api::StorageReply> reply(cmd.makeReply());
            reply->setResult(result);
            _messageSender.send(reply);
        }
    }
}

void
VisitorThread::tick()
{
    // Give all visitors an event
    for (auto it = _visitors.begin(); it != _visitors.end();) {
        LOG(spam, "Giving tick to visitor %s.", it->second->getVisitorName().c_str());
        it->second->continueVisitor();
        if (it->second->isCompleted()) {
            LOG(debug, "Closing visitor %s. Visitor marked as completed",
                it->second->getVisitorName().c_str());
            _currentlyRunningVisitor = it++;
            close();
        } else {
            ++it;
        }
    }
}

void
VisitorThread::close()
{
    vespalib::steady_time closeTime = _component.getClock().getMonotonicTime();

    Visitor& v = *_currentlyRunningVisitor->second;

    _metrics.averageVisitorLifeTime.addValue(vespalib::count_ms(closeTime - v.getStartTime()));
    v.finalize();
    _messageSender.closed(_currentlyRunningVisitor->first);
    if (v.failed()) {
        _metrics.abortedVisitors.inc(1);
    } else {
        _metrics.completedVisitors.inc(1);
    }
    vespalib::steady_time currentTime(_component.getClock().getMonotonicTime());
    trimRecentlyCompletedList(currentTime);
    _recentlyCompleted.emplace_back(_currentlyRunningVisitor->first, currentTime);
    _visitors.erase(_currentlyRunningVisitor);
    _currentlyRunningVisitor = _visitors.end();
}

void
VisitorThread::trimRecentlyCompletedList(vespalib::steady_time currentTime)
{
    vespalib::steady_time recentLimit(currentTime - 30s);
    // Dump all elements that aren't recent anymore
    while (!_recentlyCompleted.empty()
           && _recentlyCompleted.front().second < recentLimit)
    {
        _recentlyCompleted.pop_front();
    }
}

void
VisitorThread::handleNonExistingVisitorCall(const Event& entry, ReturnCode& code)
{
    // Get current time. Set the time that is the oldest still recent.
    trimRecentlyCompletedList(_component.getClock().getMonotonicTime());

    // Go through all recent visitors. Ignore request if recent
    for (const auto& e : _recentlyCompleted) {
        if (e.first == entry._visitorId) {
            code = ReturnCode(ReturnCode::ILLEGAL_PARAMETERS,
                              "Visitor recently completed/failed/aborted.");
            return;
        }
    }

    vespalib::asciistream ost;
    ost << "Visitor " << entry._visitorId << " no longer exist";
    code = ReturnCode(ReturnCode::ILLEGAL_PARAMETERS, ost.str());
}

/**
 * Utility function to get a visitor instance from a given library.
 */
std::shared_ptr<Visitor>
VisitorThread::createVisitor(vespalib::stringref libName,
                              const vdslib::Parameters& params,
                              vespalib::asciistream & error)
{
    vespalib::string str = libName;
    std::transform(str.begin(), str.end(), str.begin(), tolower);

    auto it = _visitorFactories.find(str);
    if (it == _visitorFactories.end()) {
        error << "Visitor library " << str << " not found.";
        return {};
    }

    auto libIter = _libs.find(str);
    if (libIter == _libs.end()) {
        _libs[str] = std::shared_ptr<VisitorEnvironment>(
                it->second->makeVisitorEnvironment(_component).release());
        libIter = _libs.find(str);
    }

    try{
        std::shared_ptr<Visitor> visitor(it->second->makeVisitor(_component, *libIter->second, params));
        if (!visitor.get()) {
            error << "Factory function in '" << str << "' failed.";
        }
        return visitor;
    } catch (std::exception& e) {
        error << "Failed to create visitor instance of type " << libName
              << ": " << e.what();
        return {};
    }
}

namespace {

std::unique_ptr<mbus::Route>
getDataAddress(const api::CreateVisitorCommand& cmd)
{
    return std::make_unique<mbus::Route>(mbus::Route::parse(cmd.getDataDestination()));
}

std::unique_ptr<mbus::Route>
getControlAddress(const api::CreateVisitorCommand& cmd)
{
    return std::make_unique<mbus::Route>(mbus::Route::parse(cmd.getControlDestination()));
}

void
validateDocumentSelection(const document::DocumentTypeRepo& repo,
                          const document::select::Node& selection)
{
    // Force building a field path for all field references since field path
    // correctness is not checked during regular document selection parsing.
    // This is not in any way speed optimal, but is far less intrusive and
    // risky than trying to rewrite the logic of Visitor/VisitorThread
    // to handle exceptions thrown during attach()/continueVisitor().
    try {
        document::select::BodyFieldDetector detector(repo);
        selection.visit(detector);
    } catch (vespalib::IllegalArgumentException& e) {
        throw document::select::ParsingFailedException(e.getMessage());
    }
}

}

bool
VisitorThread::onCreateVisitor(
        const std::shared_ptr<api::CreateVisitorCommand>& cmd)
{
    metrics::MetricTimer visitorTimer;
    assert(_defaultDocBlockSize); // Ensure we've gotten a config
    assert(_currentlyRunningVisitor == _visitors.end());
    ReturnCode result(ReturnCode::OK);
    std::unique_ptr<document::select::Node> docSelection;
    std::unique_ptr<mbus::Route> controlAddress;
    std::unique_ptr<mbus::Route> dataAddress;
    std::shared_ptr<Visitor> visitor;
    do {
        // If no buckets are specified, fail command
        if (cmd->getBuckets().empty()) {
            result = ReturnCode(ReturnCode::ILLEGAL_PARAMETERS, "No buckets specified");
            LOG(warning, "CreateVisitor(%s): No buckets specified. Aborting.",
                         cmd->getInstanceId().c_str());
            break;
        }
        // Get the source address
        controlAddress = getControlAddress(*cmd);
        dataAddress = getDataAddress(*cmd);
        // Attempt to load library containing visitor
        vespalib::asciistream errors;
        visitor = createVisitor(cmd->getLibraryName(), cmd->getParameters(), errors);
        if (!visitor) {
            result = ReturnCode(ReturnCode::ILLEGAL_PARAMETERS, errors.str());
            LOG(warning, "CreateVisitor(%s): Failed to create visitor: %s",
                         cmd->getInstanceId().c_str(), errors.str().data());
            break;
        }
        // Set visitor parameters
        if (cmd->getMaximumPendingReplyCount() != 0) {
            visitor->setMaxPending(cmd->getMaximumPendingReplyCount());
        } else {
            visitor->setMaxPending(_defaultPendingMessages);
        }

        visitor->setFieldSet(cmd->getFieldSet());

        if (cmd->visitRemoves()) {
            visitor->visitRemoves();
        }

        visitor->setMaxParallel(_defaultParallelIterators);
        visitor->setMaxParallelPerBucket(_iteratorsPerBucket);

        visitor->setDocBlockSize(_defaultDocBlockSize);
        visitor->setMemoryUsageLimit(_visitorMemoryUsageLimit);

        visitor->setDocBlockTimeout(_defaultDocBlockTimeout);
        visitor->setVisitorInfoTimeout(_defaultVisitorInfoTimeout);
        visitor->setOwnNodeIndex(_component.getIndex());
        visitor->setBucketSpace(cmd->getBucketSpace());

        // Parse document selection
        try{
            if (!cmd->getDocumentSelection().empty()) {
                std::shared_ptr<const document::DocumentTypeRepo> repo(_component.getTypeRepo()->documentTypeRepo);
                const document::BucketIdFactory& idFactory(_component.getBucketIdFactory());
                document::select::Parser parser(*repo, idFactory);
                docSelection = parser.parse(cmd->getDocumentSelection());
                validateDocumentSelection(*repo, *docSelection);
            }
        } catch (document::DocumentTypeNotFoundException& e) {
            vespalib::asciistream ost;
            ost << "Failed to parse document select string '"
                << cmd->getDocumentSelection() << "': " << e.getMessage();
            result = ReturnCode(ReturnCode::ILLEGAL_PARAMETERS, ost.str());
            LOG(warning, "CreateVisitor(%s): %s",
                         cmd->getInstanceId().c_str(), ost.str().data());
            break;
        } catch (document::select::ParsingFailedException& e) {
            vespalib::asciistream ost;
            ost << "Failed to parse document select string '"
                << cmd->getDocumentSelection() << "': " << e.getMessage();
            result = ReturnCode(ReturnCode::ILLEGAL_PARAMETERS, ost.str());
            LOG(warning, "CreateVisitor(%s): %s",
                         cmd->getInstanceId().c_str(), ost.str().data());
            break;
        }
        LOG(debug, "CreateVisitor(%s): Successfully created visitor",
                   cmd->getInstanceId().c_str());
        // Insert visitor prior to creating successful reply.
    } while (false);
    // Start the visitor last, as to ensure client will receive
    // visitor create reply first, and that all errors we could detect
    // resulted in proper error code in reply..
    if (result.success()) {
        _visitors[cmd->getVisitorId()] = visitor;
        try{
            VisitorMessageSession::UP messageSession(
                    _messageSessionFactory.createSession(*visitor, *this));
            documentapi::Priority::Value documentPriority =
                _messageSessionFactory.toDocumentPriority(cmd->getPriority());
            visitor->start(cmd->getVisitorId(),
                           cmd->getVisitorCmdId(),
                           cmd->getInstanceId(),
                           cmd->getBuckets(),
                           framework::MicroSecTime(cmd->getFromTime()),
                           framework::MicroSecTime(cmd->getToTime()),
                           std::move(docSelection),
                           cmd->getDocumentSelection(),
                           _messageSender,
                           std::move(messageSession),
                           documentPriority);
            visitor->attach(cmd, *controlAddress, *dataAddress, cmd->getTimeout());
        } catch (std::exception& e) {
            // We don't handle exceptions from this code, as we've
            // added visitor to internal structs we'll end up calling
            // close() twice.
            LOG(error, "Got exception we can't handle: %s", e.what());
            assert(false);
        }
        _metrics.createdVisitors.inc(1);
        visitorTimer.stop(_metrics.averageVisitorCreationTime);
    } else {
        // Send reply
        auto reply = std::make_shared<api::CreateVisitorReply>(*cmd);
        reply->setResult(result);
        _messageSender.closed(cmd->getVisitorId());
        _messageSender.send(reply);
    }
    return true;
}

void
VisitorThread::handleMessageBusReply(mbus::Reply::UP reply, Visitor& visitor)
{
    {
        std::lock_guard sync(_lock);
        _queue.emplace_back(visitor.getVisitorId(), std::move(reply));
    }
    _cond.notify_all();
}

bool
VisitorThread::onInternal(const std::shared_ptr<api::InternalCommand>& cmd)
{
    switch (cmd->getType()) {
    case PropagateVisitorConfig::ID:
        {
            auto& pcmd = dynamic_cast<PropagateVisitorConfig&>(*cmd);
            const vespa::config::content::core::StorVisitorConfig& config(pcmd.getConfig());
            if (_defaultDocBlockSize != 0) { // Live update
                LOG(config, "Updating visitor thread configuration in visitor "
                            "thread %u: "
                            "Current config(disconnectedVisitorTimeout %u,"
                            " ignoreNonExistingVisitorTimeLimit %u,"
                            " defaultParallelIterators %u,"
                            " iteratorsPerBucket %u,"
                            " defaultPendingMessages %u,"
                            " defaultDocBlockSize %u,"
                            " visitorMemoryUsageLimit %u,"
                            " defaultDocBlockTimeout %" PRIu64 ","
                            " defaultVisitorInfoTimeout %" PRIu64 ") "
                            "New config(disconnectedVisitorTimeout %u,"
                            " ignoreNonExistingVisitorTimeLimit %u,"
                            " defaultParallelIterators %u,"
                            " defaultPendingMessages %u,"
                            " defaultDocBlockSize %u,"
                            " visitorMemoryUsageLimit %u,"
                            " defaultDocBlockTimeout %u,"
                            " defaultVisitorInfoTimeout %u) ",
                            _threadIndex,
                            _disconnectedVisitorTimeout,
                            _ignoreNonExistingVisitorTimeLimit,
                            _defaultParallelIterators,
                            _iteratorsPerBucket,
                            _defaultPendingMessages,
                            _defaultDocBlockSize,
                            _visitorMemoryUsageLimit,
                            vespalib::count_ms(_defaultDocBlockTimeout),
                            vespalib::count_ms(_defaultVisitorInfoTimeout),
                            config.disconnectedvisitortimeout,
                            config.ignorenonexistingvisitortimelimit,
                            config.defaultparalleliterators,
                            config.defaultpendingmessages,
                            config.defaultdocblocksize,
                            config.visitorMemoryUsageLimit,
                            config.defaultdocblocktimeout,
                            config.defaultinfotimeout
                   );
            }
            _disconnectedVisitorTimeout = config.disconnectedvisitortimeout;
            _ignoreNonExistingVisitorTimeLimit = config.ignorenonexistingvisitortimelimit;
            _defaultParallelIterators = config.defaultparalleliterators;
            _defaultPendingMessages = config.defaultpendingmessages;
            _defaultDocBlockSize = config.defaultdocblocksize;
            _visitorMemoryUsageLimit = config.visitorMemoryUsageLimit;
            _defaultDocBlockTimeout = std::chrono::milliseconds(config.defaultdocblocktimeout);
            _defaultVisitorInfoTimeout = std::chrono::milliseconds(config.defaultinfotimeout);
            if (_defaultParallelIterators < 1) {
                LOG(config, "Cannot use value of defaultParallelIterators < 1");
                _defaultParallelIterators = 1;
            }
            if (_defaultPendingMessages < 1) {
                LOG(config, "Cannot use value of defaultPendingMessages < 1");
                _defaultPendingMessages = 1;
            }
            if (_defaultDocBlockSize < 1024) {
                LOG(config, "Refusing to use default block size less than 1k");
                _defaultDocBlockSize = 1024;
            }
            if (_defaultDocBlockTimeout < 1ms) {
                LOG(config, "Cannot use value of defaultDocBlockTimeout < 1");
                _defaultDocBlockTimeout = 1ms;
            }
            break;
        }
    case RequestStatusPage::ID:
        {
            LOG(spam, "Got RequestStatusPage request");
            auto& rsp = dynamic_cast<RequestStatusPage&>(*cmd);
            vespalib::asciistream ost;
            getStatus(ost, rsp.getPath());
            _messageSender.send(std::make_shared<RequestStatusPageReply>(rsp, ost.str()));
            break;
        }
    default:
        {
            LOG(error, "Got unknown internal message type %u: %s",
                       cmd->getType(), cmd->toString().c_str());
            return false;
        }
    }
    return true;
}

bool
VisitorThread::onInternalReply(const std::shared_ptr<api::InternalReply>& r)
{
    switch (r->getType()) {
    case GetIterReply::ID:
        {
            auto reply = std::dynamic_pointer_cast<GetIterReply>(r);
            assert(reply.get());
            _currentlyRunningVisitor->second->onGetIterReply(reply, _metrics);
            if (_currentlyRunningVisitor->second->isCompleted()) {
                LOG(debug, "onGetIterReply(%s): Visitor completed.",
                    _currentlyRunningVisitor->second->getVisitorName().c_str());
                close();
            }
            break;
        }
    case CreateIteratorReply::ID:
        {
            auto reply = std::dynamic_pointer_cast<CreateIteratorReply>(r);
            assert(reply.get());
            _currentlyRunningVisitor->second->onCreateIteratorReply(reply, _metrics);
            break;
        }
    default:
        {
            LOG(error, "Got unknown internal message type %u: %s",
                r->getType(), r->toString().c_str());
            return false;
        }
    }
    return true;
}

void
VisitorThread::getStatus(vespalib::asciistream& out,
                         const framework::HttpUrlPath& path) const
{
    bool showAll(path.hasAttribute("allvisitors"));
    bool verbose(path.hasAttribute("verbose"));
    uint32_t visitor(path.get("visitor", 0u));
    bool status(!path.hasAttribute("visitor"));

    if (status && verbose) {
        out << "<h3>Visitor libraries loaded</h3>\n<ul>\n";
        if (_libs.empty()) {
            out << "None\n";
        }
        for (const auto& lib : _libs) {
            out << "<li>" << lib.first << "\n";
        }
        out << "</ul>\n";

        out << "<h3>Recently completed/failed/aborted visitors</h3>\n<ul>\n";
        if (_recentlyCompleted.empty()) {
            out << "None\n";
        }
        for (const auto& cv : _recentlyCompleted) {
            out << "<li> Visitor " << cv.first << " done at "
                << vespalib::to_string(vespalib::to_utc(cv.second)) << "\n";
        }
        out << "</ul>\n";
        out << "<h3>Current queue size: " << _queue.size() << "</h3>\n";
        out << "<h3>Config:</h3>\n"
            << "<table border=\"1\"><tr><td>Parameter</td><td>Value</td></tr>\n"
            << "<tr><td>Disconnected visitor timeout</td><td>"
            << _disconnectedVisitorTimeout << "</td></tr>\n"
            << "<tr><td>Ignore non-existing visitor timelimit</td><td>"
            << _ignoreNonExistingVisitorTimeLimit << "</td></tr>\n"
            << "<tr><td>Default parallel iterators</td><td>"
            << _defaultParallelIterators << "</td></tr>\n"
            << "<tr><td>Iterators per bucket</td><td>"
            << _iteratorsPerBucket << "</td></tr>\n"
            << "<tr><td>Default pending messages</td><td>"
            << _defaultPendingMessages << "</td></tr>\n"
            << "<tr><td>Default DocBlock size</td><td>"
            << _defaultDocBlockSize << "</td></tr>\n"
            << "<tr><td>Default DocBlock timeout (ms)</td><td>"
            << vespalib::count_ms(_defaultDocBlockTimeout) << "</td></tr>\n"
            << "<tr><td>Visitor memory usage limit</td><td>"
            << _visitorMemoryUsageLimit << "</td></tr>\n"
            << "</table>\n";
    }
    if (showAll) {
        for (const auto& v : _visitors) {
            out << "<h3>Visitor " << v.first << "</h3>\n";
            std::ostringstream tmp;
            v.second->getStatus(tmp, verbose);
            out << tmp.str();
        }
    } else if (path.hasAttribute("visitor")) {
        out << "<h3>Visitor " << visitor << "</h3>\n";
        auto it = _visitors.find(visitor);
        if (it == _visitors.end()) {
            out << "Not found\n";
        } else {
            std::ostringstream tmp;
            it->second->getStatus(tmp, verbose);
            out << tmp.str();
        }
    } else { // List visitors
        out << "<h3>Active visitors</h3>\n";
        if (_visitors.empty()) {
            out << "None\n";
        }
        for (const auto & v : _visitors) {
            out << "<a href=\"?visitor=" << v.first
                << (verbose ? "&verbose" : "") << "\">Visitor "
                << v.first << "</a><br>\n";
        }
    }
}

} // storage
