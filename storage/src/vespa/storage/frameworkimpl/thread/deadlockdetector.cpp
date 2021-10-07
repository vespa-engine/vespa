// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "deadlockdetector.h"
#include "htmltable.h"
#include <vespa/storage/bucketdb/storbucketdb.h>
#include <vespa/storage/common/content_bucket_space_repo.h>
#include <vespa/vespalib/stllike/asciistream.h>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".deadlock.detector");

using document::BucketSpace;

namespace storage {

DeadLockDetector::DeadLockDetector(StorageComponentRegister& compReg, AppKiller::UP killer)
    : framework::HtmlStatusReporter("deadlockdetector", "Dead lock detector"),
      _killer(std::move(killer)),
      _states(),
      _lock(),
      _cond(),
      _enableWarning(true),
      _enableShutdown(false),
      _processSlack(30s),
      _waitSlack(5s),
      _reportedBucketDBLocksAtState(OK)
{
    auto* dComp(dynamic_cast<DistributorComponentRegister*>(&compReg));
    if (dComp) {
        _dComponent = std::make_unique<DistributorComponent>(*dComp, "deadlockdetector");
        _component = _dComponent.get();
    } else {
        auto* slComp(dynamic_cast<ServiceLayerComponentRegister*>(&compReg));
        assert(slComp != nullptr);
        _slComponent = std::make_unique<ServiceLayerComponent>(*slComp, "deadlockdetector");
        _component = _slComponent.get();
    }
    _component->registerStatusPage(*this);
    _thread = _component->startThread(*this);
}

DeadLockDetector::~DeadLockDetector()
{
    if (_thread) {
        _thread->interruptAndJoin(_cond);
    }
}

void
DeadLockDetector::enableWarning(bool enable)
{
    if (enable == _enableWarning) return;
    LOG(debug, "%s dead lock detection warnings",
        enable ? "Enabling" : "Disabling");
    _enableWarning = enable;
}

void
DeadLockDetector::enableShutdown(bool enable)
{
    if (enable == _enableShutdown) return;
    LOG(debug, "%s dead lock detection",
        enable ? "Enabling" : "Disabling");
    _enableShutdown = enable;
}

namespace {
    struct VisitorWrapper : public framework::ThreadVisitor {
        std::map<vespalib::string, DeadLockDetector::State>& _states;
        DeadLockDetector::ThreadVisitor& _visitor;

        VisitorWrapper(std::map<vespalib::string, DeadLockDetector::State>& s,
                       DeadLockDetector::ThreadVisitor& visitor)
            : _states(s),
              _visitor(visitor)
        {
        }

        void visitThread(const vespalib::string& id,
                         const framework::ThreadProperties& p,
                         const framework::ThreadTickData& td) override
        {
            if (_states.find(id) == _states.end()) {
                _states[id] = DeadLockDetector::OK;
            }
            _visitor.visitThread(id, p, td, _states[id]);
        }
    };
}

void
DeadLockDetector::visitThreads(ThreadVisitor& visitor) const
{
    VisitorWrapper wrapper(_states, visitor);
    _component->getThreadPool().visitThreads(wrapper);
}

bool
DeadLockDetector::isAboveFailThreshold(vespalib::steady_time time,
                                       const framework::ThreadProperties& tp,
                                       const framework::ThreadTickData& tick) const
{
    if (tp.getMaxCycleTime() == vespalib::duration::zero()) {
        return false;
    }
    vespalib::duration slack(tick._lastTickType == framework::WAIT_CYCLE
            ? getWaitSlack() : getProcessSlack());
    return (tick._lastTick + tp.getMaxCycleTime() + slack < time);
}

bool
DeadLockDetector::isAboveWarnThreshold(vespalib::steady_time time,
                                       const framework::ThreadProperties& tp,
                                       const framework::ThreadTickData& tick) const
{
    if (tp.getMaxCycleTime() == vespalib::duration::zero()) return false;
    vespalib::duration slack = tick._lastTickType == framework::WAIT_CYCLE
            ? getWaitSlack() : getProcessSlack();
    return (tick._lastTick + tp.getMaxCycleTime() + slack / 4 < time);
}

vespalib::string
DeadLockDetector::getBucketLockInfo() const
{
    vespalib::asciistream ost;
    if (_dComponent.get() != nullptr) {
        ost << "No bucket lock information available for distributor\n";
    } else {
        for (const auto &elem : _slComponent->getBucketSpaceRepo()) {
            const auto &bucketDatabase = elem.second->bucketDatabase();
            if (bucketDatabase.size() > 0) {
                bucketDatabase.showLockClients(ost);
            }
        }
    }
    return ost.str();
}

namespace {
    struct ThreadChecker : public DeadLockDetector::ThreadVisitor
    {
        DeadLockDetector& _detector;
        vespalib::steady_time _currentTime;

        ThreadChecker(DeadLockDetector& d, vespalib::steady_time time)
            : _detector(d), _currentTime(time) {}

        void visitThread(const vespalib::string& id,
                         const framework::ThreadProperties& tp,
                         const framework::ThreadTickData& tick,
                         DeadLockDetector::State& state) override
        {
                // In case we just got a new tick, ignore the thread
            if (tick._lastTick > _currentTime) return;
                // If thread is already in halted state, ignore it.
            if (state == DeadLockDetector::HALTED) return;

            if (_detector.isAboveFailThreshold(_currentTime, tp, tick)) {
                state = DeadLockDetector::HALTED;
                _detector.handleDeadlock(_currentTime, id, tp, tick, false);
            } else if (_detector.isAboveWarnThreshold(_currentTime, tp, tick)) {
                state = DeadLockDetector::WARNED;
                _detector.handleDeadlock(_currentTime, id, tp, tick, true);
            } else if (state != DeadLockDetector::OK) {
                vespalib::asciistream ost;
                ost << "Thread " << id << " has registered tick again.\n";
                LOGBP(info, "%s", ost.str().data());
                state = DeadLockDetector::OK;
            }
        }
    };
}

void
DeadLockDetector::handleDeadlock(vespalib::steady_time currentTime,
                                 const vespalib::string& id,
                                 const framework::ThreadProperties&,
                                 const framework::ThreadTickData& tick,
                                 bool warnOnly)
{
    vespalib::asciistream error;
    error << "Thread " << id << " has gone "
          << vespalib::count_ms(currentTime - tick._lastTick)
          << " milliseconds without registering a tick.";
    if (!warnOnly) {
        if (_enableShutdown && !warnOnly) {
            error << " Restarting process due to deadlock.";
        } else {
            error << " Would have restarted process due to "
                  << "deadlock if shutdown had been enabled.";
        }
    } else {
        error << " Global slack not expended yet. Warning for now.";
    }
    if (warnOnly) {
        if (_enableWarning) {
            LOGBT(warning, "deadlockw-" + id, "%s",
                  error.str().data());
            if (_reportedBucketDBLocksAtState != WARNED) {
                _reportedBucketDBLocksAtState = WARNED;
                LOG(info, "Locks in bucket database at deadlock time:\n%s",
                    getBucketLockInfo().c_str());
            }
        }
        return;
    } else {
        if (_enableShutdown || _enableWarning) {
            LOGBT(error, "deadlock-" + id, "%s",
                  error.str().data());
        }
    }
    if (!_enableShutdown) return;
    if (_reportedBucketDBLocksAtState != HALTED) {
        _reportedBucketDBLocksAtState = HALTED;
        LOG(info, "Locks in bucket database at deadlock time:"
                  "\n%s", getBucketLockInfo().c_str());
    }
    if (_enableShutdown) {
        _killer->kill();
    }
}

void
DeadLockDetector::run(framework::ThreadHandle& thread)
{
    std::unique_lock sync(_lock);
    while (!thread.interrupted()) {
        ThreadChecker checker(*this, _component->getClock().getMonotonicTime());
        visitThreads(checker);
        _cond.wait_for(sync, 1s);
        thread.registerTick(framework::WAIT_CYCLE);
    }
}

namespace {
    struct ThreadTable {
        HtmlTable _table;
        LongColumn _msSinceLastTick;
        LongColumn _maxProcTickTime;
        LongColumn _maxWaitTickTime;
        LongColumn _maxProcTickTimeSeen;
        LongColumn _maxWaitTickTimeSeen;

        ThreadTable()
            : _table("Thread name"),
              _msSinceLastTick("Milliseconds since last tick", " ms", &_table),
              _maxProcTickTime("Max milliseconds before wait tick", " ms", &_table),
              _maxWaitTickTime("Max milliseconds before wait tick", " ms", &_table),
              _maxProcTickTimeSeen("Max processing tick time observed", " ms", &_table),
              _maxWaitTickTimeSeen("Max wait tick time observed", " ms", &_table)
        {
            _maxProcTickTime._alignment = Column::LEFT;
            _maxProcTickTimeSeen._alignment = Column::LEFT;
            _maxWaitTickTimeSeen._alignment = Column::LEFT;
        }
        ~ThreadTable();
    };

    ThreadTable::~ThreadTable() = default;

    struct ThreadStatusWriter : public DeadLockDetector::ThreadVisitor {
        ThreadTable& _table;
        vespalib::steady_time _time;
        vespalib::duration _processSlack;
        vespalib::duration _waitSlack;

        ThreadStatusWriter(ThreadTable& table,
                           vespalib::steady_time time,
                           vespalib::duration processSlack,
                           vespalib::duration waitSlack)
            : _table(table), _time(time),
              _processSlack(processSlack), _waitSlack(waitSlack) {}

        template<typename T>
        vespalib::string toS(const T& val) {
            vespalib::asciistream ost;
            ost << val;
            return ost.str();
        }

        void visitThread(const vespalib::string& id,
                         const framework::ThreadProperties& tp,
                         const framework::ThreadTickData& tick,
                         DeadLockDetector::State& /*state*/) override
        {
            _table._table.addRow(id);
            uint32_t i = _table._table.getRowCount() - 1;
            _table._msSinceLastTick[i] = vespalib::count_ms(_time - tick._lastTick);
            _table._maxProcTickTime[i] = vespalib::count_ms(tp.getMaxProcessTime());
            _table._maxWaitTickTime[i] = vespalib::count_ms(tp.getWaitTime());
            _table._maxProcTickTimeSeen[i] = vespalib::count_ms(tick._maxProcessingTimeSeen);
            _table._maxWaitTickTimeSeen[i] = vespalib::count_ms(tick._maxWaitTimeSeen);
        }
    };
}

void
DeadLockDetector::reportHtmlStatus(std::ostream& os,
                                   const framework::HttpUrlPath&) const
{
    vespalib::asciistream out;
    out << "<h2>Overview of latest thread ticks</h2>\n";
    ThreadTable threads;
    std::lock_guard guard(_lock);
    ThreadStatusWriter writer(threads, _component->getClock().getMonotonicTime(),
                              getProcessSlack(), getWaitSlack());
    visitThreads(writer);
    std::ostringstream ost;
    threads._table.print(ost);
    out << ost.str();
    out << "<p>\n"
        << "Note that there is a global slack period of " << vespalib::count_ms(getProcessSlack())
        << " ms for processing ticks and " << vespalib::count_ms(getWaitSlack())
        << " ms for wait ticks. Actual shutdown or warning logs will not"
        << " appear before this slack time is expendede on top of the per"
        << " thread value.\n"
        << "</p>\n";
    if (_enableShutdown) {
        out << "<p>The deadlock detector is enabled and will kill the process "
            << "if a deadlock is detected</p>\n";
    } else {
        out << "<p>The deadlock detector is disabled and will only monitor "
            << "tick times.</p>\n";
    }
    out << "<h2>Current locks in the bucket database</h2>\n"
        << "<p>In case of a software bug causing a deadlock in the code, bucket"
        << " database locks are a likely reason. Thus, we list current locks "
        << "here in hopes that it will simplify debugging.</p>\n"
        << "<p>Bucket database</p>\n"
        << "<pre>\n"
        << getBucketLockInfo()
        << "</pre>\n";
    os << out.str();
}

} // storage
