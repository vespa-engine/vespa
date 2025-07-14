// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "monitor.h"
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/channel.h>

namespace slobrok {

//-----------------------------------------------------------------------------

Monitor::Monitor(IMonitoredServer &server, FRT_Supervisor &supervisor)
    : FNET_Task(supervisor.GetScheduler()),
      _monitoredServer(server),
      _channel(nullptr),
      _enabled(false)
{
}


Monitor::~Monitor()
{
    Kill(); // will deadlock if called from task
    disconnect();
}


void
Monitor::enable(FRT_Target *monitorTarget)
{
    assert(monitorTarget != nullptr);
    Unschedule();
    disconnect();
    _enabled = true;
    FNET_Connection *conn = monitorTarget->GetConnection();
    if (conn != nullptr) {
        _channel = conn->OpenChannel(this, FNET_Context());
    }
    if (_channel == nullptr) {
        ScheduleNow();
    } else {
        _channel->SetContext(FNET_Context(_channel));
    }
}


void
Monitor::PerformTask()
{
    if (_enabled) {
        _monitoredServer.notifyDisconnected();
    }
}


void
Monitor::disable()
{
    _enabled = false;
    disconnect();
}


void
Monitor::disconnect()
{
    if (_channel != nullptr) {
        _channel->SetContext(FNET_Context((FNET_Channel *)0));
        if (_channel->GetConnection()->GetState() <= FNET_Connection::FNET_CONNECTED) {
            _channel->CloseAndFree();
        }
        _channel = nullptr;
    }
}


FNET_IPacketHandler::HP_RetCode
Monitor::HandlePacket(FNET_Packet *packet,
                      FNET_Context context)
{
    if (context._value.CHANNEL == nullptr) {
        packet->Free();
        return FNET_FREE_CHANNEL;
    }
    if (!packet->IsChannelLostCMD()) {
        packet->Free();
        return FNET_KEEP_CHANNEL;
    }
    _channel = nullptr;
    ScheduleNow();
    return FNET_FREE_CHANNEL;
}

//-----------------------------------------------------------------------------

} // namespace slobrok
