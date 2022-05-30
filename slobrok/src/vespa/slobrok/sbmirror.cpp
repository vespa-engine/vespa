// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sbmirror.h"
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".slobrok.mirror");

namespace slobrok::api {

MirrorAPI::MirrorAPI(FRT_Supervisor &orb, const ConfiguratorFactory & config)
    : FNET_Task(orb.GetScheduler()),
      _orb(orb),
      _lock(),
      _reqPending(false),
      _scheduled(false),
      _reqDone(false),
      _logOnSuccess(true),
      _specs(),
      _specsGen(),
      _updates(),
      _slobrokSpecs(),
      _configurator(config.create(_slobrokSpecs)),
      _currSlobrok(""),
      _rpc_ms(100),
      _backOff(),
      _target(0),
      _req(0)
{
    _configurator->poll();
    if (!_slobrokSpecs.ok()) {
        throw vespalib::IllegalStateException("Not able to initialize MirrorAPI due to missing or bad slobrok specs");
    }
    ScheduleNow();
}


MirrorAPI::~MirrorAPI()
{
    Kill();
    _configurator.reset(0);
    if (_req != 0) {
        _req->Abort();
        _req->SubRef();
    }
    if (_target != 0) {
        _target->SubRef();
    }
}


MirrorAPI::SpecList
MirrorAPI::lookup(vespalib::stringref pattern) const
{
    SpecList ret;
    ret.reserve(1);
    bool exact = pattern.find('*') == std::string::npos;
    std::lock_guard guard(_lock);
    if (exact) {
        auto found = _specs.find(pattern);
        if (found != _specs.end()) {
            ret.emplace_back(found->first, found->second);
        }
    } else {
        for (const auto & spec : _specs) {
            if (match(spec.first.c_str(), pattern.data())) {
                ret.emplace_back(spec.first, spec.second);
            }
        }
    }
    return ret;
}


/*
 * Match a single name against a pattern.
 * A pattern can contain '*' to match until the next '/' separator,
 * and end with '**' to match the rest of the name.
 * Note that this isn't quite globbing, as there is no backtracking.
 * Corresponds to code in: jrt/src/com/yahoo/jrt/slobrok/api/Mirror.java
 */
bool
IMirrorAPI::match(const char *name, const char *pattern)
{
    while (*pattern != '\0') {
        if (*name == *pattern) {
            ++name;
            ++pattern;
        } else if (*pattern == '*') {
            ++pattern;
            while (*name != '/' && *name != '\0') {
                ++name;
            }
            if (*pattern == '*') {
                while (*name != '\0') {
                    ++name;
                }
            }
        } else {
            return false;
        }
    }
    return (*name == *pattern);
}


void
MirrorAPI::updateTo(SpecMap newSpecs, uint32_t newGen)
{
    {
        std::lock_guard guard(_lock);
         _specs = std::move(newSpecs);
        _updates.add();
    }
    _specsGen.setFromInt(newGen);
    if (_rpc_ms < 15000) {
        _rpc_ms = 15000;
    }
}

bool
MirrorAPI::ready() const
{
    std::lock_guard guard(_lock);
    return _updates.getAsInt() != 0;
}

// returns true if reconnect is needed
bool
MirrorAPI::handleIncrementalFetch()
{
    if (strcmp(_req->GetReturnSpec(), "iSSSi") != 0) {
        LOG(warning, "unknown return types '%s' from RPC request", _req->GetReturnSpec());
        return true;
    }
    FRT_Values &answer = *(_req->GetReturn());

    uint32_t diff_from = answer[0]._intval32;
    uint32_t numRemove = answer[1]._string_array._len;
    FRT_StringValue *r = answer[1]._string_array._pt;
    uint32_t numNames  = answer[2]._string_array._len;
    FRT_StringValue *n = answer[2]._string_array._pt;
    uint32_t numSpecs  = answer[3]._string_array._len;
    FRT_StringValue *s = answer[3]._string_array._pt;
    uint32_t diff_to   = answer[4]._intval32;

    if (diff_from != 0 && diff_from != _specsGen.getAsInt()) {
        LOG(warning, "bad old specs gen %u from RPC incremental request for [0/%u]",
            diff_from, _specsGen.getAsInt());
        return true;
    }

    if (numNames != numSpecs) {
        LOG(warning, "inconsistent array lengths from RPC mirror request");
        return true;
    }

    LOG(spam, "got incremental diff from %d to %d (had %d)",
        diff_from, diff_to, _specsGen.getAsInt());

    if (_specsGen == diff_from && _specsGen == diff_to) {
        // nop
        if (numRemove != 0 || numNames != 0) {
            LOG(spam, "incremental diff [%u;%u] nop, but numRemove=%u, numNames=%u",
                diff_from, diff_to, numRemove, numNames);
        }
    } else if (diff_from == 0) {
        // full dump
        if (numRemove != 0) {
            LOG(spam, "incremental diff [%u;%u] full dump, but numRemove=%u, numNames=%u",
                diff_from, diff_to, numRemove, numNames);
        }
        SpecMap specs;
        for (uint32_t idx = 0; idx < numNames; idx++) {
            specs[n[idx]._str] = s[idx]._str;
        }
        updateTo(std::move(specs), diff_to);
    } else if (_specsGen == diff_from) {
        // incremental update
        SpecMap specs;
        for (const auto & spec : _specs) {
            bool keep = true;
            for (uint32_t idx = 0; idx < numRemove; idx++) {
                if (spec.first == r[idx]._str) keep = false;
            }
            for (uint32_t idx = 0; idx < numNames; idx++) {
                if (spec.first == n[idx]._str) keep = false;
            }
            if (keep) specs[spec.first] = spec.second;
        }
        for (uint32_t idx = 0; idx < numNames; idx++) {
            specs[n[idx]._str] = s[idx]._str;
        }
        updateTo(specs, diff_to);
    }
    return false;
}


void
MirrorAPI::handleReconfig()
{
    if (_configurator->poll() && _target != 0) {
        if (! _slobrokSpecs.contains(_currSlobrok)) {
            std::string cps = _slobrokSpecs.logString();
            LOG(warning, "current server %s not in list of location brokers: %s",
                _currSlobrok.c_str(), cps.c_str());
            _target->SubRef();
            _target = 0;
        }
    }
}

bool
MirrorAPI::handleReqDone()
{
    if (_reqDone.load(std::memory_order_relaxed)) {
        _reqDone.store(false, std::memory_order_relaxed);
        _reqPending = false;
        bool reconn = _req->IsError() ? true : handleIncrementalFetch();

        if (reconn) {
            if (_target != 0) {
                _target->SubRef();
            }
            _target = 0;
        } else {
            _backOff.reset();
            // req done OK
            if (_logOnSuccess) {
                LOG(info, "successfully connected to location broker %s (mirror initialized with %zu service names)",
                    _currSlobrok.c_str(), _specs.size());
                _logOnSuccess = false;
            }
            return true;
        }
    }
    return false;
}


void
MirrorAPI::handleReconnect()
{
    if (_target == 0) {
        _logOnSuccess = true;
        _currSlobrok = _slobrokSpecs.nextSlobrokSpec();
        if (_currSlobrok.size() > 0) {
            _target = _orb.GetTarget(_currSlobrok.c_str());
        }
        _specsGen.reset();
        if (_target == 0) {
            if (_rpc_ms < 50000) {
                _rpc_ms += 100;
            }
            double delay = _backOff.get();
            reSched(delay);
            std::string cps = _slobrokSpecs.logString();
            const char * const msgfmt = "no location brokers available, retrying: %s (in %.1f seconds)";
            if (_backOff.shouldWarn()) {
                LOG(warning, msgfmt, cps.c_str(), delay);
            } else {
                LOG(debug, msgfmt, cps.c_str(), delay);
            }
        }
    }
}


void
MirrorAPI::makeRequest()
{
    if (_target == 0) return;
    if (_reqPending) {
        LOG(error, "cannot make new request, one is pending already");
        LOG_ABORT("should not be reached");
    }
    if (_scheduled) {
        LOG(error, "cannot make new request, re-schedule is pending");
        LOG_ABORT("should not be reached");
    }

    _req = _orb.AllocRPCRequest(_req);
    _req->SetMethodName("slobrok.incremental.fetch");
    _req->GetParams()->AddInt32(_specsGen.getAsInt()); // gencnt
    _req->GetParams()->AddInt32(5000);                 // mstimeout
    _target->InvokeAsync(_req, 0.001 * _rpc_ms, this);
    _reqPending = true;
}

void
MirrorAPI::reSched(double seconds)
{
    if (_scheduled) {
        LOG(error, "already scheduled when asked to re-schedule in %f seconds", seconds);
        LOG_ABORT("should not be reached");
    }
    Schedule(seconds);
    _scheduled = true;
}

void
MirrorAPI::PerformTask()
{
    _scheduled = false;
    handleReconfig();
    if (handleReqDone()) {
        reSched(0.1); // be nice, do not make request again immediately
        return;
    }
    handleReconnect();
    if (! _scheduled) {
        makeRequest();
    }
}


void
MirrorAPI::RequestDone(FRT_RPCRequest *req)
{
    LOG_ASSERT(req == _req && !_reqDone.load(std::memory_order_relaxed));
    (void) req;
    _reqDone.store(true, std::memory_order_relaxed);
    ScheduleNow();
}

}
