// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_aspect_delayer.h"
#include <vespa/config-attributes.h>
#include <vespa/config-summary.h>
#include <vespa/config-summarymap.h>
#include <vespa/searchcommon/attribute/attribute_utils.h>
#include <vespa/searchcore/proton/common/config_hash.hpp>
#include <vespa/searchcore/proton/common/i_document_type_inspector.h>
#include <vespa/searchcore/proton/common/i_indexschema_inspector.h>
#include <vespa/searchlib/attribute/configconverter.h>
#include <vespa/vespalib/stllike/hash_set.hpp>

using search::attribute::isUpdateableInMemoryOnly;
using search::attribute::BasicType;
using search::attribute::ConfigConverter;
using vespa::config::search::AttributesConfig;
using vespa::config::search::AttributesConfigBuilder;
using vespa::config::search::SummaryConfig;
using vespa::config::search::SummarymapConfig;
using vespa::config::search::SummarymapConfigBuilder;

namespace proton {

namespace {

using AttributesConfigHash = ConfigHash<AttributesConfig::Attribute>;

bool willTriggerReprocessOnAttributeAspectRemoval(const search::attribute::Config &cfg,
                                                  const IIndexschemaInspector &indexschemaInspector,
                                                  const vespalib::string &name)
{
    return isUpdateableInMemoryOnly(name, cfg) &&
            !indexschemaInspector.isStringIndex(name);
}

class KnownSummaryFields
{
    vespalib::hash_set<vespalib::string> _fields;

public:
    KnownSummaryFields(const SummaryConfig &summaryConfig);
    ~KnownSummaryFields();

    bool known(const vespalib::string &fieldName) const {
        return _fields.find(fieldName) != _fields.end();
    }
};

KnownSummaryFields::KnownSummaryFields(const SummaryConfig &summaryConfig)
    : _fields()
{
    for (const auto &summaryClass : summaryConfig.classes) {
        for (const auto &summaryField : summaryClass.fields) {
            _fields.insert(summaryField.name);
        }
    }
}

KnownSummaryFields::~KnownSummaryFields() = default;

vespalib::string source_field(const SummarymapConfig::Override &override) {
    if (override.arguments == "") {
        return override.field;
    } else {
        return override.arguments;
    }
}

}

AttributeAspectDelayer::AttributeAspectDelayer()
    : _attributesConfig(std::make_shared<AttributesConfigBuilder>()),
      _summarymapConfig(std::make_shared<SummarymapConfigBuilder>())
{
}

AttributeAspectDelayer::~AttributeAspectDelayer()
{
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

namespace {

void
handleNewAttributes(const AttributesConfig &oldAttributesConfig,
                    const AttributesConfig &newAttributesConfig,
                    const SummarymapConfig &newSummarymapConfig,
                    const IIndexschemaInspector &oldIndexschemaInspector,
                    const IDocumentTypeInspector &inspector,
                    AttributesConfigBuilder &attributesConfig,
                    SummarymapConfigBuilder &summarymapConfig)
{
    vespalib::hash_set<vespalib::string> delayed;
    vespalib::hash_set<vespalib::string> delayedStruct;
    AttributesConfigHash oldAttrs(oldAttributesConfig.attribute);
    for (const auto &newAttr : newAttributesConfig.attribute) {
        search::attribute::Config newCfg = ConfigConverter::convert(newAttr);
        if (!inspector.hasUnchangedField(newAttr.name)) {
            // No reprocessing due to field type change, just use new config
            attributesConfig.attribute.emplace_back(newAttr);
        } else {
            auto oldAttr = oldAttrs.lookup(newAttr.name);
            if (oldAttr != nullptr) {
                search::attribute::Config oldCfg = ConfigConverter::convert(*oldAttr);
                if (willTriggerReprocessOnAttributeAspectRemoval(oldCfg, oldIndexschemaInspector, newAttr.name) || !oldAttr->fastaccess) {
                    // Delay change of fast access flag
                    newCfg.setFastAccess(oldAttr->fastaccess);
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
                    attributesConfig.attribute.emplace_back(newAttr);
                }
            } else {
                // Delay addition of attribute aspect
                delayed.insert(newAttr.name);
                auto pos = newAttr.name.find('.');
                if (pos != vespalib::string::npos) {
                    delayedStruct.insert(newAttr.name.substr(0, pos));
                }
            }
        }
    }
    for (const auto &override : newSummarymapConfig.override) {
        if (override.command == "attribute") {
            auto itr = delayed.find(source_field(override));
            if (itr == delayed.end()) {
                summarymapConfig.override.emplace_back(override);
            }
        } else if (override.command == "attributecombiner") {
            auto itr = delayedStruct.find(source_field(override));
            if (itr == delayedStruct.end()) {
                summarymapConfig.override.emplace_back(override);
            }
        } else if (override.command == "matchedattributeelementsfilter") {
            auto itr = delayedStruct.find(source_field(override));
            if (itr == delayedStruct.end()) {
                summarymapConfig.override.emplace_back(override);
            } else {
                SummarymapConfig::Override mutated_override(override);
                mutated_override.command = "matchedelementsfilter";
                summarymapConfig.override.emplace_back(mutated_override);
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
                    const SummaryConfig &newSummaryConfig,
                    const IIndexschemaInspector &oldIndexschemaInspector,
                    const IDocumentTypeInspector &inspector,
                    AttributesConfigBuilder &attributesConfig,
                    SummarymapConfigBuilder &summarymapConfig)
{
    vespalib::hash_set<vespalib::string> delayed;
    KnownSummaryFields knownSummaryFields(newSummaryConfig);
    AttributesConfigHash newAttrs(newAttributesConfig.attribute);
    for (const auto &oldAttr : oldAttributesConfig.attribute) {
        search::attribute::Config oldCfg = ConfigConverter::convert(oldAttr);
        if (inspector.hasUnchangedField(oldAttr.name)) {
            auto newAttr = newAttrs.lookup(oldAttr.name);
            if (newAttr == nullptr) {
                // Delay removal of attribute aspect if it would trigger
                // reprocessing.
                if (willTriggerReprocessOnAttributeAspectRemoval(oldCfg, oldIndexschemaInspector, oldAttr.name)) {
                    attributesConfig.attribute.emplace_back(oldAttr);
                    delayed.insert(oldAttr.name);
                }
            }
        }
    }
    for (const auto &override : oldSummarymapConfig.override) {
        if (override.command == "attribute") {
            auto itr = delayed.find(source_field(override));
            if (itr != delayed.end() && knownSummaryFields.known(override.field)) {
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
                             const SummaryConfig &newSummaryConfig,
                             const SummarymapConfig &newSummarymapConfig,
                             const IIndexschemaInspector &oldIndexschemaInspector,
                             const IDocumentTypeInspector &inspector)
{
    handleNewAttributes(oldAttributesConfig, newAttributesConfig,
                        newSummarymapConfig,
                        oldIndexschemaInspector, inspector,
                        *_attributesConfig, *_summarymapConfig);
    handleOldAttributes(oldAttributesConfig, newAttributesConfig,
                        oldSummarymapConfig, newSummaryConfig,
                        oldIndexschemaInspector, inspector,
                        *_attributesConfig, *_summarymapConfig);
}

} // namespace proton
