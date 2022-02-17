// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "confighandle.h"
#include "subscriptionid.h"
#include "configsubscription.h"
#include "configsubscriptionset.h"
#include "configprovider.h"
#include "sourcespec.h"
#include <vespa/config/common/timingvalues.h>

namespace config {

/**
 * A subscriber is a class capable of subscribing to one or more configs. The
 * class should be used as follows:
 * - subscribe for all configs you need.
 * - run nextConfig or nextGeneration to fetch the next generation of configs.
 *
 * Once nextConfig/nextGeneration is called, the state of a ConfigSubscriber is
 * FROZEN, which means that in order to change the set of subscriptions, you
 * have to recreate the ConfigSubscriber.
 *
 * Note that this class is NOT thread safe and that you should design your
 * application so that you only need to use it from one thread.
 */
class ConfigSubscriber
{
public:
    typedef std::unique_ptr<ConfigSubscriber> UP;

    /**
     * Constructs a new ConfigSubscriber object which can be used to subscribe
     * for 1 or more configs.
     *
     * @param spec The source spec from which to get config.
     */
    ConfigSubscriber(const SourceSpec & spec = ServerSpec());

    /**
     * Constructs a new ConfigSubscriber object which can be used to subscribe
     * for 1 or more configs. The provided context is used to share resources
     * betweeen multiple config subscribers.
     *
     * @param context A ConfigContext shared between all subscribers.
     */
    explicit ConfigSubscriber(std::shared_ptr<IConfigContext> context);
    ConfigSubscriber(const ConfigSubscriber &) = delete;
    ConfigSubscriber & operator= (const ConfigSubscriber &) = delete;
    ~ConfigSubscriber();

    /**
     * Checks if one or more of the configs in the set is updated or not.
     *
     * @param timeout The timeout.
     * @return true if new configs are available, false if timeout was reached
     *              or subscriber has been closed.
     */
    bool nextConfig(vespalib::duration timeout = DEFAULT_NEXTCONFIG_TIMEOUT);
    bool nextConfigNow() { return nextConfig(vespalib::duration::zero()); }

    /**
     * Checks if the generation of this config set is updated.
     *
     * @param timeout The timeout
     * @return true if a new generation are available, false if timeout was reached
     *              or subscriber has been closed.
     */
    bool nextGeneration(vespalib::duration timeout = DEFAULT_NEXTCONFIG_TIMEOUT);
    bool nextGenerationNow() { return nextGeneration(vespalib::duration::zero()); }
    /**
     * Subscribe to a config fetched from the default source specification.
     *
     * @param configId        The configId to get config for.
     * @param timeout         An optional timeout on the subscribe call
     * @return                A subscription handle which can be used to
     *                        retrieve config.
     * @throws ConfigTimeoutException if subscription timed out.
     * @throws ConfigRuntimeException if subscriber has been closed.
     */
    template <typename ConfigType>
    std::unique_ptr<ConfigHandle<ConfigType> >
    subscribe(const std::string & configId, vespalib::duration timeout = DEFAULT_SUBSCRIBE_TIMEOUT);

    /**
     * Return the current generation number for configs.
     *
     * @return generation number
     */
    int64_t getGeneration() const;

    /**
     * Closes the set, which will interrupt nextConfig() or nextGeneration(), and unsubscribe all
     * configs currently subscribed for.
     */
    void close();

    /**
     * Check if this retriever is closed.
     *
     * @return true if closed, false if not.
     */
    bool isClosed() const;

private:
    ConfigSubscriptionSet _set;                   // The set of subscriptions for this set.
};

} // namespace config
