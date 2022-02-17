// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/messagebus/common.h>
#include <vespa/config/helper/configfetcher.h>
#include <vespa/messagebus/config-messagebus.h>

namespace mbus {

class IConfigHandler;

/**
 * A ConfigAgent will register with the config server and obtain
 * config on behalf of a IConfigHandler.
 **/
class ConfigAgent : public config::IFetcherCallback<messagebus::MessagebusConfig>
{
private:
    IConfigHandler &_handler;

public:
    ConfigAgent(const ConfigAgent &) = delete;
    ConfigAgent & operator = (const ConfigAgent &) = delete;
    ConfigAgent(IConfigHandler & handler);

    // Implements IFetcherCallback
    void configure(std::unique_ptr<messagebus::MessagebusConfig> config) override;
};

} // namespace mbus

