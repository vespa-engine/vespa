// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>

namespace config {

class ConfigSubscription;

/**
 * A ConfigHandle is a subscription handle that is capable of looking up config
 * objects of a generic type.
 */
template <typename ConfigType>
class ConfigHandle
{
public:
    typedef std::unique_ptr<ConfigHandle <ConfigType> > UP;

    explicit ConfigHandle(std::shared_ptr<ConfigSubscription> subscription);
    ~ConfigHandle();

    /**
     * Return the currently available config known to the ConfigHandle. Throws
     * a ConfigRuntimeException if the ConfigSubscriber has not yet been polled
     * for config, and InvalidConfigException, if there are errors with the
     * config payload.
     *
     * @return current config.
     * @throws InvalidConfigException if unable instantiate the given type or
     *         parse config.
     */
    std::unique_ptr<ConfigType> getConfig() const;

    /**
     * Returns whether or not this handles config has changed since the last
     * call to ConfigSubscriber.nextConfig() was made.
     *
     * @return true if changed, false if not.
     */
    bool isChanged() const;
private:
    std::shared_ptr<ConfigSubscription> _subscription;
};

} // namespace config

