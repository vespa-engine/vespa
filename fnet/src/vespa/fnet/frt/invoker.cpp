// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "invoker.h"
#include "supervisor.h"
#include <vespa/fnet/channel.h>

#include <vespa/log/log.h>
LOG_SETUP(".fnet.frt.invoker");

FRT_SingleReqWait::FRT_SingleReqWait()
    : _lock(),
      _cond(),
      _done(false),
      _waiting(false)
{ }

FRT_SingleReqWait::~FRT_SingleReqWait() {}

void
FRT_SingleReqWait::WaitReq()
{
    std::unique_lock<std::mutex> guard(_lock);
    _waiting = true;
    while(!_done) {
        _cond.wait(guard);
    }
    _waiting = false;
}


void
FRT_SingleReqWait::RequestDone(FRT_RPCRequest *req)
{
    (void) req;
    std::lock_guard<std::mutex> guard(_lock);
    _done = true;
    if (_waiting) {
        _cond.notify_one();
    }
}


FRT_RPCInvoker::FRT_RPCInvoker(FRT_Supervisor *supervisor,
                               FRT_RPCRequest *req,
                               bool noReply)
    : _req(req),
      _method(supervisor->GetReflectionManager()
              ->LookupMethod(req->GetMethodName())),
      _noReply(noReply)
{
    if (LOG_WOULD_LOG(debug)) {
        std::string methodName(_req->GetMethodName(), _req->GetMethodNameLen());
        LOG(debug, "invoke(server) init: '%s'", methodName.c_str());
    }
    if (_method == nullptr) {
        if (!req->IsError()) { // may be BAD_REQUEST
            req->SetError(FRTE_RPC_NO_SUCH_METHOD);
        }
    } else if (!FRT_Values::CheckTypes(_method->GetParamSpec(),
                                       req->GetParamSpec()))
    {
        req->SetError(FRTE_RPC_WRONG_PARAMS);
    }
    req->SetReturnHandler(this);
}

bool FRT_RPCInvoker::IsInstant() {
    return _method->IsInstant();
}

bool FRT_RPCInvoker::Invoke(bool freeChannel)
{
    bool detached = false;
    _req->SetDetachedPT(&detached);
    (_method->GetHandler()->*_method->GetMethod())(_req);
    if (detached)
        return false;
    HandleDone(freeChannel);
    return true;
}

void
FRT_RPCInvoker::HandleDone(bool freeChannel)
{
    FNET_Channel *ch = _req->GetContext()._value.CHANNEL;

    // check return value(s)
    if (!_req->IsError() &&
        !FRT_Values::CheckTypes(_method->GetReturnSpec(),
                                _req->GetReturnSpec()))
    {
        _req->SetError(FRTE_RPC_WRONG_RETURN);
    }
    if (LOG_WOULD_LOG(debug)) {
        std::string methodName(_req->GetMethodName(), _req->GetMethodNameLen());
        LOG(debug, "invoke(server) done: '%s': '%s'",
            methodName.c_str(), FRT_GetErrorCodeName(_req->GetErrorCode()));
    }
    // send response to client or get rid of it
    if (_noReply || (_req->GetErrorCode() == FRTE_RPC_BAD_REQUEST))
        _req->SubRef();
    else
        ch->Send(_req->CreateReplyPacket());

    // free FNET channel (if not in packet delivery callback)
    if (freeChannel)
        ch->Free();
}

void
FRT_RPCInvoker::HandleReturn()
{
    HandleDone(true);
}


FNET_Connection *
FRT_RPCInvoker::GetConnection()
{
    return _req->GetContext()._value.CHANNEL->GetConnection();
}


void
FRT_RPCInvoker::Run(FastOS_ThreadInterface *, void *)
{
    Invoke(true);
}

//-----------------------------------------------------------------------------

void FRT_HookInvoker::Invoke()
{
    bool detached = false;
    _req->SetDetachedPT(&detached);
    (_hook->GetHandler()->*_hook->GetMethod())(_req);
    assert(!detached);
    _req->SubRef();
}

void
FRT_HookInvoker::HandleReturn()
{
    // hooks cannot be detached
    LOG_ABORT("should not be reached");
}


FNET_Connection *
FRT_HookInvoker::GetConnection()
{
    return _conn;
}

//-----------------------------------------------------------------------------

FRT_RPCAdapter::FRT_RPCAdapter(FNET_Scheduler *scheduler,
                               FRT_RPCRequest *req,
                               FRT_IRequestWait *waiter)
    : FNET_Task(scheduler),
      _req(req),
      _waiter(waiter),
      _channel(nullptr)
{
    if (LOG_WOULD_LOG(debug)) {
        std::string methodName(_req->GetMethodName(), _req->GetMethodNameLen());
        LOG(debug, "invoke(client) init: '%s'", methodName.c_str());
    }
    req->SetAbortHandler(this);
}

void
FRT_RPCAdapter::HandleDone()
{
    if (LOG_WOULD_LOG(debug)) {
        std::string methodName(_req->GetMethodName(), _req->GetMethodNameLen());
        LOG(debug, "invoke(client) done: '%s': '%s'",
            methodName.c_str(), FRT_GetErrorCodeName(_req->GetErrorCode()));
    }
    // give req back to caller
    _waiter->RequestDone(_req);
}

bool
FRT_RPCAdapter::HandleAbort()
{
    if (!_req->GetCompletionToken()) { // too late
        return false;
    }
    if (_channel != nullptr) {
        _channel->CloseAndFree();
    }
    Kill();
    _req->SetError(FRTE_RPC_ABORT);
    HandleDone();
    return true;
}


void
FRT_RPCAdapter::PerformTask()
{
    if (!_req->GetCompletionToken()) { // too late
        return;
    }
    if (_channel != nullptr) {
        _channel->CloseAndFree();
    }
    if (!_req->IsError()) {
        _req->SetError(FRTE_RPC_TIMEOUT);
    }
    HandleDone();
}


FNET_IPacketHandler::HP_RetCode
FRT_RPCAdapter::HandlePacket(FNET_Packet *packet, FNET_Context)
{
    if (!_req->GetCompletionToken()) { // too late
        packet->Free();
        return FNET_KEEP_CHANNEL;
    }
    Kill();
    if (!packet->IsRegularPacket()) {
        if (packet->IsChannelLostCMD()) {
            _req->SetError(FRTE_RPC_CONNECTION);
        }
        if (packet->IsBadPacketCMD()) {
            _req->SetError(FRTE_RPC_BAD_REPLY);
        }
    }
    packet->Free();
    HandleDone();
    return FNET_FREE_CHANNEL;
}
