// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "frtconfigrequest.h"
#include "frtconfigresponse.h"
#include "frtsource.h"
#include "frtconfigagent.h"
#include "connectionfactory.h"
#include "connection.h"
#include "frtconfigrequestfactory.h"
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".config.frt.frtsource");

namespace config {

class GetConfigTask : public FNET_Task {
public:
    GetConfigTask(FNET_Scheduler * scheduler, FRTSource * source)
        : FNET_Task(scheduler),
          _source(source)
    {
    }
    ~GetConfigTask() {
        Kill();
    }
    void PerformTask() override {
        _source->getConfig();
    }
private:
    FRTSource * _source;
};

FRTSource::FRTSource(std::shared_ptr<ConnectionFactory> connectionFactory, const FRTConfigRequestFactory & requestFactory,
                     std::unique_ptr<ConfigAgent> agent, const ConfigKey & key)
    : _connectionFactory(std::move(connectionFactory)),
      _requestFactory(requestFactory),
      _agent(std::move(agent)),
      _currentRequest(),
      _key(key),
      _lock(),
      _task(std::make_unique<GetConfigTask>(_connectionFactory->getScheduler(), this)),
      _closed(false)
{
    LOG(spam, "New source!");
}

FRTSource::~FRTSource()
{
    LOG(spam, "Destructing source");
    close();
}

void
FRTSource::getConfig()
{
    vespalib::duration serverTimeout = _agent->getTimeout();
    vespalib::duration clientTimeout = serverTimeout + 5s; // The additional 5 seconds is the time allowed for the server to respond after serverTimeout has elapsed.
    Connection * connection = _connectionFactory->getCurrent();
    if (connection == nullptr) {
        LOG(warning, "No connection available - bad config ?");
        return;
    }
    const ConfigState & state(_agent->getConfigState());
 //   LOG(debug, "invoking request with md5 %s, gen %" PRId64 ", servertimeout(%" PRId64 "), client(%f)", state.md5.c_str(), state.generation, serverTimeout, clientTimeout);


    std::unique_ptr<FRTConfigRequest> request = _requestFactory.createConfigRequest(_key, connection, state, serverTimeout);
    FRT_RPCRequest * req = request->getRequest();

    _currentRequest = std::move(request);
    connection->invoke(req, clientTimeout, this);
}


void
FRTSource::RequestDone(FRT_RPCRequest * request)
{
    if (request->GetErrorCode() == FRTE_RPC_ABORT) {
        LOG(debug, "request aborted, stopping");
        return;
    }
    assert(_currentRequest);
    // If this was error from FRT side and nothing to do with config, notify
    // connection about the error.
    if (request->IsError()) {
        _currentRequest->setError(request->GetErrorCode());
    }
    _agent->handleResponse(*_currentRequest, _currentRequest->createResponse(request));
    LOG(spam, "Calling schedule");
    scheduleNextGetConfig();
}

void
FRTSource::close()
{
    {
        std::lock_guard guard(_lock);
        if (_closed)
            return;
        LOG(spam, "Killing task");
        _task->Kill();
    }
    LOG(spam, "Aborting");
    if (_currentRequest.get() != NULL)
        _currentRequest->abort();
    LOG(spam, "Syncing");
    _connectionFactory->syncTransport();
    _currentRequest.reset(0);
    LOG(spam, "closed");
}

void
FRTSource::scheduleNextGetConfig()
{
    std::lock_guard guard(_lock);
    if (_closed)
        return;
    double sec = vespalib::to_s(_agent->getWaitTime());
    LOG(debug, "Scheduling task in %f seconds", sec);
    _task->Schedule(sec);
    LOG(debug, "Done scheduling task");
}

void
FRTSource::reload(int64_t generation)
{
    (void) generation;
}

} // namespace config
