// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "metricmanager.h"
#include "countmetric.h"
#include "valuemetric.h"
#include "metricset.h"
#include <vespa/config/print/ostreamconfigwriter.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/config/subscription/configsubscriber.hpp>
#include <cinttypes>
#include <set>
#include <sstream>
#include <cassert>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".metrics.manager");

namespace metrics {

using Config = MetricsmanagerConfig;
using vespalib::IllegalStateException;
using vespalib::IllegalArgumentException;
using vespalib::make_string_short::fmt;
using vespalib::count_ms;
using vespalib::count_s;
using vespalib::from_s;
using vespalib::to_s;
using vespalib::to_string;

MetricManager::ConsumerSpec::ConsumerSpec() = default;
MetricManager::ConsumerSpec::~ConsumerSpec() = default;

time_point
MetricManager::Timer::getTime() const {
    return vespalib::system_clock::now();
}

void
MetricManager::assertMetricLockLocked(const MetricLockGuard& g) const {
    if ( ! g.owns(_waiter)) {
        throw IllegalArgumentException("Given lock does not lock the metric lock.", VESPA_STRLOC);
    }
}

vespalib::string
MetricManager::ConsumerSpec::toString() const
{
    vespalib::asciistream out;
    out << "ConsumerSpec(";
    std::set<Metric::String> sortedMetrics;
    for (const Metric::String & name : includedMetrics) {
        sortedMetrics.insert(name);
    }
    for (const auto & s : sortedMetrics) {
        out << "\n" << "  " << s;
    }
    out << ")";
    return out.str();
}

void
MetricManager::ConsumerSpec::addMemoryUsage(MemoryConsumption& mc) const
{
    mc._consumerMetricsInTotal += includedMetrics.size();
    for (const Metric::String & name : includedMetrics) {
        mc._consumerMetricIds += mc.getStringMemoryUsage(name, mc._consumerMetricIdsUnique) + sizeof(Metric::String);
    }
}

MetricManager::MetricManager()
    : MetricManager(std::make_unique<Timer>())
{ }

MetricManager::MetricManager(std::unique_ptr<Timer> timer)
    : _activeMetrics("Active metrics showing updates since last snapshot"),
      _configSubscriber(),
      _configHandle(),
      _config(),
      _consumerConfig(),
      _snapshots(),
      _totalMetrics(std::make_shared<MetricSnapshot>("Empty metrics before init", 0s, _activeMetrics.getMetrics(), false)),
      _timer(std::move(timer)),
      _lastProcessedTime(),
      _snapshotUnsetMetrics(false),
      _consumerConfigChanged(false),
      _metricManagerMetrics("metricmanager", {}, "Metrics for the metric manager upkeep tasks", nullptr),
      _periodicHookLatency("periodichooklatency", {}, "Time in ms used to update a single periodic hook", &_metricManagerMetrics),
      _snapshotHookLatency("snapshothooklatency", {}, "Time in ms used to update a single snapshot hook", &_metricManagerMetrics),
      _resetLatency("resetlatency", {}, "Time in ms used to reset all metrics.", &_metricManagerMetrics),
      _snapshotLatency("snapshotlatency", {}, "Time in ms used to take a snapshot", &_metricManagerMetrics),
      _sleepTimes("sleeptime", {}, "Time in ms worker thread is sleeping", &_metricManagerMetrics),
      _stop_requested(false),
      _thread()
{
    registerMetric(getMetricLock(), _metricManagerMetrics);
}

MetricManager::~MetricManager()
{
    stop();
}

void
MetricManager::stop()
{
    request_stop();
    {
        MetricLockGuard sync(_waiter);
        _cond.notify_all();
    }
    if (_thread.joinable()) {
        _thread.join();
    }
}

void
MetricManager::addMetricUpdateHook(UpdateHook& hook)
{
    hook.updateNextCall(_timer->getTime());
    std::lock_guard sync(_waiter);
    if ( hook.is_periodic() ) {
        for (UpdateHook * pHook : _periodicUpdateHooks) {
            if (pHook == &hook) {
                LOG(warning, "Update hook already registered");
                return;
            }
        }
        _periodicUpdateHooks.push_back(&hook);
    } else {
        for (UpdateHook * sHook : _snapshotUpdateHooks) {
            if (sHook == &hook) {
                LOG(warning, "Update hook already registered");
                return;
            }
        }
        _snapshotUpdateHooks.push_back(&hook);
    }
}

void
MetricManager::removeMetricUpdateHook(UpdateHook& hook)
{
    std::lock_guard sync(_waiter);
    if (hook.is_periodic()) {
        for (auto it = _periodicUpdateHooks.begin(); it != _periodicUpdateHooks.end(); it++) {
            if (*it == &hook) {
                _periodicUpdateHooks.erase(it);
                return;
            }
        }
    } else {
        for (auto it = _snapshotUpdateHooks.begin(); it != _snapshotUpdateHooks.end(); it++) {
            if (*it == &hook) {
                _snapshotUpdateHooks.erase(it);
                return;
            }
        }
    }
    LOG(warning, "Update hook not registered");
}

bool
MetricManager::isInitialized() const {
    return static_cast<bool>(_configHandle);
}

void
MetricManager::init(const config::ConfigUri & uri, bool startThread)
{
    if (isInitialized()) {
        throw IllegalStateException("The metric manager have already been initialized. "
                                    "It can only be initialized once.", VESPA_STRLOC);
    }
    LOG(debug, "Initializing metric manager.");
    _configSubscriber = std::make_unique<config::ConfigSubscriber>(uri.getContext());
    _configHandle = _configSubscriber->subscribe<Config>(uri.getConfigId());
    _configSubscriber->nextConfig();
    configure(getMetricLock(), _configHandle->getConfig());
    LOG(debug, "Starting worker thread, waiting for first iteration to complete.");
    if (startThread) {
        _thread = std::thread([this](){run();});
        // Wait for first iteration to have completed, such that it is safe
        // to access snapshots afterwards.
        MetricLockGuard sync(_waiter);
        while (_lastProcessedTime.load(std::memory_order_relaxed) == time_point()) {
            _cond.wait_for(sync, 1ms);
        }
    } else {
        _configSubscriber.reset();
    }
    LOG(debug, "Metric manager completed initialization.");
}

namespace {

struct Path {
    vespalib::StringTokenizer _path;

    Path(vespalib::stringref fullpath) : _path(fullpath, ".") { }

    vespalib::string toString() const {
        vespalib::asciistream ost;
        ost << _path[0];
        for (uint32_t i=1; i<path().size(); ++i) ost << "." << _path[i];
        return ost.str();
    }

    bool matchesPattern(const Path& p) const {
        if (path().size() != p.path().size()) {
            return false;
        }
        for (uint32_t i=0; i<p.path().size(); ++i) {
            if (p._path[i] == "*") continue;
            if (_path[i] != p._path[i]) {
                return false;
            }
        }
        return true;
    }
    const vespalib::StringTokenizer::TokenList& path() const {
        return _path.getTokens();
    }
};

struct ConsumerMetricBuilder : public MetricVisitor {
    const Config::Consumer& _consumer;
    std::vector<Path> _added;
    std::vector<Path> _removed;
    MetricManager::ConsumerSpec _matchedMetrics;
        // Keep a stack of matches to facilitate tree traversal.
    struct Result {
        bool tagAdded;
        bool tagRemoved;
        bool nameAdded;
        bool nameRemoved;
        uint32_t metricCount;

        Result()
            : tagAdded(false), tagRemoved(false),
              nameAdded(false), nameRemoved(false), metricCount(0)
        {}
    };
    std::list<Result> result;

    ConsumerMetricBuilder(const Config::Consumer& c) __attribute__((noinline));
    ~ConsumerMetricBuilder() __attribute__((noinline));

    bool tagAdded(const Metric& metric) {
        for (const auto& s : _consumer.tags) {
            if ((s == "*" && !metric.getTags().empty())
                || metric.hasTag(s))
            {
                return true;
            }
        }
        return false;
    }
    bool tagRemoved(const Metric& metric) {
        for (const auto& s : _consumer.removedtags) {
            if ((s == "*" && !metric.getTags().empty())
                || metric.hasTag(s))
            {
                return true;
            }
        }
        return false;
    }
    bool nameAdded(const Path& mpath) {
        for (const auto& p : _added) {
            if (mpath.matchesPattern(p)) {
                return true;
            }
        }
        return false;
    }
    bool nameRemoved(const Path & mpath) {
        for (const auto& p : _removed) {
            if (mpath.matchesPattern(p)) {
                return true;
            }
        }
        return false;
    }

    bool visitMetricSet(const MetricSet& metricSet, bool autoGenerated) override
    {
        (void) autoGenerated;
        result.push_back(Result());
        // If current metric match anything, use that.
        // Otherwise, copy from parent
        vespalib::string fullName = metricSet.getPath();
        Path path(fullName);
        if (nameRemoved(path)) {
            result.back().nameRemoved = true;
        } else if (nameAdded(path)) {
            result.back().nameAdded = true;
        } else if (tagRemoved(metricSet)) {
            result.back().tagRemoved = true;
        } else if (tagAdded(metricSet)) {
            result.back().tagAdded = true;
        } else if (result.size() > 1) {
            result.back() = *++result.rbegin();
            result.back().metricCount = 0;
        }
        return true;
    }
    void doneVisitingMetricSet(const MetricSet& metricSet) override {
        if (result.back().metricCount > 0 && result.size() != 1) {
            LOG(spam, "Adding metricset %s", metricSet.getPath().c_str());
            _matchedMetrics.includedMetrics.insert(metricSet.getPath());
        }
        result.pop_back();
    }
    bool visitMetric(const Metric& metric, bool autoGenerated) override {
        (void) autoGenerated;
        vespalib::string fullName = metric.getPath();
        Path path(fullName);
        if (result.back().nameRemoved || nameRemoved(path)) return true;
        bool nAdded = (result.back().nameAdded || nameAdded(path));
        if (!nAdded && (result.back().tagRemoved || tagRemoved(metric))) {
            return true;
        }
        if (nAdded || result.back().tagAdded || tagAdded(metric)) {
            _matchedMetrics.includedMetrics.insert(fullName);
            LOG(spam, "Adding metric %s", fullName.c_str());
            for (Result & r : result) {
                ++r.metricCount;
            }
        }
        return true;
    }

};

ConsumerMetricBuilder::ConsumerMetricBuilder(const Config::Consumer& c)
    : _consumer(c),
      _added(),
      _removed(),
      _matchedMetrics()
{
    _added.reserve(_consumer.addedmetrics.size());
    for (const auto& s : _consumer.addedmetrics) {
        _added.emplace_back(s);
    }
    _removed.reserve(_consumer.removedmetrics.size());
    for (const auto& s : _consumer.removedmetrics) {
        _removed.emplace_back(s);
    }
    LOG(spam, "Adding metrics for consumer %s", c.name.c_str());
}
ConsumerMetricBuilder::~ConsumerMetricBuilder() = default;

}

// This function may be called by clients or worker thread. Thus, worker monitor
// might have already been grabbed
void
MetricManager::checkMetricsAltered(const MetricLockGuard & guard)
{
    if (_activeMetrics.getMetrics().isRegistrationAltered() || _consumerConfigChanged) {
        handleMetricsAltered(guard);
    }
}

// When calling this function, the metric lock is already taken. The thread
// monitor lock might be taken.
void
MetricManager::handleMetricsAltered(const MetricLockGuard & guard)
{
    (void) guard;
    if ( ! _config ) {
        LOG(info, "_config is not set -> very odd indeed.");
        return;
    }
    if (_consumerConfig.empty()) {
        LOG(debug, "Setting up consumers for the first time.");
    } else {
        LOG(info, "Metrics registration changes detected. Handling changes.");
    }
    _activeMetrics.getMetrics().clearRegistrationAltered();
    std::map<Metric::String, ConsumerSpec> configMap;
    LOG(debug, "Calculating new consumer config");
    for (const auto & consumer : _config->consumer) {
        ConsumerMetricBuilder consumerMetricBuilder(consumer);
        _activeMetrics.getMetrics().visit(consumerMetricBuilder);
        configMap[consumer.name] = ConsumerSpec(std::move(consumerMetricBuilder._matchedMetrics));
    }
    LOG(debug, "Recreating snapshots to include altered metrics");
    _totalMetrics->recreateSnapshot(_activeMetrics.getMetrics(), _snapshotUnsetMetrics);
    for (const auto & snapshot: _snapshots) {
        snapshot->recreateSnapshot(_activeMetrics.getMetrics(), _snapshotUnsetMetrics);
    }
    LOG(debug, "Setting new consumer config. Clearing dirty flag");
    _consumerConfig.swap(configMap);
    _consumerConfigChanged = false;
}

namespace {

bool
setSnapshotName(std::ostream& out, const char* name, uint32_t length, uint32_t period) {
    if (length % period != 0) return false;
    out << (length / period) << ' ' << name;
    if (length / period != 1) out << "s";
    return true;
}

}

std::vector<MetricManager::SnapSpec>
MetricManager::createSnapshotPeriods(const Config& config)
{
    std::vector<SnapSpec> result;
    try {
        for (auto length : config.snapshot.periods) {
            if (length < 1)
                throw IllegalStateException("Snapshot periods must be positive numbers", VESPA_STRLOC);
            std::ostringstream name;
            if (setSnapshotName(name, "week", length, 60 * 60 * 24 * 7)) {
            } else if (setSnapshotName(name, "day", length, 60 * 60 * 24)) {
            } else if (setSnapshotName(name, "hour", length, 60 * 60)) {
            } else if (setSnapshotName(name, "minute", length, 60)) {
            } else {
                name << length << " seconds";
            }
            result.emplace_back(vespalib::from_s<time_point::duration>(length), name.str());
        }
        for (uint32_t i=1; i<result.size(); ++i) {
            if (result[i].first % result[i-1].first != vespalib::duration::zero()) {
                std::ostringstream ost;
                ost << "Period " << result[i].first << " is not a multiplum of period "
                    << result[i-1].first << " which is needs to be.";
                throw IllegalStateException(ost.str(), VESPA_STRLOC);
            }
        }
    } catch (vespalib::Exception& e) {
        LOG(warning, "Invalid snapshot periods specified. Using defaults: %s", e.getMessage().c_str());
        result.clear();
    }
    if (result.empty()) {
        result.emplace_back(60s * 5, "5 minute");
        result.emplace_back(60s * 60, "1 hour");
        result.emplace_back(60s * 60 * 24, "1 day");
        result.emplace_back(60s * 60 * 24 * 7, "1 week");
    }
    return result;
}

void
MetricManager::configure(const MetricLockGuard & , std::unique_ptr<Config> config)
{
    assert(config);
    if (LOG_WOULD_LOG(debug)) {
        std::ostringstream ost;
        config::OstreamConfigWriter w(ost);
        w.write(*config);
        LOG(debug, "Received new config for metric manager: %s", ost.str().c_str());
    }
    if (_snapshots.empty()) {
        LOG(debug, "Initializing snapshots as this is first configure call");
        std::vector<SnapSpec> snapshotPeriods(createSnapshotPeriods(*config));

        // Set up snapshots only first time. We don't allow live reconfig
        // of snapshot periods.
        time_point currentTime = _timer->getTime();
        _activeMetrics.setFromTime(currentTime);
        uint32_t count = 1;
        for (uint32_t i = 0; i< snapshotPeriods.size(); ++i) {
            uint32_t nextCount = 1;
            if (i + 1 < snapshotPeriods.size()) {
                nextCount = snapshotPeriods[i + 1].first / snapshotPeriods[i].first;
                if ((snapshotPeriods[i + 1].first % snapshotPeriods[i].first) != time_point::duration::zero()) {
                    throw IllegalStateException("Snapshot periods must be multiplum of each other",VESPA_STRLOC);
                }
            }
            _snapshots.push_back(std::make_shared<MetricSnapshotSet>(
                    snapshotPeriods[i].second, snapshotPeriods[i].first, count,
                    _activeMetrics.getMetrics(), _snapshotUnsetMetrics));
            count = nextCount;
        }
        // Add all time snapshot.
        _totalMetrics = std::make_shared<MetricSnapshot>("All time snapshot", 0s, _activeMetrics.getMetrics(), _snapshotUnsetMetrics);
        _totalMetrics->reset(currentTime);
    }
    if (_config.get() == 0 || (_config->consumer.size() != config->consumer.size())) {
        _consumerConfigChanged = true;
    } else {
        for (uint32_t i=0; i<_config->consumer.size(); ++i) {
            if (_config->consumer[i] != config->consumer[i]) {
                _consumerConfigChanged = true;
                break;
            }
        }
    }
    if (_consumerConfigChanged) {
        LOG(debug, "Consumer config changed. Tagging consumer config dirty.");
    }
    _config = std::move(config);

}

const MetricManager::ConsumerSpec *
MetricManager::getConsumerSpec(const MetricLockGuard &, const Metric::String& consumer) const
{
    auto it(_consumerConfig.find(consumer));
    return (it != _consumerConfig.end() ? &it->second : nullptr);
}


namespace {

struct ConsumerMetricVisitor : public MetricVisitor {
    const MetricManager::ConsumerSpec& _metricsToMatch;
    MetricVisitor& _client;

    ConsumerMetricVisitor(const MetricManager::ConsumerSpec& spec, MetricVisitor& clientVisitor)
        : _metricsToMatch(spec),
          _client(clientVisitor)
    { }

    bool visitMetricSet(const MetricSet& metricSet, bool autoGenerated) override {
        if (metricSet.isTopSet()) return true;
        return (_metricsToMatch.contains(metricSet)
                && _client.visitMetricSet(metricSet, autoGenerated));
    }
    void doneVisitingMetricSet(const MetricSet& metricSet) override {
        if (!metricSet.isTopSet()) {
            _client.doneVisitingMetricSet(metricSet);
        }
    }
    bool visitCountMetric(const AbstractCountMetric& metric, bool autoGenerated) override {
        if (_metricsToMatch.contains(metric)) {
            return _client.visitCountMetric(metric, autoGenerated);
        }
        return true;
    }
    bool visitValueMetric(const AbstractValueMetric& metric, bool autoGenerated) override {
        if (_metricsToMatch.contains(metric)) {
            return _client.visitValueMetric(metric, autoGenerated);
        }
        return true;
    }
};

}

void
MetricManager::visit(const MetricLockGuard & guard, const MetricSnapshot& snapshot,
                     MetricVisitor& visitor, const std::string& consumer) const
{
    if (visitor.visitSnapshot(snapshot)) {
        if (consumer == "") {
            snapshot.getMetrics().visit(visitor);
        } else {
            const ConsumerSpec * consumerSpec = getConsumerSpec(guard, consumer);
            if (consumerSpec) {

                ConsumerMetricVisitor consumerVis(*consumerSpec, visitor);
                snapshot.getMetrics().visit(consumerVis);
            } else {
                LOGBP(debug, "Requested metrics for non-defined consumer '%s'.", consumer.c_str());
            }
        }
        visitor.doneVisitingSnapshot(snapshot);
    }
    visitor.doneVisiting();
}

std::vector<time_point::duration>
MetricManager::getSnapshotPeriods(const MetricLockGuard& l) const
{
    assertMetricLockLocked(l);
    std::vector<time_point::duration> result;
    result.reserve(_snapshots.size());
    for (const auto & snapshot : _snapshots) {
        result.emplace_back(snapshot->getPeriod());
    }
    return result;
}

// Client should have grabbed metrics lock before doing this
const MetricSnapshot&
MetricManager::getMetricSnapshot(const MetricLockGuard& l, vespalib::duration period, bool getInProgressSet) const
{
    assertMetricLockLocked(l);
    for (const auto & snapshot : _snapshots) {
        if (snapshot->getPeriod() == period) {
            if (snapshot->getCount() == 1 && getInProgressSet) {
                throw IllegalStateException("No temporary snapshot for set " + snapshot->getName(), VESPA_STRLOC);
            }
            return snapshot->getSnapshot(getInProgressSet);
        }
    }
    throw IllegalArgumentException(fmt("No snapshot for period of length %f exist.", vespalib::to_s(period)), VESPA_STRLOC);
}

// Client should have grabbed metrics lock before doing this
const MetricSnapshotSet&
MetricManager::getMetricSnapshotSet(const MetricLockGuard& l, vespalib::duration period) const
{
    assertMetricLockLocked(l);
    for (const auto & snapshot : _snapshots) {
        if (snapshot->getPeriod() == period) {
            return *snapshot;
        }
    }
    throw IllegalArgumentException(fmt("No snapshot set for period of length %f exist.", to_s(period)), VESPA_STRLOC);
}

void
MetricManager::timeChangedNotification() const
{
    MetricLockGuard sync(_waiter);
    _cond.notify_all();
}

void
MetricManager::updateMetrics()
{
    // Ensure we're not in the way of the background thread
    MetricLockGuard sync(_waiter);
    LOG(debug, "Calling %zu periodic update hooks.", _periodicUpdateHooks.size());
    updatePeriodicMetrics(sync, time_point(), true);
    updateSnapshotMetrics(sync);
}

// When this is called, the thread monitor lock has already been grabbed
time_point
MetricManager::updatePeriodicMetrics(const MetricLockGuard & guard, time_point updateTime, bool outOfSchedule)
{
    assertMetricLockLocked(guard);
    time_point nextUpdateTime = time_point::max();
    time_point preTime = _timer->getTimeInMilliSecs();
    for (auto hook : _periodicUpdateHooks) {
        if (hook->expired(updateTime)) {
            hook->updateMetrics(guard);
            if (hook->expired(updateTime - hook->getPeriod())) {
                if (hook->has_valid_expiry()) {
                    LOG(debug, "Updated hook %s at time %s, but next run in %" PRId64 " seconds have already passed as "
                               "time is %s. Bumping next call to current time + period.",
                        hook->getName(), to_string(hook->getNextCall()).c_str(),
                        count_s(hook->getPeriod()), to_string(updateTime).c_str());
                }
                hook->updateNextCall(updateTime);
            } else {
                hook->updateNextCall();
            }
            time_point postTime = _timer->getTimeInMilliSecs();
            _periodicHookLatency.addValue(count_ms(postTime - preTime));
            preTime = postTime;
        } else if (outOfSchedule) {
            hook->updateMetrics(guard);
            time_point postTime = _timer->getTimeInMilliSecs();
            _periodicHookLatency.addValue(count_ms(postTime - preTime));
            preTime = postTime;
        }
        nextUpdateTime = std::min(nextUpdateTime, hook->getNextCall());
    }
    return nextUpdateTime;
}

// When this is called, the thread monitor lock has already been grabbed.
void
MetricManager::updateSnapshotMetrics(const MetricLockGuard & guard)
{
    assertMetricLockLocked(guard);
    time_point preTime = _timer->getTimeInMilliSecs();
    for (const auto & hook : _snapshotUpdateHooks) {
        hook->updateMetrics(guard);
        time_point postTime = _timer->getTimeInMilliSecs();
        _snapshotHookLatency.addValue(count_ms(postTime - preTime));
        preTime = postTime;
    }
}

void
MetricManager::forceEventLogging()
{
    LOG(debug, "Forcing event logging to happen.");
    // Ensure background thread is not in a current cycle during change.
    MetricLockGuard sync(_waiter);
    _cond.notify_all();
}

void
MetricManager::reset(system_time currentTime)
{
    time_point preTime = _timer->getTimeInMilliSecs();
    // Resetting implies visiting metrics, which needs to grab metric lock
    // to avoid conflict with adding/removal of metrics
    std::lock_guard waiterLock(_waiter);
    _activeMetrics.reset(currentTime);
    for (const auto & snapshot : _snapshots) {
        snapshot->reset(currentTime);
    }
    _totalMetrics->reset(currentTime);
    time_point postTime = _timer->getTimeInMilliSecs();
    _resetLatency.addValue(count_ms(postTime - preTime));
}

void
MetricManager::run()
{
    MetricLockGuard sync(_waiter);
    // For a slow system to still be doing metrics tasks each n'th
    // second, rather than each n'th + time to do something seconds,
    // we constantly add next time to do something from the last timer.
    // For that to work, we need to initialize timers on first iteration
    // to set them to current time.
    system_time currentTime = _timer->getTime();
    for (auto & snapshot : _snapshots) {
        snapshot->setFromTime(currentTime);
    }
    for (auto hook : _periodicUpdateHooks) {
        hook->setNextCall(currentTime);
    }
    // Ensure correct time for first snapshot
    _snapshots[0]->getSnapshot().setToTime(currentTime);
    while (!stop_requested()) {
        currentTime = _timer->getTime();
        time_point next = tick(sync, currentTime);
        if (currentTime < next) {
            vespalib::duration wait_time = next - currentTime;
            _cond.wait_for(sync, wait_time);
            _sleepTimes.addValue(count_ms(wait_time));
        } else {
            _sleepTimes.addValue(0);
        }
    }
}

time_point
MetricManager::tick(const MetricLockGuard & guard, time_point currentTime)
{
    LOG(spam, "Worker thread starting to process for time %s.", to_string(currentTime).c_str());

    // Check for new config and reconfigure
    if (_configSubscriber && _configSubscriber->nextConfigNow()) {
        configure(guard, _configHandle->getConfig());
    }

    // If metrics have changed since last time we did a snapshot,
    // work that out before taking the snapshot, such that new
    // metric can be included
    checkMetricsAltered(guard);

        // Set next work time to the time we want to take next snapshot.
    time_point nextWorkTime = _snapshots[0]->getNextWorkTime();
    time_point nextUpdateHookTime;
    if (nextWorkTime <= currentTime) {
        // If taking a new snapshot or logging, force calls to all
        // update hooks.
        LOG(debug, "%s. Calling update hooks.", nextWorkTime <= currentTime
                 ? "Time to do snapshot" : "Out of sequence event logging");
        nextUpdateHookTime = updatePeriodicMetrics(guard, currentTime, true);
        updateSnapshotMetrics(guard);
    } else {
        // If not taking a new snapshot. Only give update hooks to
        // periodic hooks wanting it.
        nextUpdateHookTime = updatePeriodicMetrics(guard, currentTime, false);
    }
        // Do snapshotting if it is time
    if (nextWorkTime <= currentTime) takeSnapshots(guard, nextWorkTime);

    _lastProcessedTime.store((nextWorkTime <= currentTime ? nextWorkTime : currentTime), std::memory_order_relaxed);
    LOG(spam, "Worker thread done with processing for time %s.",
        to_string(_lastProcessedTime.load(std::memory_order_relaxed)).c_str());
    time_point next = _snapshots[0]->getNextWorkTime();
    next = std::min(next, nextUpdateHookTime);
    return next;
}

void
MetricManager::takeSnapshots(const MetricLockGuard & guard, system_time timeToProcess)
{
    assertMetricLockLocked(guard);
    // If not time to do dump data from active snapshot yet, nothing to do
    if (!_snapshots[0]->timeForAnotherSnapshot(timeToProcess)) {
         LOG(spam, "Not time to process snapshot %s at time %s. Current "
                   "first period (%f) snapshot goes from %s to %s",
             _snapshots[0]->getName().c_str(), to_string(timeToProcess).c_str(),
             to_s(_snapshots[0]->getPeriod()), to_string(_snapshots[0]->getFromTime()).c_str(),
             to_string(_snapshots[0]->getToTime()).c_str());
        return;
    }
    time_point preTime = _timer->getTimeInMilliSecs();
    LOG(debug, "Updating %s snapshot and total metrics at time %s.",
        _snapshots[0]->getName().c_str(), to_string(timeToProcess).c_str());
    MetricSnapshot& firstTarget(_snapshots[0]->getNextTarget());
    firstTarget.reset(_activeMetrics.getFromTime());
    _activeMetrics.addToSnapshot(firstTarget, false, timeToProcess);
    _activeMetrics.addToSnapshot(*_totalMetrics, false, timeToProcess);
    _activeMetrics.reset(timeToProcess);
    LOG(debug, "After snapshotting, active metrics goes from %s to %s, and 5 minute metrics goes from %s to %s.",
        to_string(_activeMetrics.getFromTime()).c_str(), to_string(_activeMetrics.getToTime()).c_str(),
        to_string(firstTarget.getFromTime()).c_str(), to_string(firstTarget.getToTime()).c_str());

        // Update later snapshots if it is time for it
    for (uint32_t i=1; i<_snapshots.size(); ++i) {
        LOG(debug, "Adding data from last snapshot to building snapshot of next period snapshot %s.",
            _snapshots[i]->getName().c_str());
        MetricSnapshot& target(_snapshots[i]->getNextTarget());
        _snapshots[i-1]->getSnapshot().addToSnapshot(target, false, timeToProcess);
        target.setToTime(timeToProcess);
        if (!_snapshots[i]->haveCompletedNewPeriod(timeToProcess)) {
            LOG(debug, "Not time to roll snapshot %s yet. %u of %u snapshot taken at time %s, and period of %f "
                       "is not up yet as we're currently processing for time %s.",
                _snapshots[i]->getName().c_str(), _snapshots[i]->getBuilderCount(), _snapshots[i]->getCount(),
                to_string(_snapshots[i]->getBuilderCount() * _snapshots[i]->getPeriod() + _snapshots[i]->getFromTime()).c_str(),
                to_s(_snapshots[i]->getPeriod()), to_string(timeToProcess).c_str());
            break;
        } else {
            LOG(debug, "Rolled snapshot %s at time %s.",
                _snapshots[i]->getName().c_str(), to_string(timeToProcess).c_str());
        }
    }
    time_point postTime = _timer->getTimeInMilliSecs();
    _snapshotLatency.addValue(count_ms(postTime - preTime));
}

MemoryConsumption::UP
MetricManager::getMemoryConsumption(const MetricLockGuard & guard) const
{
    assertMetricLockLocked(guard);
    auto mc = std::make_unique<MemoryConsumption>();
    mc->_consumerCount += _consumerConfig.size();
    mc->_consumerMeta += sizeof(ConsumerSpec) * _consumerConfig.size();
    for (const auto & consumer : _consumerConfig) {
        mc->_consumerId += mc->getStringMemoryUsage(consumer.first, mc->_consumerIdUnique) + sizeof(Metric::String);
        consumer.second.addMemoryUsage(*mc);
    }
    uint32_t preTotal = mc->getTotalMemoryUsage();
    _activeMetrics.addMemoryUsage(*mc);
    uint32_t postTotal = mc->getTotalMemoryUsage();
    mc->addSnapShotUsage("active", postTotal - preTotal);
    preTotal = postTotal;
    for (const auto & snapshot : _snapshots) {
        snapshot->addMemoryUsage(*mc);
        postTotal = mc->getTotalMemoryUsage();
        mc->addSnapShotUsage(snapshot->getName(), postTotal - preTotal);
        preTotal = postTotal;
    }
    _totalMetrics->addMemoryUsage(*mc);
    postTotal = mc->getTotalMemoryUsage();
    mc->addSnapShotUsage("total", postTotal - preTotal);
    return mc;
}

} // metrics
