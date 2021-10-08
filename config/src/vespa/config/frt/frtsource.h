// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "connectionfactory.h"
#include "frtconfigagent.h"
#include "frtconfigrequestfactory.h"
#include <vespa/config/common/configkey.h>
#include <vespa/config/common/configrequest.h>
#include <vespa/config/common/source.h>
#include <vespa/fnet/frt/invoker.h>

namespace config {

/**
 * Class for sending and receiving config requests via FRT.
 */
class FRTSource : public Source,
                  public FRT_IRequestWait
{
public:
    FRTSource(const ConnectionFactory::SP & connectionFactory, const FRTConfigRequestFactory & requestFactory, ConfigAgent::UP agent, const ConfigKey & key);
    ~FRTSource() override;

    void RequestDone(FRT_RPCRequest * request) override;
    void close() override;
    void reload(int64_t generation) override;
    void getConfig() override;

    const FRTConfigRequest & getCurrentRequest() const;

private:
    void scheduleNextGetConfig();

    ConnectionFactory::SP _connectionFactory;
    const FRTConfigRequestFactory & _requestFactory;
    ConfigAgent::UP _agent;
    FRTConfigRequest::UP _currentRequest;
    const ConfigKey _key;

    std::unique_ptr<FNET_Task> _task;
    std::mutex _lock; // Protects _task and _closed
    bool _closed;
};

} // namespace config

