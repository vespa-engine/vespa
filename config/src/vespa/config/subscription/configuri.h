// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/common/iconfigcontext.h>
#include <vespa/config/configgen/configinstance.h>

namespace config {

class SourceSpec;

/**
 * A ConfigUri is a single representation of a configId and its source. The
 * purpose of this class is to make it more convenient to deal with config
 * subscription for single-config components. The uri can be constructed from a
 * config id, or a config id combined with a context, or by using the static
 * factory methods to create an uri from a single instance.
 */
class ConfigUri {
public:

    /**
     * Construct a config URI from a given config id.
     * @param configId The config id.
     */
    explicit ConfigUri(vespalib::stringref configId);

    /**
     * Construct a config URI from a config id and a context.
     * @param configId The config id.
     * @param context A context object that can be shared with multiple URIs.
     */
    ConfigUri(const vespalib::string &configId, std::shared_ptr<IConfigContext> context);

    ~ConfigUri();

    /**
     * Create a new config Uri with a different config id, but with the same
     * context as this URI.
     * @param configId The config id to give the new URI.
     * @return A new config URI.
     */
    ConfigUri createWithNewId(const vespalib::string & configId) const;

    /**
     * Create a config uri from a config instance. The instance does not need
     * to be kept alive.
     * @param instance The config instance to use as source.
     * @return A config uri.
     */
    static ConfigUri createFromInstance(const ConfigInstance & instance);

    /**
     * Create uri from a config id and a source spec.
     *
     * @param configId The config id to subscribe to.
     * @param spec The source spec pointing to the config source.
     */
    static ConfigUri createFromSpec(const vespalib::string & configId,
                                    const SourceSpec & spec);

    /**
     * Create a new empty config uri as initialization convenience.
     */
    static ConfigUri createEmpty();

    /**
     * Get this URIs config id. Used by subscriber.
     * @return The config id of this uri.
     */
    const vespalib::string & getConfigId() const;

    /**
     * Get the context for this uri. Used by subscriber.
     * @return The context.
     */
    const std::shared_ptr<IConfigContext> & getContext() const;

    /**
     * Empty if the original id was empty or created with createEmpty
     * @return true if empty.
     */
    bool empty() const { return _empty; }

private:
    vespalib::string                _configId;
    std::shared_ptr<IConfigContext> _context;
    bool                            _empty;
};

} // namespace config

