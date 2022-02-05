// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "configkeyset.h"
#include <vespa/config/common/configvalue.h>
#include <vespa/config/common/misc.h>

namespace config {

class ConfigSubscription;
class ConfigDataBuffer;

/**
 * A ConfigSnapshot contains a map of config keys to config instances. You may
 * request an instance of a config by calling the getConfig method.
 */
class ConfigSnapshot
{
public:
    using SubscriptionList = std::vector<std::shared_ptr<ConfigSubscription>>;

    /**
     * Construct an empty config snapshot.
     */
    ConfigSnapshot();
    ~ConfigSnapshot();

    ConfigSnapshot(const ConfigSnapshot & rhs);

    /**
     * Construct a config snapshot from a list of subscriptions and their
     * current generation.
     *
     * @param subscriptionList A list of config subscriptions used to populate
     *                         the snapshot.
     * @param generation The latest generation of configs.
     */
    ConfigSnapshot(const SubscriptionList & subscriptionList, int64_t generation);

    /**
     * Instantiate one of the configs from this snapshot identified by its type
     * and config id.
     *
     * @param configId The configId of the desired instance.
     * @return an std::unqiue_ptr to an instance of this config.
     * @throws InvalidConfigException if unable instantiate the given type or
     *         parse config.
     * @throws IllegalConfigKeyException if the config does not exist.
     */
    template <typename ConfigType>
    std::unique_ptr<ConfigType> getConfig(const vespalib::string & configId) const;

    /**
     * Query snapshot to check if a config of type ConfigType and id configId is
     * changed relative to a provided generation.
     *
     * @param configId The configId of the instance to check.
     * @param currentGeneration The generation of the current active config in
     *                          use by the caller.
     * @return true if changed, false if not.
     * @throws IllegalConfigKeyException if the config does not exist.
     */
    template <typename ConfigType>
    bool isChanged(const vespalib::string & configId, int64_t currentGeneration) const;

    ConfigSnapshot & operator = (const ConfigSnapshot & rhs);
    void swap(ConfigSnapshot & rhs);

    /**
     * Query snapshot to check if a config of type ConfigType and id configId
     * exists in this snapshot.
     *
     * @param configId The configId of the instance to check.
     * @return true if exists, false if not.
     */
    template <typename ConfigType>
    bool hasConfig(const vespalib::string & configId) const;

    /**
     * Create a new snapshot as a subset of this snapshot based on a set of keys.
     * If a key does not exist in this snapshot, the new snapshot will not
     * contain an entry for that key.
     *
     * @param keySet The keySet to use for selecting which configs to put in the
     *               new snapshot.
     * @return a new snapshot.
     */
    ConfigSnapshot subset(const ConfigKeySet & keySet) const;

    int64_t getGeneration() const;
    size_t size() const;
    bool empty() const;

    void serialize(ConfigDataBuffer & buffer) const;
    void deserialize(const ConfigDataBuffer & buffer);
private:
    using Value = std::pair<int64_t, ConfigValue>;
    using ValueMap = std::map<ConfigKey, Value>;
    const static int64_t SNAPSHOT_FORMAT_VERSION;

    ConfigSnapshot(const ValueMap & valueMap, int64_t generation);
    void serializeV1(vespalib::slime::Cursor & root) const;
    void serializeKeyV1(vespalib::slime::Cursor & root, const ConfigKey & key) const;
    void serializeValueV1(vespalib::slime::Cursor & root, const Value & value) const;

    void deserializeV1(vespalib::slime::Inspector & root);
    ConfigKey deserializeKeyV1(vespalib::slime::Inspector & inspector) const;
    Value deserializeValueV1(vespalib::slime::Inspector & inspector) const;

    void serializeV2(vespalib::slime::Cursor & root) const;
    void serializeValueV2(vespalib::slime::Cursor & root, const Value & value) const;

    void deserializeV2(vespalib::slime::Inspector & root);
    Value deserializeValueV2(vespalib::slime::Inspector & inspector) const;

    ValueMap::const_iterator find(const ConfigKey & key) const;

    ValueMap _valueMap;
    int64_t _generation;
};

} // namespace config
