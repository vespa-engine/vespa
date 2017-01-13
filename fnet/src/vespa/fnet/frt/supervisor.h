// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

class FRT_Supervisor : public FNET_IServerAdapter,
                       public FNET_IPacketHandler
{
public:
    class RPCHooks : public FRT_Invokable
    {
    private:
        FRT_ReflectionManager *_reflectionManager;

        RPCHooks(const RPCHooks &);
        RPCHooks &operator=(const RPCHooks &);

    public:
        RPCHooks(FRT_ReflectionManager *reflect)
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
        FRT_Supervisor &_parent;
        FRT_Method     *_sessionInitHook;
        FRT_Method     *_sessionDownHook;
        FRT_Method     *_sessionFiniHook;

        ConnHooks(const ConnHooks &);
        ConnHooks &operator=(const ConnHooks &);

    public:
        ConnHooks(FRT_Supervisor &parent);
        virtual ~ConnHooks();

        void SetSessionInitHook(FRT_METHOD_PT  method, FRT_Invokable *handler);
        void SetSessionDownHook(FRT_METHOD_PT  method, FRT_Invokable *handler);
        void SetSessionFiniHook(FRT_METHOD_PT  method, FRT_Invokable *handler);
        void InvokeHook(FRT_Method *hook, FNET_Connection *conn);
        bool InitAdminChannel(FNET_Channel *channel);
        HP_RetCode HandlePacket(FNET_Packet *packet, FNET_Context context);
        void Cleanup(FNET_Connection *conn);
    };

private:
    FNET_Transport            *_transport;
    FastOS_ThreadPool         *_threadPool;
    bool                       _standAlone;

    FRT_PacketFactory          _packetFactory;
    FNET_SimplePacketStreamer  _packetStreamer;
    FNET_Connector            *_connector;
    FRT_ReflectionManager      _reflectionManager;
    RPCHooks                   _rpcHooks;
    ConnHooks                  _connHooks;
    FRT_Method                *_methodMismatchHook;

    FRT_Supervisor(const FRT_Supervisor &);
    FRT_Supervisor &operator=(const FRT_Supervisor &);

public:
    FRT_Supervisor(FNET_Transport *transport,
                   FastOS_ThreadPool *threadPool);
    FRT_Supervisor(uint32_t threadStackSize = 65000,
                   uint32_t maxThreads = 0);
    virtual ~FRT_Supervisor();

    bool StandAlone() { return _standAlone; }
    FNET_Transport *GetTransport() { return _transport; }
    FNET_Scheduler *GetScheduler();
    FastOS_ThreadPool *GetThreadPool() { return _threadPool; }
    FRT_ReflectionManager *GetReflectionManager() { return &_reflectionManager; }

    bool Listen(const char *spec);
    bool Listen(int port);
    uint32_t GetListenPort() const;

    bool RunInvocation(FRT_RPCInvoker *invoker);

    FRT_Target *GetTarget(const char *spec);
    FRT_Target *Get2WayTarget(const char *spec,
                              FNET_Context connContext = FNET_Context());
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
    static void InvokeVoid(FNET_Connection *conn,
                           FRT_RPCRequest *req);
    static void InvokeAsync(SchedulerPtr scheduler,
                            FNET_Connection *conn,
                            FRT_RPCRequest *req,
                            double timeout,
                            FRT_IRequestWait *waiter);
    static void InvokeSync(SchedulerPtr scheduler,
                           FNET_Connection *conn,
                           FRT_RPCRequest *req,
                           double timeout);

    // FNET ServerAdapter Interface
    bool InitAdminChannel(FNET_Channel *channel);
    bool InitChannel(FNET_Channel *channel, uint32_t pcode);

    // Packet Handling
    HP_RetCode HandlePacket(FNET_Packet *packet, FNET_Context context);

    // Methods for controlling transport object in standalone mode
    bool Start();
    void Main();
    void ShutDown(bool waitFinished);
    void WaitFinished();
};

