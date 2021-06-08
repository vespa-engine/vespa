// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "invokable.h"
#include "packets.h"
#include "reflection.h"
#include <vespa/fnet/iserveradapter.h>
#include <vespa/fnet/ipackethandler.h>
#include <vespa/fnet/connection.h>
#include <vespa/fnet/simplepacketstreamer.h>

class FNET_Transport;
class FRT_Target;
class FastOS_ThreadPool;
class FNET_Scheduler;
class FRT_RPCInvoker;
class FRT_IRequestWait;

namespace vespalib { struct CryptoEngine; }


class FRT_Supervisor : public FNET_IServerAdapter,
                       public FNET_IPacketHandler
{
public:
    class RPCHooks : public FRT_Invokable
    {
    private:
        FRT_ReflectionManager *_reflectionManager;
    public:
        RPCHooks(const RPCHooks &) = delete;
        RPCHooks &operator=(const RPCHooks &) = delete;
        explicit RPCHooks(FRT_ReflectionManager *reflect)
            : _reflectionManager(reflect) {}

        void InitRPC(FRT_Supervisor *supervisor);
        void RPC_Ping(FRT_RPCRequest *req);
        void RPC_Echo(FRT_RPCRequest *req);
        void RPC_GetMethodList(FRT_RPCRequest *req);
        void RPC_GetMethodInfo(FRT_RPCRequest *req);
    };

    class ConnHooks : public FNET_IConnectionCleanupHandler,
                      public FNET_IPacketHandler
    {
    private:
        FRT_Supervisor               &_parent;
        std::unique_ptr<FRT_Method>   _sessionInitHook;
        std::unique_ptr<FRT_Method>   _sessionDownHook;
        std::unique_ptr<FRT_Method>   _sessionFiniHook;
    public:
        ConnHooks(const ConnHooks &) = delete;
        ConnHooks &operator=(const ConnHooks &) = delete;
        explicit ConnHooks(FRT_Supervisor &parent);
        ~ConnHooks() override;

        void SetSessionInitHook(FRT_METHOD_PT  method, FRT_Invokable *handler);
        void SetSessionDownHook(FRT_METHOD_PT  method, FRT_Invokable *handler);
        void SetSessionFiniHook(FRT_METHOD_PT  method, FRT_Invokable *handler);
        void InvokeHook(FRT_Method *hook, FNET_Connection *conn);
        bool InitAdminChannel(FNET_Channel *channel);
        HP_RetCode HandlePacket(FNET_Packet *packet, FNET_Context context) override;
        void Cleanup(FNET_Connection *conn) override;
    };

private:
    FNET_Transport               *_transport;
    FRT_PacketFactory             _packetFactory;
    FNET_SimplePacketStreamer     _packetStreamer;
    FNET_Connector               *_connector;
    FRT_ReflectionManager         _reflectionManager;
    RPCHooks                      _rpcHooks;
    ConnHooks                     _connHooks;
    std::unique_ptr<FRT_Method>   _methodMismatchHook;

public:
    explicit FRT_Supervisor(FNET_Transport *transport);
    FRT_Supervisor(const FRT_Supervisor &) = delete;
    FRT_Supervisor &operator=(const FRT_Supervisor &) = delete;
    ~FRT_Supervisor() override;

    FNET_Transport *GetTransport() { return _transport; }
    FNET_Scheduler *GetScheduler();
    FRT_ReflectionManager *GetReflectionManager() { return &_reflectionManager; }

    bool Listen(const char *spec);
    bool Listen(int port);
    uint32_t GetListenPort() const;

    FRT_Target *GetTarget(const char *spec);
    FRT_Target *Get2WayTarget(const char *spec, FNET_Context connContext = FNET_Context());
    FRT_Target *GetTarget(int port);
    FRT_RPCRequest *AllocRPCRequest(FRT_RPCRequest *tradein = nullptr);

    // special hooks (implemented as RPC methods)
    void SetSessionInitHook(FRT_METHOD_PT  method, FRT_Invokable *handler);
    void SetSessionDownHook(FRT_METHOD_PT  method, FRT_Invokable *handler);
    void SetSessionFiniHook(FRT_METHOD_PT  method, FRT_Invokable *handler);
    void SetMethodMismatchHook(FRT_METHOD_PT  method, FRT_Invokable *handler);

    struct SchedulerPtr {
        FNET_Scheduler *ptr;
        SchedulerPtr(FNET_Scheduler *scheduler)
            : ptr(scheduler) {}
        SchedulerPtr(FNET_Transport *transport);
        SchedulerPtr(FNET_TransportThread *transport_thread);
    };

    // methods for performing rpc invocations
    static void InvokeVoid(FNET_Connection *conn, FRT_RPCRequest *req);
    static void InvokeSync(SchedulerPtr scheduler, FNET_Connection *conn, FRT_RPCRequest *req, double timeout);
    static void InvokeAsync(SchedulerPtr scheduler, FNET_Connection *conn, FRT_RPCRequest *req, double timeout, FRT_IRequestWait *waiter);


    // FNET ServerAdapter Interface
    bool InitAdminChannel(FNET_Channel *channel) override;
    bool InitChannel(FNET_Channel *channel, uint32_t pcode) override;

    // Packet Handling
    HP_RetCode HandlePacket(FNET_Packet *packet, FNET_Context context) override;
};

namespace fnet::frt {

/**
 * This is a simple class that makes it easy to test RPC.
 * Normally you do not want use it in production code as it hides your possibilites and responsibilities.
 */
class StandaloneFRT {
public:
    StandaloneFRT();
    explicit StandaloneFRT(std::shared_ptr<vespalib::CryptoEngine> crypto);
    ~StandaloneFRT();
    FRT_Supervisor & supervisor() { return *_supervisor; }
    void shutdown();
private:
    std::unique_ptr<FastOS_ThreadPool> _threadPool;
    std::unique_ptr<FNET_Transport> _transport;
    std::unique_ptr<FRT_Supervisor> _supervisor;
};

}
