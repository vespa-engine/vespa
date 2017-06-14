// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "mirror.h"
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>

#include <vespa/log/log.h>
LOG_SETUP(".slobrok.mirror");

namespace slobrok::api {


MirrorOld::MirrorOld(FRT_Supervisor &orb, const std::vector<std::string> &slobroks)
    : FNET_Task(orb.GetScheduler()),
      _orb(orb),
      _lock(),
      _reqDone(false),
      _specs(),
      _specsGen(),
      _updates(),
      _slobrokspecs(),
      _idx(0),
      _backOff(),
      _target(0),
      _req(0)
{
    _slobrokspecs = slobroks;
    for (uint32_t i = 0; i < slobroks.size(); ++i) { // randomize order
        uint32_t x = random() % slobroks.size();
        if (x != i) {
            std::swap(_slobrokspecs[i], _slobrokspecs[x]);
        }
    }
    if (_slobrokspecs.size() <= 0) {
        LOG(error, "no service location brokers!");
    }
    ScheduleNow();
}


MirrorOld::~MirrorOld()
{
    Kill();
    if (_req != 0) {
        _req->Abort();
        _req->SubRef();
    }
    if (_target != 0) {
        _target->SubRef();
    }
}


MirrorOld::SpecList
MirrorOld::lookup(const std::string & pattern) const
{
    SpecList ret;
    _lock.Lock();
    SpecList::const_iterator end = _specs.end();
    for (SpecList::const_iterator it = _specs.begin(); it != end; ++it) {
        if (match(it->first.c_str(), pattern.c_str())) {
            ret.push_back(*it);
        }
    }
    _lock.Unlock();
    return ret;    
}


bool
IMirrorOld::match(const char *name, const char *pattern)
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
        } else {
            return false;
        }
    }
    return (*name == *pattern);
}


void
MirrorOld::PerformTask()
{
    if (_reqDone) {
        _reqDone = false;
        if (_req->IsError()
            || strcmp(_req->GetReturnSpec(), "SSi") != 0
            || (_req->GetReturn()->GetValue(0)._string_array._len !=
                _req->GetReturn()->GetValue(1)._string_array._len))
        {
            if (_target != 0) {
                _target->SubRef();
            }
            _target = 0;
            ScheduleNow(); // try next slobrok
            return;
        }

        FRT_Values &answer = *(_req->GetReturn());

        if (_specsGen != answer[2]._intval32) {
            SpecList specs;
            uint32_t numNames  = answer[0]._string_array._len;
            FRT_StringValue *n = answer[0]._string_array._pt;
            FRT_StringValue *s = answer[1]._string_array._pt;

            for (uint32_t idx = 0; idx < numNames; idx++) {
                specs.push_back(std::make_pair(std::string(n[idx]._str),
                                               std::string(s[idx]._str)));
            }

            _lock.Lock();
            std::swap(specs, _specs);
            _updates.add();
            _lock.Unlock();
            _specsGen.setFromInt(answer[2]._intval32);
        }
        _backOff.reset();
        Schedule(0.1); // be nice
        return;
    }
    if (_target == 0) {
        if (_idx >= _slobrokspecs.size()) {
            _idx = 0;
            double delay = _backOff.get();
            Schedule(delay);
            if (_slobrokspecs.size() < 1) {
                // we already logged an error for this
                return;
            }
            if (_backOff.shouldWarn()) {
                std::string cps = _slobrokspecs[0];
                for (size_t ss = 1; ss < _slobrokspecs.size(); ++ss) {
                    cps += " or at ";
                    cps += _slobrokspecs[ss];
                }
                LOG(warning, "cannot connect to location broker at %s "
                    "(retry in %f seconds)", cps.c_str(), delay);
            }
            return;
        }
        _target = _orb.GetTarget(_slobrokspecs[_idx++].c_str());
        LOG_ASSERT(_target != 0); // just in case (tm)
        _specsGen.reset();
    }
    _req = _orb.AllocRPCRequest(_req);
    _req->SetMethodName("slobrok.mirror.fetch");
    _req->GetParams()->AddInt32(_specsGen.getAsInt());    // gencnt
    _req->GetParams()->AddInt32(5000); // mstimeout
    _target->InvokeAsync(_req, 40.0, this);
}


void
MirrorOld::RequestDone(FRT_RPCRequest *req)
{
    LOG_ASSERT(req == _req && !_reqDone);
    (void) req;
    _reqDone = true;
    ScheduleNow();
}

} // namespace slobrok::api
