// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/configgen/configinstance.h>

namespace config {

class IGenerationCallback
{
public:
    virtual void notifyGenerationChange(int64_t generation) = 0;
    virtual ~IGenerationCallback() {}
};

class ICallback
{
public:
    virtual void configure(std::unique_ptr<const ConfigInstance> config) = 0;
    virtual ~ICallback() { }
};

/**
 * Interface for callback methods used by ConfigFetcher, ConfigPoller and
 * LegacySubscriber.
 */
template <typename ConfigType>
class IFetcherCallback : public ICallback
{
protected:
    void configure(std::unique_ptr<const ConfigInstance> config) override {
        configure(std::unique_ptr<ConfigType>(static_cast<const ConfigType *>(config.release())));
    }
    virtual void configure(std::unique_ptr<ConfigType> config) = 0;
};

} // namespace config
