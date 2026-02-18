// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "rpcrequest.h"

#include <vespa/fnet/ipackethandler.h>
#include <vespa/fnet/task.h>

#include <condition_variable>
#include <mutex>

class FRT_Method;
class FRT_Supervisor;
//-----------------------------------------------------------------------------

class FRT_IRequestWait {
public:
    /**
     * Destructor.  No cleanup needed for base class.
     */
    virtual ~FRT_IRequestWait() = default;

    virtual void RequestDone(FRT_RPCRequest* req) = 0;
};

//-----------------------------------------------------------------------------

class FRT_SingleReqWait : public FRT_IRequestWait {
private:
    std::mutex              _lock;
    std::condition_variable _cond;
    bool                    _done;
    bool                    _waiting;

public:
    FRT_SingleReqWait();
    virtual ~FRT_SingleReqWait();

    void WaitReq();
    void RequestDone(FRT_RPCRequest* req) override;
};

//-----------------------------------------------------------------------------

class FRT_ITimeoutHandler {
public:
    /**
     * Destructor.  No cleanup needed for base class.
     */
    virtual ~FRT_ITimeoutHandler() = default;

    virtual void HandleTimeout() = 0;
};

//-----------------------------------------------------------------------------

class FRT_RPCInvoker : public FRT_IReturnHandler {
private:
    FRT_RPCRequest* _req;
    FRT_Method*     _method;
    bool            _noReply;

    FRT_RPCInvoker(const FRT_RPCInvoker&);
    FRT_RPCInvoker& operator=(const FRT_RPCInvoker&);

public:
    FRT_RPCInvoker(FRT_Supervisor* supervisor, FRT_RPCRequest* req, bool noReply);

    void ForceMethod(FRT_Method* method) { _method = method; }

    FRT_RPCRequest* GetRequest() { return _req; }

    void             HandleDone(bool freeChannel);
    bool             Invoke();
    void             HandleReturn() override;
    FNET_Connection* GetConnection() override;
};

//-----------------------------------------------------------------------------

class FRT_HookInvoker : public FRT_IReturnHandler {
private:
    FRT_RPCRequest*  _req;
    FRT_Method*      _hook;
    FNET_Connection* _conn;

    FRT_HookInvoker(const FRT_HookInvoker&);
    FRT_HookInvoker& operator=(const FRT_HookInvoker&);

public:
    FRT_HookInvoker(FRT_RPCRequest* req, FRT_Method* hook, FNET_Connection* conn)
        : _req(req), _hook(hook), _conn(conn) {
        _req->SetReturnHandler(this);
    }

    void             Invoke();
    void             HandleReturn() override;
    FNET_Connection* GetConnection() override;
};

//-----------------------------------------------------------------------------

class FRT_RPCAdapter : public FNET_Task, public FRT_IAbortHandler, public FNET_IPacketHandler {
private:
    FRT_RPCRequest*   _req;
    FRT_IRequestWait* _waiter;
    FNET_Channel*     _channel;

    FRT_RPCAdapter(const FRT_RPCAdapter&);
    FRT_RPCAdapter& operator=(const FRT_RPCAdapter&);

public:
    FRT_RPCAdapter(FNET_Scheduler* scheduler, FRT_RPCRequest* req, FRT_IRequestWait* waiter);

    void SetChannel(FNET_Channel* channel) { _channel = channel; }

    void HandleDone();

    bool       HandleAbort() override;
    void       PerformTask() override;
    HP_RetCode HandlePacket(FNET_Packet* packet, FNET_Context context) override;
};

//-----------------------------------------------------------------------------

VESPA_CAN_SKIP_DESTRUCTION(FRT_RPCAdapter)
VESPA_CAN_SKIP_DESTRUCTION(FRT_RPCInvoker)
VESPA_CAN_SKIP_DESTRUCTION(FRT_HookInvoker)
