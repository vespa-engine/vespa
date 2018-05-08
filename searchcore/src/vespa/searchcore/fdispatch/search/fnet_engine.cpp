// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fnet_dataset.h"
#include "datasetcollection.h"
#include "fnet_engine.h"
#include <vespa/searchcore/fdispatch/common/search.h>
#include <vespa/fnet/transport.h>
#include <vespa/fnet/connection.h>

using namespace search::fs4transport;

//----------------------------------------------------------------------

void
FastS_StaticMonitorQuery::Free()
{
    if (_refcnt-- == 1) {
        delete this;
    }
}


FastS_StaticMonitorQuery::FastS_StaticMonitorQuery()
    : FS4Packet_MONITORQUERYX(),
      _refcnt(1)
{ }


FastS_StaticMonitorQuery::~FastS_StaticMonitorQuery()
{
    FastS_assert(_refcnt == 0);
}

//----------------------------------------------------------------------

void
FastS_FNET_Engine::WarnTask::PerformTask()
{
    _engine->HandleNotOnline(DELAY);
}

//----------------------------------------------------------------------

void
FastS_FNET_Engine::ConnectTask::PerformTask()
{
    _engine->Connect();
}

//----------------------------------------------------------------------

void
FastS_FNET_Engine::Connect()
{
    if (_conn == nullptr ||
        _conn->GetState() >= FNET_Connection::FNET_CLOSING)
    {
        FNET_Connection *newConn =
            _transport->Connect(_spec.c_str(),
                                &FS4PersistentPacketStreamer::Instance,
                                this);
        FNET_Connection *oldConn;
        {
            auto dsGuard(getDsGuard());
            oldConn = _conn;
            _conn = newConn;
        }
        if (oldConn != nullptr)
            oldConn->SubRef();
        if (newConn == nullptr && !IsRealBad())
            ScheduleConnect(2.9);
    }
}


void
FastS_FNET_Engine::Disconnect()
{
    if (_conn != nullptr) {
        _conn->CloseAdminChannel();
        FNET_Connection *conn;
        {
            auto dsGuard(getDsGuard());
            conn = _conn;
            _conn = nullptr;
        }
        _transport->Close(conn, /* needref = */ false);
    }
}


FastS_FNET_Engine::FastS_FNET_Engine(FastS_EngineDesc *desc,
                                     FastS_FNET_DataSet *dataset)
    : FastS_EngineBase(desc, dataset),
      _spec(),
      _transport(dataset->GetTransport()),
      _conn(nullptr),
      _warnTask(dataset->GetAppContext()->GetFNETScheduler(), this),
      _connectTask(dataset->GetAppContext()->GetFNETScheduler(), this),
      _monitorQuery(nullptr)
{
    if (strncmp(_config._name, "tcp/", 4) == 0) {
        _spec = _config._name;
    } else {
        _spec = "tcp/";
        _spec += _config._name;
    }
}


FastS_FNET_Engine::~FastS_FNET_Engine()
{
    _warnTask.Kill();
    _connectTask.Kill();
    Disconnect();
    if (IsUp()) {
        auto dsGuard(getDsGuard());
        _dataset->LinkOutPart_HasLock(this);
    }
    if (_monitorQuery != nullptr) {
        _monitorQuery->Free();
        _monitorQuery = nullptr;
    }
}


void
FastS_FNET_Engine::StartWarnTimer()
{
    _warnTask.Schedule(_warnTask.DELAY);
}


void
FastS_FNET_Engine::ScheduleConnect(double delay)
{
    if (delay == 0.0) {
        _connectTask.ScheduleNow();
    } else {
        _connectTask.Schedule(delay);
    }
}


FNET_Channel *
FastS_FNET_Engine::OpenChannel_HasDSLock(FNET_IPacketHandler *handler)
{
    return (_conn != nullptr) ?  _conn->OpenChannel(handler, FNET_Context()) : nullptr;
}


FNET_IPacketHandler::HP_RetCode
FastS_FNET_Engine::HandlePacket(FNET_Packet *packet, FNET_Context)
{
    HP_RetCode ret = FNET_KEEP_CHANNEL;
    uint32_t pcode = packet->GetPCODE();

    if (packet->IsChannelLostCMD()) {

        HandleLostConnection();
        ret = FNET_FREE_CHANNEL;
        if (!IsRealBad()) {
            ScheduleConnect(2.9);
        }

    } else if (pcode == search::fs4transport::PCODE_MONITORRESULTX) {

        FS4Packet_MONITORRESULTX *mr = (FS4Packet_MONITORRESULTX *) packet;

        PossCount activeDocs;
        activeDocs.valid = ((mr->_features & search::fs4transport::MRF_ACTIVEDOCS) != 0);
        activeDocs.count = mr->_activeDocs;
        if ((mr->_features & search::fs4transport::MRF_MLD) != 0) {
            HandlePingResponse(mr->_partid, mr->_timestamp, true,
                               mr->_totalNodes, mr->_activeNodes,
                               mr->_totalParts, mr->_activeParts,
                               activeDocs);
        } else {
            HandlePingResponse(mr->_partid, mr->_timestamp, false, 1, 1, 1, 1, activeDocs);
        }
    }

    packet->Free();
    return ret;
}


void
FastS_FNET_Engine::Ping()
{
    FastS_EngineBase::Ping();

    // handle badness
    if (IsRealBad()) {
        if (_conn != nullptr) {
            Disconnect();
            HandleLostConnection();
        }
        return;
    }

    // handle ping
    if ((_conn != nullptr) && (_conn->GetState() < FNET_Connection::FNET_CLOSING)) {
        if (_monitorQuery == nullptr) {
            _monitorQuery = new FastS_StaticMonitorQuery();
        }
        if (_monitorQuery->getBusy()) {
            return;
        }
        _monitorQuery->markBusy();
        uint32_t features = search::fs4transport::MQF_QFLAGS;
        uint32_t qflags = search::fs4transport::MQFLAG_REPORT_ACTIVEDOCS;
        _monitorQuery->_features |= features;
        _monitorQuery->_qflags = qflags;
        _conn->PostPacket(_monitorQuery, FastS_NoID32());
    }
}


void
FastS_FNET_Engine::HandleClearedBad()
{
    ScheduleConnect(0.0);
}


void
FastS_FNET_Engine::HandleUp()
{
    _warnTask.Unschedule();
}
