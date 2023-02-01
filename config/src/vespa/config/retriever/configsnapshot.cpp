// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configsnapshot.h"
#include <vespa/config/subscription/configsubscription.h>
#include <vespa/config/print/configdatabuffer.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/config/common/misc.h>
#include <vespa/config/frt/protocol.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/stllike/asciistream.h>

using vespalib::Slime;
using vespalib::slime::Cursor;
using vespalib::slime::Inspector;
using vespalib::Memory;

namespace config {

const int64_t ConfigSnapshot::SNAPSHOT_FORMAT_VERSION = 1;

ConfigSnapshot::ConfigSnapshot()
        : _valueMap(),
          _generation(0) {}

ConfigSnapshot::~ConfigSnapshot() = default;

ConfigSnapshot::ConfigSnapshot(const ConfigSnapshot &rhs) = default;

ConfigSnapshot &
ConfigSnapshot::operator=(const ConfigSnapshot &rhs) {
    if (&rhs != this) {
        ConfigSnapshot tmp(rhs);
        tmp.swap(*this);
    }
    return *this;
}

void
ConfigSnapshot::swap(ConfigSnapshot &rhs) {
    _valueMap.swap(rhs._valueMap);
    std::swap(_generation, rhs._generation);
}

ConfigSnapshot::ConfigSnapshot(const SubscriptionList &subscriptionList, int64_t generation)
    : _valueMap(),
      _generation(generation)
{
    for (SubscriptionList::const_iterator it(subscriptionList.begin()), mt(subscriptionList.end()); it != mt; it++) {
        _valueMap[(*it)->getKey()] = Value((*it)->getLastGenerationChanged(), (*it)->getConfig());
    }
}

ConfigSnapshot::ConfigSnapshot(const ValueMap &valueMap, int64_t generation)
        : _valueMap(valueMap),
          _generation(generation) {
}

ConfigSnapshot::ValueMap::const_iterator
ConfigSnapshot::find(const ConfigKey &key) const {
    ValueMap::const_iterator it(_valueMap.find(key));
    if (it == _valueMap.end()) {
        throw IllegalConfigKeyException("Unable to find config for key " + key.toString());
    }
    return it;
}

ConfigSnapshot
ConfigSnapshot::subset(const ConfigKeySet & keySet) const
{
    ValueMap subSet;
    for (const ConfigKey & key : keySet) {
        ValueMap::const_iterator found(_valueMap.find(key));
        if (found != _valueMap.end()) {
            subSet[key] = found->second;
        }
    }
    return ConfigSnapshot(subSet, _generation);
}

int64_t ConfigSnapshot::getGeneration() const { return _generation; }
size_t ConfigSnapshot::size() const { return _valueMap.size(); }
bool ConfigSnapshot::empty() const { return _valueMap.empty(); }

void
ConfigSnapshot::serialize(ConfigDataBuffer & buffer) const
{
    Slime & slime(buffer.slimeObject());
    Cursor & root(slime.setObject());
    root.setDouble("version", SNAPSHOT_FORMAT_VERSION);

    switch (SNAPSHOT_FORMAT_VERSION) {
        case 1:
            serializeV1(root);
            break;
        case 2:
            serializeV2(root);
            break;
        default:
            vespalib::asciistream ss;
            ss << "Version '" << SNAPSHOT_FORMAT_VERSION << "' is not a valid version.";
            throw ConfigWriteException(ss.str());
    }
}

void
ConfigSnapshot::serializeV1(Cursor & root) const
{
    root.setDouble("generation", _generation);
    Cursor & snapshots(root.setArray("snapshots"));
    for (ValueMap::const_iterator it(_valueMap.begin()), mt(_valueMap.end()); it != mt; it++) {
        Cursor & snapshot(snapshots.addObject());
        serializeKeyV1(snapshot.setObject("configKey"), it->first);
        serializeValueV1(snapshot.setObject("configPayload"), it->second);
    }
}

void
ConfigSnapshot::serializeV2(Cursor & root) const
{
    root.setDouble("generation", _generation);
    Cursor & snapshots(root.setArray("snapshots"));
    for (ValueMap::const_iterator it(_valueMap.begin()), mt(_valueMap.end()); it != mt; it++) {
        Cursor & snapshot(snapshots.addObject());
        serializeKeyV1(snapshot.setObject("configKey"), it->first);
        serializeValueV2(snapshot.setObject("configPayload"), it->second);
    }
}

void
ConfigSnapshot::serializeKeyV1(Cursor & cursor, const ConfigKey & key) const
{
    cursor.setString("configId", Memory(key.getConfigId()));
    cursor.setString("defName", Memory(key.getDefName()));
    cursor.setString("defNamespace", Memory(key.getDefNamespace()));
    cursor.setString("defMd5", Memory(key.getDefMd5()));
    Cursor & defSchema(cursor.setArray("defSchema"));
    for (const vespalib::string & line : key.getDefSchema()) {
        defSchema.addString(vespalib::Memory(line));
    }
}

void
ConfigSnapshot::serializeValueV1(Cursor & cursor, const Value & value) const
{
    cursor.setDouble("lastChanged", value.first);
    value.second.serializeV1(cursor.setArray("lines"));
}

void
ConfigSnapshot::serializeValueV2(Cursor & cursor, const Value & value) const
{
    cursor.setDouble("lastChanged", value.first);
    cursor.setString("xxhash64", Memory(value.second.getXxhash64()));
    value.second.serializeV2(cursor.setObject("payload"));
}

void
ConfigSnapshot::deserialize(const ConfigDataBuffer & buffer)
{
    const Slime & slime(buffer.slimeObject());
    Inspector & inspector(slime.get());
    int64_t version = static_cast<int64_t>(inspector["version"].asDouble());
    switch (version) {
    case 1:
        deserializeV1(inspector);
        break;
    case 2:
        deserializeV2(inspector);
        break;
    default:
        vespalib::asciistream ss;
        ss << "Version '" << version << "' is not a valid version.";
        throw ConfigReadException(ss.str());
    }
}

void
ConfigSnapshot::deserializeV1(Inspector & root)
{
    _generation = static_cast<int64_t>(root["generation"].asDouble());
    Inspector & snapshots(root["snapshots"]);
    for (size_t i = 0; i < snapshots.children(); i++) {
        Inspector & snapshot(snapshots[i]);
        ConfigKey key(deserializeKeyV1(snapshot["configKey"]));
        Value value(deserializeValueV1(snapshot["configPayload"]));
        _valueMap[key] = value;
    }
}

void
ConfigSnapshot::deserializeV2(Inspector & root)
{
    _generation = static_cast<int64_t>(root["generation"].asDouble());
    Inspector & snapshots(root["snapshots"]);
    for (size_t i = 0; i < snapshots.children(); i++) {
        Inspector & snapshot(snapshots[i]);
        ConfigKey key(deserializeKeyV1(snapshot["configKey"]));
        Value value(deserializeValueV2(snapshot["configPayload"]));
        _valueMap[key] = value;
    }
}

ConfigKey
ConfigSnapshot::deserializeKeyV1(Inspector & inspector) const
{
    StringVector schema;
    Inspector & s(inspector["defSchema"]);
    for (size_t i = 0; i < s.children(); i++) {
        schema.push_back(s[i].asString().make_string());
    }
    return ConfigKey(inspector["configId"].asString().make_string(),
                     inspector["defName"].asString().make_string(),
                     inspector["defNamespace"].asString().make_string(),
                     inspector["defMd5"].asString().make_string(),
                     schema);

}

std::pair<int64_t, ConfigValue>
ConfigSnapshot::deserializeValueV1(Inspector & inspector) const
{
    StringVector payload;
    int64_t lastChanged = static_cast<int64_t>(inspector["lastChanged"].asDouble());
    Inspector & s(inspector["lines"]);
    for (size_t i = 0; i < s.children(); i++) {
        payload.push_back(s[i].asString().make_string());
    }
    return Value(lastChanged, ConfigValue(payload));
}

namespace {

class FixedPayload : public protocol::Payload {
public:
    const Inspector & getSlimePayload() const override {
        return _data.get();
    }

    Slime & getData() { return _data; }
    ~FixedPayload() override;
private:
    Slime _data;
};

FixedPayload::~FixedPayload() = default;

}

std::pair<int64_t, ConfigValue>
ConfigSnapshot::deserializeValueV2(Inspector & inspector) const
{
    int64_t lastChanged = static_cast<int64_t>(inspector["lastChanged"].asDouble());
    vespalib::string xxhash64(inspector["xxhash64"].asString().make_string());
    auto payload = std::make_unique<FixedPayload>();
    copySlimeObject(inspector["payload"], payload->getData().setObject());
    return Value(lastChanged, ConfigValue(std::move(payload), xxhash64));
}

}
