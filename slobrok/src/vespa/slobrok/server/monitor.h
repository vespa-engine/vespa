// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_monitored_server.h"
#include <vespa/fnet/task.h>
#include <vespa/fnet/ipackethandler.h>

class FRT_Supervisor;
class FRT_Target;

namespace slobrok {

//-----------------------------------------------------------------------------

/**
 * @class Monitor
 * @brief Utility for monitoring an FNET connection
 *
 * Connection failure is reported via notifyDisconnected()
 * to the owner.
 **/
class Monitor : public FNET_IPacketHandler,
                public FNET_Task
{
private:
    IMonitoredServer &_monitoredServer;
    FNET_Channel     *_channel;
    bool              _enabled;
    Monitor(const Monitor&);
    Monitor &operator=(const Monitor&);
public:
    explicit Monitor(IMonitoredServer& owner,
                     FRT_Supervisor &supervisor);
    ~Monitor();
    void enable(FRT_Target *monitorTarget);
    void disable();
private:
    void disconnect();
    HP_RetCode HandlePacket(FNET_Packet *packet,
                            FNET_Context context) override;
    void PerformTask() override;
};

//-----------------------------------------------------------------------------

} // namespace slobrok

