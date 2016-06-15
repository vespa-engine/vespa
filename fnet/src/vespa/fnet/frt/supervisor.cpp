// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/fnet/frt/frt.h>


FRT_Supervisor::FRT_Supervisor(FNET_Transport *transport,
                               FastOS_ThreadPool *threadPool)
    : _transport(transport),
      _threadPool(threadPool),
      _standAlone(false),
      _packetFactory(),
      _packetStreamer(&_packetFactory),
      _connector(NULL),
      _reflectionManager(),
      _rpcHooks(&_reflectionManager),
      _connHooks(*this),
      _methodMismatchHook(NULL)
{
    _rpcHooks.InitRPC(this);
}


FRT_Supervisor::FRT_Supervisor(uint32_t threadStackSize,
                               uint32_t maxThreads)
    : _transport(NULL),
      _threadPool(NULL),
      _standAlone(true),
      _packetFactory(),
      _packetStreamer(&_packetFactory),
      _connector(NULL),
      _reflectionManager(),
      _rpcHooks(&_reflectionManager),
      _connHooks(*this),
      _methodMismatchHook(NULL)
{
    _transport = new FNET_Transport();
    assert(_transport != NULL);
    if (threadStackSize > 0) {
        _threadPool = new FastOS_ThreadPool(threadStackSize, maxThreads);
        assert(_threadPool != NULL);
    }
    _rpcHooks.InitRPC(this);
}


FRT_Supervisor::~FRT_Supervisor()
{
    if (_standAlone) {
        delete _transport;
        delete _threadPool;
    }
    if (_connector != NULL) {
        _connector->SubRef();
    }
    delete _methodMismatchHook;
}


bool
FRT_Supervisor::Listen(const char *spec)
{
    if (_connector != NULL)
        return false;
    _connector = _transport->Listen(spec, &_packetStreamer, this);
    return (_connector != NULL);
}


bool
FRT_Supervisor::Listen(int port)
{
    char spec[32];
    sprintf(spec, "tcp/%d", port);
    return Listen(spec);
}


uint32_t
FRT_Supervisor::GetListenPort() const
{
    return (_connector != NULL) ? _connector->GetPortNumber() : 0;
}


bool
FRT_Supervisor::RunInvocation(FRT_RPCInvoker *invoker)
{
    // XXX: implement queue with max length + max # threads

    if (_threadPool == NULL ||
        _threadPool->NewThread(invoker) == NULL)
    {
        invoker->GetRequest()->SetError(FRTE_RPC_OVERLOAD,
                                        "Could not start thread");
        return false;
    }
    return true;
}


FRT_Target *
FRT_Supervisor::GetTarget(const char *spec)
{
    FNET_TransportThread *thread = _transport->select_thread(spec, strlen(spec));
    return new FRT_Target(thread->GetScheduler(),
                          thread->Connect(spec, &_packetStreamer));
}


FRT_Target *
FRT_Supervisor::Get2WayTarget(const char *spec, FNET_Context connContext)
{
    FNET_TransportThread *thread = _transport->select_thread(spec, strlen(spec));
    return new FRT_Target(thread->GetScheduler(),
                          thread->Connect(spec, &_packetStreamer,
                                  NULL, FNET_Context(),
                                  this, connContext));
}


FRT_Target *
FRT_Supervisor::GetTarget(int port)
{
    char spec[64];
    sprintf(spec, "tcp/localhost:%d", port);
    return GetTarget(spec);
}


FRT_RPCRequest *
FRT_Supervisor::AllocRPCRequest(FRT_RPCRequest *tradein)
{
    if (tradein != NULL) {
        if (tradein->Recycle()) {
            return tradein;
        }
        tradein->SubRef();
    }
    return new FRT_RPCRequest();
}


void
FRT_Supervisor::SetSessionInitHook(FRT_METHOD_PT  method,
                                   FRT_Invokable *handler)
{
    _connHooks.SetSessionInitHook(method, handler);
}


void
FRT_Supervisor::SetSessionDownHook(FRT_METHOD_PT  method,
                                   FRT_Invokable *handler)
{
    _connHooks.SetSessionDownHook(method, handler);
}


void
FRT_Supervisor::SetSessionFiniHook(FRT_METHOD_PT  method,
                                   FRT_Invokable *handler)
{
    _connHooks.SetSessionFiniHook(method, handler);
}


void
FRT_Supervisor::SetMethodMismatchHook(FRT_METHOD_PT  method,
                                      FRT_Invokable *handler)
{
    delete _methodMismatchHook;
    _methodMismatchHook = new FRT_Method("frt.hook.methodMismatch", "*", "*",
                                         true, method, handler);
    assert(_methodMismatchHook != NULL);
}


void
FRT_Supervisor::InvokeVoid(FNET_Connection *conn,
                           FRT_RPCRequest *req)
{
    if (conn != NULL) {
        FNET_Channel *ch = conn->OpenChannel();
        ch->Send(req->CreateRequestPacket(false));
        ch->Free();
    } else {
        req->SubRef();
    }
}


void
FRT_Supervisor::InvokeAsync(SchedulerPtr scheduler,
                            FNET_Connection *conn,
                            FRT_RPCRequest *req,
                            double timeout,
                            FRT_IRequestWait *waiter)
{
    uint32_t chid;
    FNET_Packet *packet = req->CreateRequestPacket(true);
    FRT_RPCAdapter *adapter = new (req->GetMemoryTub()) FRT_RPCAdapter(scheduler.ptr, req, waiter);
    FNET_Channel *ch = (conn == NULL)? NULL : conn->OpenChannel(adapter, FNET_Context((void *)req), &chid);

    adapter->SetChannel(ch);
    if (ch == NULL) {
        packet->Free();
        req->SetError(FRTE_RPC_CONNECTION);
        adapter->ScheduleNow();
        return;
    }
    if (timeout > 0.0) {
        adapter->Schedule(timeout);
    }
    conn->PostPacket(packet, chid);
}


void
FRT_Supervisor::InvokeSync(SchedulerPtr scheduler,
                           FNET_Connection *conn,
                           FRT_RPCRequest *req,
                           double timeout)
{
    FRT_SingleReqWait waiter;
    InvokeAsync(scheduler, conn, req, timeout, &waiter);
    waiter.WaitReq();
}


bool
FRT_Supervisor::InitAdminChannel(FNET_Channel *channel)
{
    return _connHooks.InitAdminChannel(channel);
}


bool
FRT_Supervisor::InitChannel(FNET_Channel *channel, uint32_t pcode)
{
    pcode &= 0xffff; // remove flags;
    bool rc = false;
    if (pcode >= PCODE_FRT_RPC_FIRST &&
        pcode <= PCODE_FRT_RPC_LAST) {
        FRT_RPCRequest *req = AllocRPCRequest();
        channel->SetHandler(this);
        channel->SetContext((void *)req);
        if (req != NULL) {
            req->SetContext(FNET_Context(channel));
            rc = true;
        }
    }
    return rc;
}


FNET_IPacketHandler::HP_RetCode
FRT_Supervisor::HandlePacket(FNET_Packet *packet, FNET_Context context)
{
    uint32_t        pcode   = packet->GetPCODE() & 0xffff; // remove flags
    FRT_RPCRequest *req     = (FRT_RPCRequest *) context._value.VOIDP;
    FRT_RPCInvoker *invoker = NULL;
    bool            noReply = false;

    if (pcode == PCODE_FRT_RPC_REQUEST) {
        noReply = ((FRT_RPCPacket *)packet)->NoReply();
    } else {
        req->SetError(FRTE_RPC_BAD_REQUEST);
    }
    invoker = new (req->GetMemoryTub()) FRT_RPCInvoker(this, req, noReply);
    packet->Free();

    if (req->IsError()) {

        if (req->GetErrorCode() != FRTE_RPC_BAD_REQUEST
            && _methodMismatchHook != NULL)
        {
            invoker->ForceMethod(_methodMismatchHook);
            return (invoker->Invoke(false)) ?
                FNET_FREE_CHANNEL : FNET_CLOSE_CHANNEL;
        }

        invoker->HandleDone(false);
        return FNET_FREE_CHANNEL;

    } else if (invoker->IsInstant()) {

        return (invoker->Invoke(false)) ?
            FNET_FREE_CHANNEL : FNET_CLOSE_CHANNEL;

    } else {

        if (!RunInvocation(invoker)) {
            invoker->HandleDone(false);
            return FNET_FREE_CHANNEL;
        }
        return FNET_CLOSE_CHANNEL;
    }
}


bool
FRT_Supervisor::Start()
{
    assert(_standAlone);
    if (_threadPool == NULL)
        return false;
    return _transport->Start(_threadPool);
}


void
FRT_Supervisor::Main()
{
    assert(_standAlone);
    _transport->Main();
}


void
FRT_Supervisor::ShutDown(bool waitFinished)
{
    assert(_standAlone);
    _transport->ShutDown(waitFinished);
}


void
FRT_Supervisor::WaitFinished()
{
    assert(_standAlone);
    _transport->WaitFinished();
}

//----------------------------------------------------
// RPC Hooks
//----------------------------------------------------

void
FRT_Supervisor::RPCHooks::InitRPC(FRT_Supervisor *supervisor)
{
    FRT_ReflectionBuilder rb(supervisor);
    //---------------------------------------------------------------------------
    rb.DefineMethod("frt.rpc.ping", "", "", true,
                    FRT_METHOD(FRT_Supervisor::RPCHooks::RPC_Ping), this);
    rb.MethodDesc("Method that may be used to check if the server is online");
    //---------------------------------------------------------------------------
    rb.DefineMethod("frt.rpc.echo", "*", "*", true,
                    FRT_METHOD(FRT_Supervisor::RPCHooks::RPC_Echo), this);
    rb.MethodDesc("Echo the parameters as return values");
    rb.ParamDesc("params", "Any set of parameters");
    rb.ReturnDesc("return", "The parameter values");
    //---------------------------------------------------------------------------
    rb.DefineMethod("frt.rpc.getMethodList", "", "SSS", true,
                    FRT_METHOD(FRT_Supervisor::RPCHooks::RPC_GetMethodList),
                    this);
    rb.MethodDesc("Obtain a list of all available methods");
    rb.ReturnDesc("names",  "Method names");
    rb.ReturnDesc("params", "Method parameter types");
    rb.ReturnDesc("return", "Method return types");
    //---------------------------------------------------------------------------
    rb.DefineMethod("frt.rpc.getMethodInfo", "s", "sssSSSS", true,
                    FRT_METHOD(FRT_Supervisor::RPCHooks::RPC_GetMethodInfo),
                    this);
    rb.MethodDesc("Obtain detailed information about a single method");
    rb.ParamDesc ("methodName",  "The method we want information about");
    rb.ReturnDesc("desc",        "Description of what the method does");
    rb.ReturnDesc("params",      "Method parameter types");
    rb.ReturnDesc("return",      "Method return types");
    rb.ReturnDesc("paramNames",  "Method parameter names");
    rb.ReturnDesc("paramDesc",   "Method parameter descriptions");
    rb.ReturnDesc("returnNames", "Method return value names");
    rb.ReturnDesc("returnDesc",  "Method return value descriptions");
    //---------------------------------------------------------------------------
}


void
FRT_Supervisor::RPCHooks::RPC_Ping(FRT_RPCRequest *req)
{
    (void) req;
}


void
FRT_Supervisor::RPCHooks::RPC_Echo(FRT_RPCRequest *req)
{
    char tmp[1024];
    FNET_DataBuffer buf(tmp, sizeof(tmp));
    buf.EnsureFree(req->GetParams()->GetLength());
    req->GetParams()->EncodeCopy(&buf);
    req->GetReturn()->DecodeCopy(&buf, buf.GetDataLen());
}


void
FRT_Supervisor::RPCHooks::RPC_GetMethodList(FRT_RPCRequest *req)
{
    _reflectionManager->DumpMethodList(req->GetReturn());
}


void
FRT_Supervisor::RPCHooks::RPC_GetMethodInfo(FRT_RPCRequest *req)
{
    FRT_Values &arg = *req->GetParams();

    FRT_Method *info = _reflectionManager->LookupMethod(arg[0]._string._str);
    if (info != NULL) {
        info->GetDocumentation(req->GetReturn());
    } else {
        req->SetError(FRTE_RPC_METHOD_FAILED, "No such method");
    }
}

//----------------------------------------------------
// Connection Hooks
//----------------------------------------------------

FRT_Supervisor::ConnHooks::ConnHooks(FRT_Supervisor &parent)
    : _parent(parent),
      _sessionInitHook(NULL),
      _sessionDownHook(NULL),
      _sessionFiniHook(NULL)
{
}


FRT_Supervisor::ConnHooks::~ConnHooks()
{
    delete _sessionInitHook;
    delete _sessionDownHook;
    delete _sessionFiniHook;
}


void
FRT_Supervisor::ConnHooks::SetSessionInitHook(FRT_METHOD_PT  method,
                                              FRT_Invokable *handler)
{
    delete _sessionInitHook;
    _sessionInitHook = new FRT_Method("frt.hook.sessionInit", "", "",
                                      true, method, handler);
    assert(_sessionInitHook != NULL);
}


void
FRT_Supervisor::ConnHooks::SetSessionDownHook(FRT_METHOD_PT  method,
                                              FRT_Invokable *handler)
{
    delete _sessionDownHook;
    _sessionDownHook = new FRT_Method("frt.hook.sessionDown", "", "",
                                      true, method, handler);
    assert(_sessionDownHook != NULL);
}


void
FRT_Supervisor::ConnHooks::SetSessionFiniHook(FRT_METHOD_PT  method,
                                              FRT_Invokable *handler)
{
    delete _sessionFiniHook;
    _sessionFiniHook = new FRT_Method("frt.hook.sessionFini", "", "",
                                      true, method, handler);
    assert(_sessionFiniHook != NULL);
}


void
FRT_Supervisor::ConnHooks::InvokeHook(FRT_Method *hook,
                                      FNET_Connection *conn)
{
    FRT_RPCRequest *req = _parent.AllocRPCRequest();
    req->SetMethodName(hook->GetName());
    (new (req->GetMemoryTub()) FRT_HookInvoker(req, hook, conn))->Invoke();
}


bool
FRT_Supervisor::ConnHooks::InitAdminChannel(FNET_Channel *channel)
{
    FNET_Connection *conn = channel->GetConnection();
    conn->SetCleanupHandler(this);
    if (_sessionInitHook != NULL) {
        InvokeHook(_sessionInitHook, conn);
    }
    channel->SetHandler(this);
    channel->SetContext(channel);
    return true;
}


FNET_IPacketHandler::HP_RetCode
FRT_Supervisor::ConnHooks::HandlePacket(FNET_Packet *packet,
                                        FNET_Context context)
{
    if (!packet->IsChannelLostCMD()) {
        packet->Free();
        return FNET_KEEP_CHANNEL;
    }
    FNET_Channel *ch = context._value.CHANNEL;
    if (_sessionDownHook != NULL) {
        InvokeHook(_sessionDownHook, ch->GetConnection());
    }
    return FNET_FREE_CHANNEL;
}


void
FRT_Supervisor::ConnHooks::Cleanup(FNET_Connection *conn)
{
    if (_sessionFiniHook != NULL) {
        InvokeHook(_sessionFiniHook, conn);
    }
}
