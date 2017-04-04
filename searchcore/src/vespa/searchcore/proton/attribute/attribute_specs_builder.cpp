// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_specs_builder.h"
#include <vespa/searchlib/attribute/configconverter.h>
#include <vespa/searchcore/proton/common/i_document_type_inspector.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/config-indexschema.h>
#include <vespa/config-attributes.h>
#include "attribute_specs.h"

using search::attribute::ConfigConverter;
using vespa::config::search::AttributesConfig;
using vespa::config::search::AttributesConfigBuilder;
using vespa::config::search::IndexschemaConfig;
using search::attribute::BasicType;

namespace proton {

namespace {

template <class Elem>
class ConfigHash {
    vespalib::hash_map<vespalib::string, const Elem *> _hash;
public:
    ConfigHash(const std::vector<Elem> &config)
        : _hash()
    {
        for (const auto &elem : config) {
            auto insres = _hash.insert(std::make_pair(elem.name, &elem));
            assert(insres.second);
        }
    }
    ~ConfigHash() { }
    const Elem *lookup(const vespalib::string &name) const {
        auto itr = _hash.find(name);
        return ((itr == _hash.end()) ? nullptr : itr->second);
    }
};

using AttributesConfigHash = ConfigHash<AttributesConfig::Attribute>;
using IndexConfigHash = ConfigHash<IndexschemaConfig::Indexfield>;

bool fastPartialUpdateAttribute(const search::attribute::Config &cfg) {
    auto basicType = cfg.basicType().type();
    return ((basicType != BasicType::Type::PREDICATE) &&
            (basicType != BasicType::Type::TENSOR) &&
            (basicType != BasicType::Type::REFERENCE));
}

bool isStringIndex(const IndexConfigHash &indexConfig, const vespalib::string &name)
{
    auto index = indexConfig.lookup(name);
    if (index != nullptr) {
        if (index->datatype == IndexschemaConfig::Indexfield::Datatype::STRING) {
            return true;
        }
    }
    return false;
}

bool willTriggerReprocessOnAttributeAspectRemoval(const search::attribute::Config &cfg,
                                                  const IndexConfigHash &indexConfig,
                                                  const vespalib::string &name)
{
    return fastPartialUpdateAttribute(cfg) && !isStringIndex(indexConfig, name);
}


}

AttributeSpecsBuilder::AttributeSpecsBuilder()
    : _specs(std::make_shared<AttributeSpecs>()),
      _config(std::make_shared<AttributesConfigBuilder>())
{
}

AttributeSpecsBuilder::~AttributeSpecsBuilder()
{
}

std::shared_ptr<const AttributeSpecs>
AttributeSpecsBuilder::getAttributeSpecs() const
{
    return _specs;
}

std::shared_ptr<AttributeSpecsBuilder::AttributesConfig>
AttributeSpecsBuilder::getAttributesConfig() const
{
    return _config;
}

void
AttributeSpecsBuilder::setup(const AttributesConfig &newConfig)
{
    for (const auto &attr : newConfig.attribute) {
        search::attribute::Config cfg = ConfigConverter::convert(attr);
        _specs->emplace_back(attr.name, cfg);
    }
    _config = std::make_shared<AttributesConfigBuilder>(newConfig);
}

namespace {

void
handleNewAttributes(const AttributesConfig &oldAttributesConfig,
                    const AttributesConfig &newAttributesConfig,
                    IndexConfigHash &oldIndexes,
                    const IDocumentTypeInspector &inspector,
                    AttributeSpecs &specs,
                    AttributesConfigBuilder &config)
{
    AttributesConfigHash oldAttrs(oldAttributesConfig.attribute);
    for (const auto &newAttr : newAttributesConfig.attribute) {
        search::attribute::Config newCfg = ConfigConverter::convert(newAttr);
        if (!inspector.hasUnchangedField(newAttr.name)) {
            // No reprocessing due to field type change, just use new config
            specs.emplace_back(newAttr.name, newCfg);
            config.attribute.emplace_back(newAttr);
        } else {
            auto oldAttr = oldAttrs.lookup(newAttr.name);
            if (oldAttr != nullptr) {
                search::attribute::Config oldCfg = ConfigConverter::convert(*oldAttr);
                if (willTriggerReprocessOnAttributeAspectRemoval(oldCfg, oldIndexes, newAttr.name) || !oldAttr->fastaccess) {
                    // Delay change of fast access flag
                    newCfg.setFastAccess(oldAttr->fastaccess);
                    specs.emplace_back(newAttr.name, newCfg);
                    auto modNewAttr = newAttr;
                    modNewAttr.fastaccess = oldAttr->fastaccess;
                    config.attribute.emplace_back(modNewAttr);
                    // TODO: Don't delay change of fast access flag if
                    // attribute type can change without doucment field
                    // type changing (needs a smarter attribute
                    // reprocessing initializer).
                } else {
                    // Don't delay change of fast access flag from true to
                    // false when removing attribute aspect in a way that
                    // doesn't trigger reprocessing.
                    specs.emplace_back(newAttr.name, newCfg);
                    config.attribute.emplace_back(newAttr);
                }
            } else {
                // Delay addition of attribute aspect
                specs.emplace_back(newAttr.name, newCfg, false, true);
            }
        }
    }
}

void
handleOldAttributes(const AttributesConfig &oldAttributesConfig,
                    const AttributesConfig &newAttributesConfig,
                    IndexConfigHash &oldIndexes,
                    const IDocumentTypeInspector &inspector,
                    AttributeSpecs &specs,
                    AttributesConfigBuilder &config)
{
    AttributesConfigHash newAttrs(newAttributesConfig.attribute);
    for (const auto &oldAttr : oldAttributesConfig.attribute) {
        search::attribute::Config oldCfg = ConfigConverter::convert(oldAttr);
        if (inspector.hasUnchangedField(oldAttr.name)) {
            auto newAttr = newAttrs.lookup(oldAttr.name);
            if (newAttr == nullptr) {
                // Delay removal of attribute aspect if it would trigger
                // reprocessing.
                if (willTriggerReprocessOnAttributeAspectRemoval(oldCfg, oldIndexes, oldAttr.name)) {
                    specs.emplace_back(oldAttr.name, oldCfg, true, false);
                    config.attribute.emplace_back(oldAttr);
                }
            }
        }
    }
}

}

void
AttributeSpecsBuilder::setup(const AttributesConfig &oldAttributesConfig,
                             const AttributesConfig &newAttributesConfig,
                             const IndexschemaConfig &oldIndexschemaConfig,
                             const IDocumentTypeInspector &inspector)
{
    IndexConfigHash oldIndexes(oldIndexschemaConfig.indexfield);
    handleNewAttributes(oldAttributesConfig, newAttributesConfig,
                        oldIndexes, inspector, *_specs, *_config);
    handleOldAttributes(oldAttributesConfig, newAttributesConfig,
                        oldIndexes, inspector, *_specs, *_config);
}

} // namespace proton
