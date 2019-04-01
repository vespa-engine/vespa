// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "forwarder.h"
#include <logd/config-logd.h>
#include <vespa/config/config.h>
#include <vespa/fnet/frt/supervisor.h>

namespace logdemon {

class Metrics;

/**
 * Class used to subscribe for logd config.
 */
class ConfigSubscriber {
private:
    std::string _logserver_host;
    int _logserver_port;
    int _logserver_rpc_port;
    bool _logserver_use_rpc;
    int _state_port;
    ForwardMap _forward_filter;
    int _rotate_size;
    int _rotate_age;
    int _remove_meg;
    int _remove_age;
    bool _use_logserver;
    config::ConfigSubscriber _subscriber;
    config::ConfigHandle<cloud::config::log::LogdConfig>::UP _handle;
    bool _has_available;
    bool _need_new_forwarder;
    FRT_Supervisor _supervisor;

public:
    bool checkAvailable();
    void latch();
    ConfigSubscriber(const ConfigSubscriber& other) = delete;
    ConfigSubscriber& operator=(const ConfigSubscriber& other) = delete;
    ConfigSubscriber(const config::ConfigUri& configUri);
    ~ConfigSubscriber();

    int getStatePort() const { return _state_port; }
    int getRotateSize() const { return _rotate_size; }
    int getRotateAge() const { return _rotate_age; }
    int getRemoveMegabytes() const { return _remove_meg; }
    int getRemoveAge() const { return _remove_age; }

    bool need_new_forwarder() const { return _need_new_forwarder; }
    std::unique_ptr<Forwarder> make_forwarder(Metrics& metrics);

    void configure(std::unique_ptr<cloud::config::log::LogdConfig> cfg);
    size_t generation() const { return _subscriber.getGeneration(); }
};

}

