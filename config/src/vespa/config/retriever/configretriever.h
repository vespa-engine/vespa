// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "configsnapshot.h"
#include "genericconfigsubscriber.h"
#include "fixedconfigsubscriber.h"
#include <vespa/config/common/configkey.h>
#include <vespa/config/subscription/configsubscription.h>
#include <vespa/vespalib/stllike/string.h>
#include <mutex>

namespace config {

class SourceSpec;

/**
 * A ConfigRetriever is a helper class for retrieving a set of dynamically
 * changing and depending configs. You should use this class whenever you have a
 * set of bootstrap configs, and want to subscribe to a dynamically changing set
 * of configs based on those.
 *
 * The retriever should be used from one thread only, but close can be called
 * from another thread.
 */
class ConfigRetriever
{
public:
    ConfigRetriever(const ConfigKeySet & bootstrapSet,
                    std::shared_ptr<IConfigContext> context,
                    vespalib::duration subscribeTimeout = DEFAULT_SUBSCRIBE_TIMEOUT);
    ~ConfigRetriever();

    /**
     * Waits for the next generation of bootstrap configs to arrive, and returns
     * them. If no new generation has arrived, return an empty snapshot.
     *
     * @param timeout The timeout of the nextGeneration call. Optional.
     * @return a snapshot of bootstrap configs, empty if no new snapshot or
     *         retriever has been closed.
     * @throws ConfigTimeoutException if initial subscribe timed out.
     */
    ConfigSnapshot getBootstrapConfigs(vespalib::duration timeout = DEFAULT_NEXTGENERATION_TIMEOUT);

    /**
     * Return the configs represented by a ConfigKeySet in a snapshot, and makes
     * sure that it is in sync with the bootstrap config. If it is not, an empty
     * snapshot is returned.
     *
     * @param keySet The set of configs that should be fetched. The set may only
     *               change when bootstrap has been changed.
     * @param timeout The timeout. Optional.
     * @return a snapshot of configs corresponding to the keySet or
     *         an empty snapshot if
     *              a) retriever has been closed. The isClosed() method can be
     *                 used to check for this condition.
     *              b) no new generation was found, in which case getConfigs()
     *                 should be called again.
     *              c) generation is not in sync with bootstrap, in which case
     *                 getBootstrapConfigs() must be called. The bootstrapRequired
     *                 method can be used to check for this condition.
     * @throws ConfigTimeoutException if resubscribe timed out.
     */
    ConfigSnapshot getConfigs(const ConfigKeySet & keySet, vespalib::duration timeout = DEFAULT_NEXTGENERATION_TIMEOUT);

    /**
     * Close this retriever in order to shut down.
     */
    void close();

    /**
     * Check if this retriever is closed.
     *
     * @return true if closed, false if not.
     */
    bool isClosed() const;

    /**
     * Returns if a new bootstrap call is required.
     *
     * @return true if required, false if not.
     */
    bool bootstrapRequired() const { return _bootstrapRequired; }

    /**
     * Get the current generation of the configs managed by this retriever.
     *
     * @return the generation
     */
    int64_t getGeneration() const { return _generation; }

    static const vespalib::duration DEFAULT_SUBSCRIBE_TIMEOUT;
    static const vespalib::duration DEFAULT_NEXTGENERATION_TIMEOUT;
private:
    FixedConfigSubscriber                            _bootstrapSubscriber;
    std::unique_ptr<GenericConfigSubscriber>         _configSubscriber;
    std::mutex                                       _lock;
    std::vector<std::shared_ptr<ConfigSubscription>> _subscriptionList;
    ConfigKeySet                                     _lastKeySet;
    std::shared_ptr<IConfigContext>                  _context;
    std::unique_ptr<SourceSpec>                      _spec;
    int64_t              _generation;
    vespalib::duration   _subscribeTimeout;
    bool                 _bootstrapRequired;
    std::atomic<bool>    _closed;
};

} // namespace config

