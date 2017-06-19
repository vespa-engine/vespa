// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include "engine_base.h"
#include <vespa/searchlib/common/packets.h>
#include <vespa/fnet/ipackethandler.h>
#include <vespa/vespalib/net/lazy_resolver.h>

//----------------------------------------------------------------------

using search::fs4transport::FS4Packet_MONITORQUERYX;

class FastS_StaticMonitorQuery : public FS4Packet_MONITORQUERYX
{
    FastOS_Mutex _lock;
    int _refcnt;
public:
    virtual void Free() override;

    bool getBusy() const
    {
        return _refcnt > 1;
    }

    void markBusy()
    {
        _lock.Lock();
        _refcnt++;
        _lock.Unlock();
    }
    FastS_StaticMonitorQuery();
    ~FastS_StaticMonitorQuery();
};

//----------------------------------------------------------------------

class FastS_FNET_Engine : public FNET_IPacketHandler,
                          public FastS_EngineBase
{
private:
    FastS_FNET_Engine(const FastS_FNET_Engine &);
    FastS_FNET_Engine& operator=(const FastS_FNET_Engine &);

public:
    class WarnTask : public FNET_Task
    {
    private:
        WarnTask(const WarnTask &);
        WarnTask& operator=(const WarnTask &);

        FastS_FNET_Engine *_engine;

    public:
        enum { DELAY = 30 };
        WarnTask(FNET_Scheduler *scheduler,
                 FastS_FNET_Engine *engine)
            : FNET_Task(scheduler), _engine(engine) {}
        virtual void PerformTask() override;
    };
    friend class FastS_FNET_Engine::WarnTask;

    class ConnectTask : public FNET_Task
    {
    private:
        ConnectTask(const ConnectTask &);
        ConnectTask& operator=(const ConnectTask &);

        FastS_FNET_Engine *_engine;

    public:
        ConnectTask(FNET_Scheduler *scheduler,
                    FastS_FNET_Engine *engine)
            : FNET_Task(scheduler), _engine(engine) {}
        virtual void PerformTask() override;
    };
    friend class FastS_FNET_Engine::ConnectTask;

private:
    FastOS_Mutex      _lock;
    std::string   _hostName;
    int           _portNumber;
    std::string   _spec;
    vespalib::LazyResolver::Address::SP _lazy_address;
    FNET_Transport  *_transport;
    FNET_Connection *_conn;
    WarnTask         _warnTask;
    ConnectTask      _connectTask;
    FastS_StaticMonitorQuery *_monitorQuery;

    void Connect();
    void Disconnect();

public:
    FastS_FNET_Engine(FastS_EngineDesc *desc,
                      FastS_FNET_DataSet *dataset);
    virtual ~FastS_FNET_Engine();

    void LockDataSet()   { _dataset->LockDataset();   }
    void UnlockDataSet() { _dataset->UnlockDataset(); }

    void StartWarnTimer();
    void ScheduleConnect(double delay);
    FNET_Channel *OpenChannel_HasDSLock(FNET_IPacketHandler *handler);

    // handle FNET admin packets
    //--------------------------
    virtual HP_RetCode HandlePacket(FNET_Packet *packet, FNET_Context) override;

    // common engine API
    //------------------
    virtual void LockEngine()   override { _lock.Lock();   }
    virtual void UnlockEngine() override { _lock.Unlock(); }
    virtual void Ping() override;
    virtual void HandleClearedBad() override;
    virtual void HandleUp() override;

    // typesafe "down"-cast
    //---------------------
    virtual FastS_FNET_Engine *GetFNETEngine()    override { return this; }

    const char *getHostName() const { return _hostName.c_str(); }
    int getPortNumber() const { return _portNumber; }
};

//----------------------------------------------------------------------

