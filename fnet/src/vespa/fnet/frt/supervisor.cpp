// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "supervisor.h"

#include "invoker.h"
#include "target.h"

#include <vespa/fnet/channel.h>
#include <vespa/fnet/connector.h>
#include <vespa/fnet/transport.h>
#include <vespa/fnet/transport_thread.h>
#include <vespa/vespalib/util/require.h>

FNET_IPacketStreamer* FRT_Supervisor::get_packet_streamer() {
    static FRT_PacketFactory         packet_factory;
    static FNET_SimplePacketStreamer packet_streamer(&packet_factory);
    return &packet_streamer;
}

FRT_Supervisor::FRT_Supervisor(FNET_Transport* transport)
    : _transport(transport), _connector(nullptr), _reflectionManager(), _rpcHooks(&_reflectionManager) {
    _rpcHooks.InitRPC(this);
}

FRT_Supervisor::~FRT_Supervisor() {
    _transport->detach(this);
    if (_connector != nullptr) {
        _connector->internal_subref();
    }
}

FNET_Scheduler* FRT_Supervisor::GetScheduler() { return _transport->GetScheduler(); }

bool FRT_Supervisor::Listen(const char* spec) {
    if (_connector != nullptr)
        return false;
    _connector = _transport->Listen(spec, get_packet_streamer(), this);
    return (_connector != nullptr);
}

bool FRT_Supervisor::Listen(int port) {
    char spec[32];
    snprintf(spec, sizeof(spec), "tcp/%d", port);
    return Listen(spec);
}

uint32_t FRT_Supervisor::GetListenPort() const { return (_connector != nullptr) ? _connector->GetPortNumber() : 0; }

FRT_Target* FRT_Supervisor::GetTarget(const char* spec) {
    FNET_TransportThread* thread = _transport->select_thread(spec, strlen(spec));
    return new FRT_Target(thread->GetScheduler(), thread->Connect(spec, get_packet_streamer()));
}

FRT_Target* FRT_Supervisor::Get2WayTarget(const char* spec, FNET_Context connContext) {
    FNET_TransportThread* thread = _transport->select_thread(spec, strlen(spec));
    return new FRT_Target(thread->GetScheduler(), thread->Connect(spec, get_packet_streamer(), this, connContext));
}

FRT_Target* FRT_Supervisor::GetTarget(int port) {
    char spec[64];
    snprintf(spec, sizeof(spec), "tcp/localhost:%d", port);
    return GetTarget(spec);
}

FRT_RPCRequest* FRT_Supervisor::AllocRPCRequest(FRT_RPCRequest* tradein) {
    if (tradein != nullptr) {
        if (tradein->Recycle()) {
            return tradein;
        }
        tradein->internal_subref();
    }
    return new FRT_RPCRequest();
}

void FRT_Supervisor::InvokeVoid(FNET_Connection* conn, FRT_RPCRequest* req) {
    if (conn != nullptr) {
        FNET_Channel* ch = conn->OpenChannel();
        ch->Send(req->CreateRequestPacket(false));
        ch->Free();
    } else {
        req->internal_subref();
    }
}

void FRT_Supervisor::InvokeAsync(
    SchedulerPtr scheduler, FNET_Connection* conn, FRT_RPCRequest* req, double timeout, FRT_IRequestWait* waiter) {
    uint32_t        chid;
    FNET_Packet*    packet = req->CreateRequestPacket(true);
    FRT_RPCAdapter* adapter = &req->getStash().create<FRT_RPCAdapter>(scheduler.ptr, req, waiter);
    FNET_Channel*   ch = (conn == nullptr) ? nullptr : conn->OpenChannel(adapter, FNET_Context((void*)req), &chid);

    adapter->SetChannel(ch);
    if (ch == nullptr) {
        packet->Free();
        req->SetError(FRTE_RPC_CONNECTION);
        adapter->ScheduleNow();
        return;
    }
    constexpr double ONE_YEAR_S = 3600 * 24 * 365;
    if (timeout > 0.0 && timeout < ONE_YEAR_S) {
        adapter->Schedule(timeout);
    }
    conn->PostPacket(packet, chid);
}

void FRT_Supervisor::InvokeSync(SchedulerPtr scheduler, FNET_Connection* conn, FRT_RPCRequest* req, double timeout) {
    FRT_SingleReqWait waiter;
    InvokeAsync(scheduler, conn, req, timeout, &waiter);
    waiter.WaitReq();
}

bool FRT_Supervisor::InitChannel(FNET_Channel* channel, uint32_t pcode) {
    pcode &= 0xffff; // remove flags;
    bool rc = false;
    if (pcode >= PCODE_FRT_RPC_FIRST && pcode <= PCODE_FRT_RPC_LAST) {
        FRT_RPCRequest* req = AllocRPCRequest();
        channel->SetHandler(this);
        channel->SetContext((void*)req);
        if (req != nullptr) {
            req->SetContext(FNET_Context(channel));
            rc = true;
        }
    }
    return rc;
}

FNET_IPacketHandler::HP_RetCode FRT_Supervisor::HandlePacket(FNET_Packet* packet, FNET_Context context) {
    uint32_t        pcode = packet->GetPCODE() & 0xffff; // remove flags
    auto*           req = (FRT_RPCRequest*)context._value.VOIDP;
    FRT_RPCInvoker* invoker = nullptr;
    bool            noReply = false;

    if (pcode == PCODE_FRT_RPC_REQUEST) {
        noReply = ((FRT_RPCPacket*)packet)->NoReply();
    } else {
        req->SetError(FRTE_RPC_BAD_REQUEST);
    }
    invoker = &req->getStash().create<FRT_RPCInvoker>(this, req, noReply);
    packet->Free();

    if (req->IsError()) {

        invoker->HandleDone(false);
        return FNET_FREE_CHANNEL;

    } else {

        return (invoker->Invoke()) ? FNET_FREE_CHANNEL : FNET_CLOSE_CHANNEL;
    }
}

//----------------------------------------------------
// RPC Hooks
//----------------------------------------------------

void FRT_Supervisor::RPCHooks::InitRPC(FRT_Supervisor* supervisor) {
    FRT_ReflectionBuilder rb(supervisor);
    //---------------------------------------------------------------------------
    rb.DefineMethod("frt.rpc.ping", "", "", FRT_METHOD(FRT_Supervisor::RPCHooks::RPC_Ping), this);
    rb.MethodDesc("Method that may be used to check if the server is online");
    //---------------------------------------------------------------------------
    rb.DefineMethod("frt.rpc.echo", "*", "*", FRT_METHOD(FRT_Supervisor::RPCHooks::RPC_Echo), this);
    rb.MethodDesc("Echo the parameters as return values");
    rb.ParamDesc("params", "Any set of parameters");
    rb.ReturnDesc("return", "The parameter values");
    //---------------------------------------------------------------------------
    rb.DefineMethod("frt.rpc.getMethodList", "", "SSS", FRT_METHOD(FRT_Supervisor::RPCHooks::RPC_GetMethodList),
                    this);
    rb.MethodDesc("Obtain a list of all available methods");
    rb.ReturnDesc("names", "Method names");
    rb.ReturnDesc("params", "Method parameter types");
    rb.ReturnDesc("return", "Method return types");
    //---------------------------------------------------------------------------
    rb.DefineMethod("frt.rpc.getMethodInfo", "s", "sssSSSS", FRT_METHOD(FRT_Supervisor::RPCHooks::RPC_GetMethodInfo),
                    this);
    rb.MethodDesc("Obtain detailed information about a single method");
    rb.ParamDesc("methodName", "The method we want information about");
    rb.ReturnDesc("desc", "Description of what the method does");
    rb.ReturnDesc("params", "Method parameter types");
    rb.ReturnDesc("return", "Method return types");
    rb.ReturnDesc("paramNames", "Method parameter names");
    rb.ReturnDesc("paramDesc", "Method parameter descriptions");
    rb.ReturnDesc("returnNames", "Method return value names");
    rb.ReturnDesc("returnDesc", "Method return value descriptions");
    //---------------------------------------------------------------------------
}

void FRT_Supervisor::RPCHooks::RPC_Ping(FRT_RPCRequest* req) { (void)req; }

void FRT_Supervisor::RPCHooks::RPC_Echo(FRT_RPCRequest* req) {
    char            tmp[1024];
    FNET_DataBuffer buf(tmp, sizeof(tmp));
    buf.EnsureFree(req->GetParams()->GetLength());
    req->GetParams()->EncodeCopy(&buf);
    req->GetReturn()->DecodeCopy(&buf, buf.GetDataLen());
}

void FRT_Supervisor::RPCHooks::RPC_GetMethodList(FRT_RPCRequest* req) {
    _reflectionManager->DumpMethodList(req->GetReturn());
}

void FRT_Supervisor::RPCHooks::RPC_GetMethodInfo(FRT_RPCRequest* req) {
    FRT_Values& arg = *req->GetParams();

    FRT_Method* info = _reflectionManager->LookupMethod(arg[0]._string._str);
    if (info != nullptr) {
        info->GetDocumentation(req->GetReturn());
    } else {
        req->SetError(FRTE_RPC_METHOD_FAILED, "No such method");
    }
}

FRT_Supervisor::SchedulerPtr::SchedulerPtr(FNET_Transport* transport) : ptr(transport->GetScheduler()) {}

FRT_Supervisor::SchedulerPtr::SchedulerPtr(FNET_TransportThread* transport_thread)
    : ptr(transport_thread->GetScheduler()) {}

namespace fnet::frt {

StandaloneFRT::StandaloneFRT(const TransportConfig& config)
    : _transport(std::make_unique<FNET_Transport>(config)),
      _supervisor(std::make_unique<FRT_Supervisor>(_transport.get())) {
    REQUIRE(_transport->Start());
}

StandaloneFRT::StandaloneFRT() : StandaloneFRT(TransportConfig()) {}

StandaloneFRT::StandaloneFRT(std::shared_ptr<vespalib::CryptoEngine> crypto)
    : StandaloneFRT(TransportConfig().crypto(std::move(crypto))) {}

StandaloneFRT::~StandaloneFRT() { _transport->ShutDown(true); }

void StandaloneFRT::shutdown() { _transport->ShutDown(true); }

} // namespace fnet::frt
