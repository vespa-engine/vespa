// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "rpc_target.h"
#include "shared_rpc_resources.h"
#include <vespa/fastos/thread.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/transport.h>
#include <vespa/slobrok/sbregister.h>
#include <vespa/slobrok/sbmirror.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/host_name.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <cassert>
#include <chrono>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".storage.shared_rpc_resources");

using namespace std::chrono_literals;

namespace storage::rpc {

namespace {

class RpcTargetImpl : public RpcTarget {
private:
    FRT_Target* _target;
    vespalib::string _spec;

public:
    RpcTargetImpl(FRT_Target* target, const vespalib::string& spec)
        : _target(target),
          _spec(spec)
    {}
    ~RpcTargetImpl() override {
        _target->SubRef();
    }
    FRT_Target* get() noexcept override { return _target; }
    bool is_valid() const noexcept override { return _target->IsValid(); }
    const vespalib::string& spec() const noexcept override { return _spec; }
};

}

class SharedRpcResources::RpcTargetFactoryImpl : public RpcTargetFactory {
private:
    FRT_Supervisor& _orb;

public:
    RpcTargetFactoryImpl(FRT_Supervisor& orb)
        : _orb(orb)
    {}
    std::unique_ptr<RpcTarget> make_target(const vespalib::string& connection_spec) const override {
        auto* raw_target = _orb.GetTarget(connection_spec.c_str());
        if (raw_target) {
            return std::make_unique<RpcTargetImpl>(raw_target, connection_spec);
        }
        return std::unique_ptr<RpcTarget>();
    }
};

SharedRpcResources::SharedRpcResources(const config::ConfigUri& config_uri,
                                       int rpc_server_port,
                                       size_t rpc_thread_pool_size,
                                       size_t rpc_events_before_wakeup)
    : _thread_pool(std::make_unique<FastOS_ThreadPool>(1024*60)),
      _transport(std::make_unique<FNET_Transport>(TransportConfig(rpc_thread_pool_size).
              events_before_wakeup(rpc_events_before_wakeup))),
      _orb(std::make_unique<FRT_Supervisor>(_transport.get())),
      _slobrok_register(std::make_unique<slobrok::api::RegisterAPI>(*_orb, slobrok::ConfiguratorFactory(config_uri))),
      _slobrok_mirror(std::make_unique<slobrok::api::MirrorAPI>(*_orb, slobrok::ConfiguratorFactory(config_uri))),
      _target_factory(std::make_unique<RpcTargetFactoryImpl>(*_orb)),
      _hostname(vespalib::HostName::get()),
      _rpc_server_port(rpc_server_port),
      _shutdown(false)
{ }

// TODO make sure init/shutdown is safe for aborted init in comm. mgr.

SharedRpcResources::~SharedRpcResources() {
    if (!_shutdown) {
        shutdown();
    }
}

void SharedRpcResources::start_server_and_register_slobrok(vespalib::stringref my_handle) {
    LOG(debug, "Starting main RPC supervisor on port %d with slobrok handle '%s'",
        _rpc_server_port, vespalib::string(my_handle).c_str());
    if (!_orb->Listen(_rpc_server_port)) {
        throw vespalib::IllegalStateException(vespalib::make_string("Failed to listen to RPC port %d", _rpc_server_port),
                                              VESPA_STRLOC);
    }
    _transport->Start(_thread_pool.get());
    _slobrok_register->registerName(my_handle);
    wait_until_slobrok_is_ready();
    _handle = my_handle;
}

void SharedRpcResources::wait_until_slobrok_is_ready() {
    // TODO look more closely at how mbus does its slobrok business
    while (_slobrok_register->busy() || !_slobrok_mirror->ready()) {
        // TODO some form of timeout mechanism here, and warning logging to identify SB issues
        LOG(debug, "Waiting for Slobrok to become ready");
        std::this_thread::sleep_for(50ms);
    }
}

void SharedRpcResources::shutdown() {
    assert(!_shutdown);
    if (listen_port() > 0) {
        _slobrok_register->unregisterName(_handle);
    }
    _transport->ShutDown(true);
    _shutdown = true;
}

int SharedRpcResources::listen_port() const noexcept {
    return _orb->GetListenPort();
}

const RpcTargetFactory& SharedRpcResources::target_factory() const {
    return *_target_factory;
}

}
