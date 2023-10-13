// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    class CleanupGuard;
    void scheduleNextGetConfig();
    void erase(FRT_RPCRequest *);
    std::shared_ptr<FRTConfigRequest> find(FRT_RPCRequest *);

    using RequestMap = std::map<FRT_RPCRequest *, std::shared_ptr<FRTConfigRequest>>;
    std::shared_ptr<ConnectionFactory> _connectionFactory;
    const FRTConfigRequestFactory &    _requestFactory;
    std::unique_ptr<ConfigAgent>       _agent;
    const ConfigKey                    _key;
    std::mutex                         _lock; // Protects _inflight, _task and _state
    std::condition_variable            _cond;
    RequestMap                         _inflight;
    std::unique_ptr<FNET_Task>         _task;
    enum class State : uint8_t { OPEN, CLOSING, CLOSED } _state;
};

} // namespace config

