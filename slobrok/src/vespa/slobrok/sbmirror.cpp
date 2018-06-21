// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sbmirror.h"
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>

#include <vespa/log/log.h>
LOG_SETUP(".slobrok.mirror");

using vespalib::LockGuard;

namespace slobrok {
namespace api {

MirrorAPI::MirrorAPI(FRT_Supervisor &orb, const ConfiguratorFactory & config)
    : FNET_Task(orb.GetScheduler()),
      _orb(orb),
      _lock(),
      _reqPending(false),
      _scheduled(false),
      _reqDone(false),
      _useOldProto(false),
      _specs(),
      _specsGen(),
      _updates(),
      _slobrokSpecs(),
      _configurator(config.create(_slobrokSpecs)),
      _currSlobrok(""),
      _rpc_ms(100),
      _idx(0),
      _backOff(),
      _target(0),
      _req(0)
{
    _configurator->poll();
    LOG_ASSERT(_slobrokSpecs.ok());
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
MirrorAPI::lookup(const std::string & pattern) const
{
    SpecList ret;
    LockGuard guard(_lock);
    SpecList::const_iterator end = _specs.end();
    for (SpecList::const_iterator it = _specs.begin(); it != end; ++it) {
        if (match(it->first.c_str(), pattern.c_str())) {
            ret.push_back(*it);
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
    LOG_ASSERT(name != NULL);
    LOG_ASSERT(pattern != NULL);
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
MirrorAPI::updateTo(SpecList& newSpecs, uint32_t newGen)
{
    {
        LockGuard guard(_lock);
        std::swap(newSpecs, _specs);
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
    LockGuard guard(_lock);
    return _updates.getAsInt() != 0;
}


// returns true if reconnect is needed
bool
MirrorAPI::handleMirrorFetch()
{
    if (strcmp(_req->GetReturnSpec(), "SSi") != 0) {
        LOG(warning, "unknown return types '%s' from RPC request", _req->GetReturnSpec());
        return true;
    }

    FRT_Values &answer = *(_req->GetReturn());

    uint32_t numNames  = answer[0]._string_array._len;
    FRT_StringValue *n = answer[0]._string_array._pt;
    uint32_t numSpecs  = answer[1]._string_array._len;
    FRT_StringValue *s = answer[1]._string_array._pt;
    uint32_t newGen    = answer[2]._intval32;

    if (numNames != numSpecs) {
        LOG(warning, "inconsistent array lengths from RPC mirror request");
        return true;
    }

    if (_specsGen != newGen) {
        SpecList specs;

        for (uint32_t idx = 0; idx < numNames; idx++) {
            specs.push_back(std::make_pair(std::string(n[idx]._str),
                                           std::string(s[idx]._str)));
        }
        updateTo(specs, newGen);
    }
    return false;
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
        SpecList specs;
        for (uint32_t idx = 0; idx < numNames; idx++) {
            specs.push_back(
                    std::make_pair(std::string(n[idx]._str),
                                   std::string(s[idx]._str)));
        }
        updateTo(specs, diff_to);
    } else if (_specsGen == diff_from) {
        // incremental update
        SpecList specs;
        SpecList::const_iterator end = _specs.end();
        for (SpecList::const_iterator it = _specs.begin();
             it != end;
             ++it)
        {
            bool keep = true;
            for (uint32_t idx = 0; idx < numRemove; idx++) {
                if (it->first == r[idx]._str) keep = false;
            }
            for (uint32_t idx = 0; idx < numNames; idx++) {
                if (it->first == n[idx]._str) keep = false;
            }
            if (keep) specs.push_back(*it);
        }
        for (uint32_t idx = 0; idx < numNames; idx++) {
            specs.push_back(
                    std::make_pair(std::string(n[idx]._str),
                                   std::string(s[idx]._str)));
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
    if (_reqDone) {
        _reqDone = false;
        _reqPending = false;
        bool reconn = (_target == 0);

        if (_req->IsError()) {
            if (_req->GetErrorCode() == FRTE_RPC_NO_SUCH_METHOD && !_useOldProto) {
                _useOldProto = true;
                return false;
            }
            reconn = true;
        } else if (_useOldProto) {
            reconn = handleMirrorFetch();
        } else {
            reconn = handleIncrementalFetch();
        }

        if (reconn) {
            if (_target != 0) {
                _target->SubRef();
            }
            _target = 0;
        } else {
            _backOff.reset();
            // req done OK
            return true;
        }
    }
    return false;
}


void
MirrorAPI::handleReconnect()
{
    if (_target == 0) {
        _currSlobrok = _slobrokSpecs.nextSlobrokSpec();
        if (_currSlobrok.size() > 0) {
            _target = _orb.GetTarget(_currSlobrok.c_str());
        }
        _specsGen.reset();
        _useOldProto = false;
        if (_target == 0) {
            if (_rpc_ms < 50000) {
                _rpc_ms += 100;
            }
            double delay = _backOff.get();
            reSched(delay);
            if (_backOff.shouldWarn()) {
                std::string cps = _slobrokSpecs.logString();
                LOG(warning, "cannot connect to location broker at %s (retry in %f seconds)",
                    cps.c_str(), delay);
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
    if (_useOldProto) {
        _req->SetMethodName("slobrok.mirror.fetch");
    } else {
        _req->SetMethodName("slobrok.incremental.fetch");
    }
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
    LOG_ASSERT(req == _req && !_reqDone);
    (void) req;
    _reqDone = true;
    ScheduleNow();
}

} // namespace api
} // namespace slobrok
