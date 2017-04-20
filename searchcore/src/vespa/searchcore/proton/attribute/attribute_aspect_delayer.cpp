// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_aspect_delayer.h"
#include <vespa/searchlib/attribute/configconverter.h>
#include <vespa/searchcore/proton/common/i_document_type_inspector.h>
#include <vespa/searchcore/proton/common/i_indexschema_inspector.h>
#include <vespa/searchcore/proton/common/config_hash.hpp>
#include <vespa/vespalib/stllike/hash_set.hpp>
#include <vespa/config-attributes.h>
#include <vespa/config-summarymap.h>
#include "attribute_specs.h"

using search::attribute::ConfigConverter;
using vespa::config::search::AttributesConfig;
using vespa::config::search::AttributesConfigBuilder;
using vespa::config::search::SummarymapConfig;
using vespa::config::search::SummarymapConfigBuilder;
using search::attribute::BasicType;

namespace proton {

namespace {

using AttributesConfigHash = ConfigHash<AttributesConfig::Attribute>;

bool fastPartialUpdateAttribute(const search::attribute::Config &cfg) {
    auto basicType = cfg.basicType().type();
    return ((basicType != BasicType::Type::PREDICATE) &&
            (basicType != BasicType::Type::TENSOR) &&
            (basicType != BasicType::Type::REFERENCE));
}

bool willTriggerReprocessOnAttributeAspectRemoval(const search::attribute::Config &cfg,
                                                  const IIndexschemaInspector &indexschemaInspector,
                                                  const vespalib::string &name)
{
    return fastPartialUpdateAttribute(cfg) && !indexschemaInspector.isStringIndex(name);
}


}

AttributeAspectDelayer::AttributeAspectDelayer()
    : _specs(std::make_shared<AttributeSpecs>()),
      _attributesConfig(std::make_shared<AttributesConfigBuilder>()),
      _summarymapConfig(std::make_shared<SummarymapConfigBuilder>())
{
}

AttributeAspectDelayer::~AttributeAspectDelayer()
{
}

std::shared_ptr<const AttributeSpecs>
AttributeAspectDelayer::getAttributeSpecs() const
{
    return _specs;
}

std::shared_ptr<AttributeAspectDelayer::AttributesConfig>
AttributeAspectDelayer::getAttributesConfig() const
{
    return _attributesConfig;
}

std::shared_ptr<AttributeAspectDelayer::SummarymapConfig>
AttributeAspectDelayer::getSummarymapConfig() const
{
    return _summarymapConfig;
}

void
AttributeAspectDelayer::setup(const AttributesConfig &newAttributesConfig, const SummarymapConfig &newSummarymapConfig)
{
    for (const auto &attr : newAttributesConfig.attribute) {
        search::attribute::Config cfg = ConfigConverter::convert(attr);
        _specs->emplace_back(attr.name, cfg);
    }
    _attributesConfig = std::make_shared<AttributesConfigBuilder>(newAttributesConfig);
    _summarymapConfig = std::make_shared<SummarymapConfigBuilder>(newSummarymapConfig);
}

namespace {

void
handleNewAttributes(const AttributesConfig &oldAttributesConfig,
                    const AttributesConfig &newAttributesConfig,
                    const SummarymapConfig &newSummarymapConfig,
                    const IIndexschemaInspector &oldIndexschemaInspector,
                    const IDocumentTypeInspector &inspector,
                    AttributeSpecs &specs,
                    AttributesConfigBuilder &attributesConfig,
                    SummarymapConfigBuilder &summarymapConfig)
{
    vespalib::hash_set<vespalib::string> delayed;
    AttributesConfigHash oldAttrs(oldAttributesConfig.attribute);
    for (const auto &newAttr : newAttributesConfig.attribute) {
        search::attribute::Config newCfg = ConfigConverter::convert(newAttr);
        if (!inspector.hasUnchangedField(newAttr.name)) {
            // No reprocessing due to field type change, just use new config
            specs.emplace_back(newAttr.name, newCfg);
            attributesConfig.attribute.emplace_back(newAttr);
        } else {
            auto oldAttr = oldAttrs.lookup(newAttr.name);
            if (oldAttr != nullptr) {
                search::attribute::Config oldCfg = ConfigConverter::convert(*oldAttr);
                if (willTriggerReprocessOnAttributeAspectRemoval(oldCfg, oldIndexschemaInspector, newAttr.name) || !oldAttr->fastaccess) {
                    // Delay change of fast access flag
                    newCfg.setFastAccess(oldAttr->fastaccess);
                    specs.emplace_back(newAttr.name, newCfg);
                    auto modNewAttr = newAttr;
                    modNewAttr.fastaccess = oldAttr->fastaccess;
                    attributesConfig.attribute.emplace_back(modNewAttr);
                    // TODO: Don't delay change of fast access flag if
                    // attribute type can change without doucment field
                    // type changing (needs a smarter attribute
                    // reprocessing initializer).
                } else {
                    // Don't delay change of fast access flag from true to
                    // false when removing attribute aspect in a way that
                    // doesn't trigger reprocessing.
                    specs.emplace_back(newAttr.name, newCfg);
                    attributesConfig.attribute.emplace_back(newAttr);
                }
            } else {
                // Delay addition of attribute aspect
                delayed.insert(newAttr.name);
            }
        }
    }
    for (const auto &override : newSummarymapConfig.override) {
        if (override.command == "attribute") {
            auto itr = delayed.find(override.field);
            if (itr == delayed.end()) {
                summarymapConfig.override.emplace_back(override);
            }
        } else {
            summarymapConfig.override.emplace_back(override);
        }
    }
}

void
handleOldAttributes(const AttributesConfig &oldAttributesConfig,
                    const AttributesConfig &newAttributesConfig,
                    const SummarymapConfig &oldSummarymapConfig,
                    const IIndexschemaInspector &oldIndexschemaInspector,
                    const IDocumentTypeInspector &inspector,
                    AttributeSpecs &specs,
                    AttributesConfigBuilder &attributesConfig,
                    SummarymapConfigBuilder &summarymapConfig)
{
    vespalib::hash_set<vespalib::string> delayed;
    AttributesConfigHash newAttrs(newAttributesConfig.attribute);
    for (const auto &oldAttr : oldAttributesConfig.attribute) {
        search::attribute::Config oldCfg = ConfigConverter::convert(oldAttr);
        if (inspector.hasUnchangedField(oldAttr.name)) {
            auto newAttr = newAttrs.lookup(oldAttr.name);
            if (newAttr == nullptr) {
                // Delay removal of attribute aspect if it would trigger
                // reprocessing.
                if (willTriggerReprocessOnAttributeAspectRemoval(oldCfg, oldIndexschemaInspector, oldAttr.name)) {
                    specs.emplace_back(oldAttr.name, oldCfg);
                    attributesConfig.attribute.emplace_back(oldAttr);
                    delayed.insert(oldAttr.name);
                }
            }
        }
    }
    for (const auto &override : oldSummarymapConfig.override) {
        if (override.command == "attribute") {
            auto itr = delayed.find(override.field);
            if (itr != delayed.end()) {
                summarymapConfig.override.emplace_back(override);
            }
        }
    }
}

}

void
AttributeAspectDelayer::setup(const AttributesConfig &oldAttributesConfig,
                             const SummarymapConfig &oldSummarymapConfig,
                             const AttributesConfig &newAttributesConfig,
                             const SummarymapConfig &newSummarymapConfig,
                             const IIndexschemaInspector &oldIndexschemaInspector,
                             const IDocumentTypeInspector &inspector)
{
    handleNewAttributes(oldAttributesConfig, newAttributesConfig,
                        newSummarymapConfig,
                        oldIndexschemaInspector, inspector, *_specs,
                        *_attributesConfig, *_summarymapConfig);
    handleOldAttributes(oldAttributesConfig, newAttributesConfig,
                        oldSummarymapConfig,
                        oldIndexschemaInspector, inspector, *_specs,
                        *_attributesConfig, *_summarymapConfig);
}

} // namespace proton
