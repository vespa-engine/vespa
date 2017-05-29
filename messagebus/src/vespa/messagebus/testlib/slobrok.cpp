// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "slobrok.h"
#include <vespa/slobrok/server/sbenv.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/transport.h>

#include <vespa/log/log.h>
LOG_SETUP(".slobrok");

namespace {
class WaitTask : public FNET_Task
{
private:
    bool              _done;
    vespalib::Monitor _mon;
public:
    WaitTask(FNET_Scheduler *s) : FNET_Task(s), _done(false), _mon() {}
    void wait() {
        vespalib::MonitorGuard guard(_mon);
        while (!_done) {
            guard.wait();
        }
    }

    void PerformTask() override {
        vespalib::MonitorGuard guard(_mon);
        _done = true;
        guard.signal();
    }
};
} // namespace <unnamed>

namespace mbus {

void
Slobrok::Thread::setEnv(slobrok::SBEnv *env)
{
    _env = env;
}

void
Slobrok::Thread::Run(FastOS_ThreadInterface *, void *)
{
    if (_env->MainLoop() != 0) {
        LOG_ABORT("Slobrok main failed");
    }
}

void
Slobrok::init()
{
    slobrok::ConfigShim shim(_port);
    _env.reset(new slobrok::SBEnv(shim));
    _thread.setEnv(_env.get());
    WaitTask wt(_env->getTransport()->GetScheduler());
    wt.ScheduleNow();
    if (_pool.NewThread(&_thread, 0) == 0) {
        LOG_ABORT("Could not spawn thread");
    }
    wt.wait();
    int p = _env->getSupervisor()->GetListenPort();
    LOG_ASSERT(p != 0 && (p == _port || _port == 0));
    _port = p;
}

Slobrok::Slobrok()
    : _pool(128000, 0),
      _env(),
      _port(0),
      _thread()
{
    init();
}

Slobrok::Slobrok(int p)
    : _pool(128000, 0),
      _env(),
      _port(p),
      _thread()
{
    init();
}

Slobrok::~Slobrok()
{
    _env->getTransport()->ShutDown(true);
    _pool.Close();
}

int
Slobrok::port() const
{
    return _port;
}

config::ConfigUri
Slobrok::config() const
{
    cloud::config::SlobroksConfigBuilder builder;
    cloud::config::SlobroksConfig::Slobrok sb;
    sb.connectionspec = vespalib::make_string("tcp/localhost:%d", port());
    builder.slobrok.push_back(sb);
    return config::ConfigUri::createFromInstance(builder);
}

} // namespace mbus
