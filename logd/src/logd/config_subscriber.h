// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <logd/config-logd.h>
#include <vespa/config/config.h>

namespace logdemon {

class LegacyForwarder;

/**
 * Class used to subscribe for logd config.
 */
class ConfigSubscriber {
private:
    std::string _logServer;
    int _logPort;
    int _logserverfd;
    int _statePort;
    int _rotate_size;
    int _rotate_age;
    int _remove_meg;
    int _remove_age;
    bool _use_logserver;
    LegacyForwarder& _fw;
    config::ConfigSubscriber _subscriber;
    config::ConfigHandle<cloud::config::log::LogdConfig>::UP _handle;
    bool _hasAvailable;
    bool _needToConnect;

    void connectToLogserver();
    void connectToDevNull();
    void resetFileDescriptor(int newfd);
public:
    bool checkAvailable();
    void latch();
    void closeConn();
    ConfigSubscriber(const ConfigSubscriber& other) = delete;
    ConfigSubscriber& operator=(const ConfigSubscriber& other) = delete;
    ConfigSubscriber(LegacyForwarder &fw, const config::ConfigUri &configUri);
    ~ConfigSubscriber();

    int getStatePort() const { return _statePort; }
    int getservfd() const { return _logserverfd; }
    int getRotateSize() const { return _rotate_size; }
    int getRotateAge() const { return _rotate_age; }
    int getRemoveMegabytes() const { return _remove_meg; }
    int getRemoveAge() const { return _remove_age; }
    bool useLogserver() const { return _use_logserver; }

    void configure(std::unique_ptr<cloud::config::log::LogdConfig> cfg);
    size_t generation() const { return _subscriber.getGeneration(); }
};

}

