// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

//-----------------------------------------------------------------------------

class FRT_IRequestWait
{
public:

    /**
     * Destructor.  No cleanup needed for base class.
     */
    virtual ~FRT_IRequestWait(void) {}

    virtual void RequestDone(FRT_RPCRequest *req) = 0;
};

//-----------------------------------------------------------------------------

class FRT_SingleReqWait : public FRT_IRequestWait
{
private:
    FNET_Cond       _cond;
    bool            _done;
    bool            _waiting;

public:
    FRT_SingleReqWait()
        : _cond(),
          _done(false),
          _waiting(false) {}
    virtual ~FRT_SingleReqWait() {}

    void WaitReq()
    {
        _cond.Lock();
        _waiting = true;
        while(!_done)
            _cond.Wait();
        _waiting = false;
        _cond.Unlock();
    }

    virtual void RequestDone(FRT_RPCRequest *req);
};

//-----------------------------------------------------------------------------

class FRT_ITimeoutHandler
{
public:

    /**
     * Destructor.  No cleanup needed for base class.
     */
    virtual ~FRT_ITimeoutHandler(void) {}

    virtual void HandleTimeout() = 0;
};

//-----------------------------------------------------------------------------

class FRT_RPCInvoker : public FastOS_Runnable,
                       public FRT_IReturnHandler
{
private:
    FRT_RPCRequest *_req;
    FRT_Method     *_method;
    bool            _noReply;

    FRT_RPCInvoker(const FRT_RPCInvoker &);
    FRT_RPCInvoker &operator=(const FRT_RPCInvoker &);

public:
    FRT_RPCInvoker(FRT_Supervisor *supervisor,
                   FRT_RPCRequest *req,
                   bool noReply);

    void ForceMethod(FRT_Method *method) { _method = method; }
    bool IsInstant() { return _method->IsInstant(); }

    FRT_RPCRequest *GetRequest() { return _req; }

    void HandleDone(bool freeChannel);

    bool Invoke(bool freeChannel)
    {
        bool detached = false;
        _req->SetDetachedPT(&detached);
        (_method->GetHandler()->*_method->GetMethod())(_req);
        if (detached)
            return false;
        HandleDone(freeChannel);
        return true;
    }

    virtual void HandleReturn();
    virtual FNET_Connection *GetConnection();
    virtual void Run(FastOS_ThreadInterface *, void *);
};

//-----------------------------------------------------------------------------

class FRT_HookInvoker : public FRT_IReturnHandler
{
private:
    FRT_RPCRequest  *_req;
    FRT_Method      *_hook;
    FNET_Connection *_conn;

    FRT_HookInvoker(const FRT_HookInvoker &);
    FRT_HookInvoker &operator=(const FRT_HookInvoker &);

public:
    FRT_HookInvoker(FRT_RPCRequest *req,
                    FRT_Method *hook,
                    FNET_Connection *conn)
        : _req(req),
          _hook(hook),
          _conn(conn)
    {
        _req->SetReturnHandler(this);
    }

    void Invoke()
    {
        bool detached = false;
        _req->SetDetachedPT(&detached);
        (_hook->GetHandler()->*_hook->GetMethod())(_req);
        assert(!detached);
        _req->SubRef();
    }

    virtual void HandleReturn();
    virtual FNET_Connection *GetConnection();
};

//-----------------------------------------------------------------------------

class FRT_RPCAdapter : public FNET_Task,
                       public FRT_IAbortHandler,
                       public FNET_IPacketHandler
{
private:
    FRT_RPCRequest   *_req;
    FRT_IRequestWait *_waiter;
    FNET_Channel     *_channel;

    FRT_RPCAdapter(const FRT_RPCAdapter &);
    FRT_RPCAdapter &operator=(const FRT_RPCAdapter &);

public:
    FRT_RPCAdapter(FNET_Scheduler *scheduler,
                   FRT_RPCRequest *req,
                   FRT_IRequestWait *waiter);

    void SetChannel(FNET_Channel *channel) { _channel = channel; }

    void HandleDone();

    virtual bool HandleAbort();
    virtual void PerformTask();
    virtual HP_RetCode HandlePacket(FNET_Packet *packet, FNET_Context context);
};

//-----------------------------------------------------------------------------

