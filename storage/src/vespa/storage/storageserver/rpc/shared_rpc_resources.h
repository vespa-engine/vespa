// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "rpc_target_factory.h"
#include <vespa/config/subscription/configuri.h>
#include <vespa/vespalib/stllike/string.h>
#include <memory>

class FNET_Transport;
class FRT_Supervisor;

namespace slobrok::api {
class RegisterAPI;
class MirrorAPI;
}

namespace storage::rpc {

class SharedRpcResources {
    class RpcTargetFactoryImpl;
    std::unique_ptr<FNET_Transport>            _transport;
    std::unique_ptr<FRT_Supervisor>            _orb;
    std::unique_ptr<slobrok::api::RegisterAPI> _slobrok_register;
    std::unique_ptr<slobrok::api::MirrorAPI>   _slobrok_mirror;
    std::unique_ptr<RpcTargetFactoryImpl>      _target_factory;
    vespalib::string                           _hostname;
    vespalib::string                           _handle;
    int                                        _rpc_server_port;
    bool                                       _shutdown;
public:
    SharedRpcResources(const config::ConfigUri& config_uri, int rpc_server_port,
                       size_t rpc_thread_pool_size, size_t rpc_events_before_wakeup);
    ~SharedRpcResources();

    FRT_Supervisor& supervisor() noexcept { return *_orb; }
    const FRT_Supervisor& supervisor() const noexcept { return *_orb; }

    slobrok::api::RegisterAPI& slobrok_register() noexcept { return *_slobrok_register; }
    const slobrok::api::RegisterAPI& slobrok_register() const noexcept { return *_slobrok_register; }
    slobrok::api::MirrorAPI& slobrok_mirror() noexcept { return *_slobrok_mirror; }
    const slobrok::api::MirrorAPI& slobrok_mirror() const noexcept { return *_slobrok_mirror; }
    // To be called after all RPC handlers have been registered.
    void start_server_and_register_slobrok(vespalib::stringref my_handle);

    void shutdown();
    [[nodiscard]] int listen_port() const noexcept; // Only valid if server has been started

    // Hostname of host node is running on.
    [[nodiscard]] const vespalib::string& hostname() const noexcept { return _hostname; }
    [[nodiscard]] const vespalib::string handle() const noexcept { return _handle; }

    const RpcTargetFactory& target_factory() const;
private:
    void wait_until_slobrok_is_ready();
};


}
