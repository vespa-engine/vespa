// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/common/configkey.h>
#include <vespa/config/common/source.h>
#include <vespa/fnet/frt/invoker.h>

namespace config {

class FRTConfigRequestFactory;
class ConnectionFactory;
class ConfigAgent;
class FRTConfigRequest;

/**
 * Class for sending and receiving config requests via FRT.
 */
class FRTSource : public Source,
                  public FRT_IRequestWait
{
public:
    FRTSource(std::shared_ptr<ConnectionFactory> connectionFactory, const FRTConfigRequestFactory & requestFactory, std::unique_ptr<ConfigAgent> agent, const ConfigKey & key);
    ~FRTSource() override;

    void RequestDone(FRT_RPCRequest * request) override;
    void close() override;
    void reload(int64_t generation) override;
    void getConfig() override;
private:
    void scheduleNextGetConfig();

    std::shared_ptr<ConnectionFactory> _connectionFactory;
    const FRTConfigRequestFactory &    _requestFactory;
    std::unique_ptr<ConfigAgent>       _agent;
    std::unique_ptr<FRTConfigRequest>  _currentRequest;
    const ConfigKey                    _key;

    std::mutex                         _lock; // Protects _task and _closed
    std::unique_ptr<FNET_Task>         _task;
    bool                               _closed;
};

} // namespace config

