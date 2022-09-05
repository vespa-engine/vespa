// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_aspect_delayer.h"
#include <vespa/config-attributes.h>
#include <vespa/config-summary.h>
#include <vespa/config-summarymap.h>
#include <vespa/searchcommon/attribute/attribute_utils.h>
#include <vespa/searchcore/proton/common/config_hash.hpp>
#include <vespa/searchcore/proton/common/i_document_type_inspector.h>
#include <vespa/searchcore/proton/common/i_indexschema_inspector.h>
#include <vespa/searchlib/attribute/configconverter.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/stllike/hash_set.hpp>

using search::attribute::isUpdateableInMemoryOnly;
using search::attribute::BasicType;
using search::attribute::ConfigConverter;
using vespa::config::search::AttributesConfig;
using vespa::config::search::AttributesConfigBuilder;
using vespa::config::search::SummaryConfig;
using vespa::config::search::SummaryConfigBuilder;
using vespa::config::search::SummarymapConfig;
using vespa::config::search::SummarymapConfigBuilder;

namespace proton {

namespace {

vespalib::string attribute_combiner_dfw_string("attributecombiner");
vespalib::string matched_attribute_elements_filter_dfw_string("matchedattributeelementsfilter");
vespalib::string matched_elements_filter_dfw_string("matchedelementsfilter");
vespalib::string copy_dfw_string("copy");
vespalib::string attribute_dfw_string("attribute");

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

vespalib::string
source_field(const SummaryConfig::Classes::Fields& summary_field)
{
    if (summary_field.source == "") {
        return summary_field.name;
    } else {
        return summary_field.source;
    }
}

void
remove_docsum_field_rewriter(SummaryConfig::Classes::Fields& summary_field)
{
    if (source_field(summary_field) != summary_field.name) {
        summary_field.command = copy_dfw_string;
    } else {
        summary_field.command = "";
        summary_field.source = "";
    }
}

class AttributeAspectConfigRewriter
{
    const AttributesConfig&       _old_attributes_config;
    const AttributesConfig&       _new_attributes_config;
    AttributesConfigHash          _old_attributes_config_hash;
    AttributesConfigHash          _new_attributes_config_hash;
    const IIndexschemaInspector&  _old_index_schema_inspector;
    const IDocumentTypeInspector& _inspector;
    vespalib::hash_set<vespalib::string> _delayed_add_attribute_aspect;
    vespalib::hash_set<vespalib::string> _delayed_add_attribute_aspect_struct;
    vespalib::hash_set<vespalib::string> _delayed_remove_attribute_aspect;

    bool has_unchanged_field(const vespalib::string& name) const;
    bool should_delay_add_attribute_aspect(const vespalib::string& name) const;
    bool should_delay_remove_attribute_aspect(const vespalib::string& name) const;
    bool calculate_fast_access(const AttributesConfig::Attribute& new_attribute_config) const;
    void mark_delayed_add_attribute_aspect(const vespalib::string& name) { _delayed_add_attribute_aspect.insert(name); }
    bool is_delayed_add_attribute_aspect(const vespalib::string& name) const noexcept { return _delayed_add_attribute_aspect.find(name) != _delayed_add_attribute_aspect.end(); }
    void mark_delayed_add_attribute_aspect_struct(const vespalib::string& name) { _delayed_add_attribute_aspect_struct.insert(name); }
    bool is_delayed_add_attribute_aspect_struct(const vespalib::string& name) const noexcept { return _delayed_add_attribute_aspect_struct.find(name) != _delayed_add_attribute_aspect_struct.end(); }
    void mark_delayed_remove_attribute_aspect(const vespalib::string& name) { _delayed_remove_attribute_aspect.insert(name); }
    bool is_delayed_remove_attribute_aspect(const vespalib::string& name) const noexcept { return _delayed_remove_attribute_aspect.find(name) != _delayed_remove_attribute_aspect.end(); }
public:
    AttributeAspectConfigRewriter(const AttributesConfig& old_attributes_config,
                                  const AttributesConfig& new_attributes_config,
                                  const IIndexschemaInspector& old_index_schema_inspector,
                                  const IDocumentTypeInspector& inspector);
    ~AttributeAspectConfigRewriter();
    void calculate_delayed_attribute_aspects();
    void build_attributes_config(AttributesConfigBuilder& attributes_config_builder) const;
    void build_summary_map_config(const SummarymapConfig& old_summarymap_config,
                                  const SummarymapConfig& new_summarymap_config,
                                  const SummaryConfig& new_summary_config,
                                  SummarymapConfigBuilder& summary_map_config_builder) const;
    void build_summary_config(const SummaryConfig& new_summary_config,
                              SummaryConfigBuilder& summary_config_builder) const;
};

AttributeAspectConfigRewriter::AttributeAspectConfigRewriter(const AttributesConfig& old_attributes_config,
                                                             const AttributesConfig& new_attributes_config,
                                                             const IIndexschemaInspector& old_index_schema_inspector,
                                                             const IDocumentTypeInspector& inspector)
    : _old_attributes_config(old_attributes_config),
      _new_attributes_config(new_attributes_config),
      _old_attributes_config_hash(old_attributes_config.attribute),
      _new_attributes_config_hash(new_attributes_config.attribute),
      _old_index_schema_inspector(old_index_schema_inspector),
      _inspector(inspector),
      _delayed_add_attribute_aspect(),
      _delayed_add_attribute_aspect_struct(),
      _delayed_remove_attribute_aspect()
{
    calculate_delayed_attribute_aspects();
}

AttributeAspectConfigRewriter::~AttributeAspectConfigRewriter() = default;

bool
AttributeAspectConfigRewriter::has_unchanged_field(const vespalib::string& name) const
{
    return _inspector.hasUnchangedField(name);
}

bool
AttributeAspectConfigRewriter::should_delay_add_attribute_aspect(const vespalib::string& name) const
{
    if (!has_unchanged_field(name)) {
        // No reprocessing due to field type/presence change, just use new config
        return false;
    }
    auto old_attribute_config = _old_attributes_config_hash.lookup(name);
    if (old_attribute_config != nullptr) {
        return false; // Already added for ready subdb
    }
    auto new_attribute_config = _new_attributes_config_hash.lookup(name);
    if (new_attribute_config == nullptr) {
        return false; // Not added for any subdb
    }
    // Delay addition of attribute aspect since it would trigger reprocessing.
    return true;
}

bool
AttributeAspectConfigRewriter::should_delay_remove_attribute_aspect(const vespalib::string& name) const
{
    if (!has_unchanged_field(name)) {
        // No reprocessing due to field type/presence change, just use new config
        return false;
    }
    auto old_attribute_config = _old_attributes_config_hash.lookup(name);
    if (old_attribute_config == nullptr) {
        return false; // Already removed in all subdbs
    }
    auto new_attribute_config = _new_attributes_config_hash.lookup(name);
    if (new_attribute_config != nullptr) {
        return false; // Not removed for ready subdb
    }
    // Delay removal of attribute aspect if it would trigger reprocessing.
    auto old_cfg = ConfigConverter::convert(*old_attribute_config);
    return willTriggerReprocessOnAttributeAspectRemoval(old_cfg, _old_index_schema_inspector, name);
}

bool
AttributeAspectConfigRewriter::calculate_fast_access(const AttributesConfig::Attribute& new_attribute_config) const
{
    auto& name = new_attribute_config.name;
    if (!has_unchanged_field(name)) {
        // No reprocessing due to field type/presence change, just use new config
        return new_attribute_config.fastaccess;
    }
    auto old_attribute_config = _old_attributes_config_hash.lookup(name);
    assert(old_attribute_config != nullptr);
    auto old_cfg = ConfigConverter::convert(*old_attribute_config);
    if (!old_attribute_config->fastaccess || willTriggerReprocessOnAttributeAspectRemoval(old_cfg, _old_index_schema_inspector, name)) {
        // Delay change of fast access flag
        return old_attribute_config->fastaccess;
    } else {
        // Don't delay change of fast access flag from true to
        // false when removing attribute aspect in a way that
        // doesn't trigger reprocessing.
        return new_attribute_config.fastaccess;
    }
}

void
AttributeAspectConfigRewriter::calculate_delayed_attribute_aspects()
{
    for (const auto &newAttr : _new_attributes_config.attribute) {
        if (should_delay_add_attribute_aspect(newAttr.name)) {
            mark_delayed_add_attribute_aspect(newAttr.name);
            auto pos = newAttr.name.find('.');
            if (pos != vespalib::string::npos) {
                mark_delayed_add_attribute_aspect_struct(newAttr.name.substr(0, pos));
            }
        }
    }
    for (const auto &oldAttr : _old_attributes_config.attribute) {
        if (should_delay_remove_attribute_aspect(oldAttr.name)) {
            mark_delayed_remove_attribute_aspect(oldAttr.name);
        }
    }
}

void
AttributeAspectConfigRewriter::build_attributes_config(AttributesConfigBuilder& attributes_config_builder) const
{
    for (const auto &newAttr : _new_attributes_config.attribute) {
        if (is_delayed_add_attribute_aspect(newAttr.name)) {
            // Delay addition of attribute aspect
        } else {
            attributes_config_builder.attribute.emplace_back(newAttr);
            attributes_config_builder.attribute.back().fastaccess = calculate_fast_access(newAttr);
        }
    }
    for (const auto &oldAttr : _old_attributes_config.attribute) {
        if (is_delayed_remove_attribute_aspect(oldAttr.name)) {
            // Delay removal of attribute aspect
            attributes_config_builder.attribute.emplace_back(oldAttr);
        }
    }
}

void
AttributeAspectConfigRewriter::build_summary_map_config(const SummarymapConfig& old_summarymap_config,
                                                        const SummarymapConfig& new_summarymap_config,
                                                        const SummaryConfig& new_summary_config,
                                                        SummarymapConfigBuilder& summarymap_config_builder) const
{
    KnownSummaryFields knownSummaryFields(new_summary_config);
    for (const auto &override : new_summarymap_config.override) {
        if (override.command == attribute_dfw_string) {
            if (!is_delayed_add_attribute_aspect(source_field(override))) {
                summarymap_config_builder.override.emplace_back(override);
            }
        } else if (override.command == attribute_combiner_dfw_string) {
            if (!is_delayed_add_attribute_aspect_struct(source_field(override))) {
                summarymap_config_builder.override.emplace_back(override);
            }
        } else if (override.command == matched_attribute_elements_filter_dfw_string) {
            if (!is_delayed_add_attribute_aspect_struct(source_field(override))) {
                summarymap_config_builder.override.emplace_back(override);
            } else {
                SummarymapConfig::Override mutated_override(override);
                mutated_override.command = matched_elements_filter_dfw_string;
                summarymap_config_builder.override.emplace_back(mutated_override);
            }
        } else {
            summarymap_config_builder.override.emplace_back(override);
        }
    }
    for (const auto &override : old_summarymap_config.override) {
        if (override.command == attribute_dfw_string) {
            if (is_delayed_remove_attribute_aspect(source_field(override)) && knownSummaryFields.known(override.field)) {
                summarymap_config_builder.override.emplace_back(override);
            }
        }
    }
}

void
AttributeAspectConfigRewriter::build_summary_config(const SummaryConfig& new_summary_config,
                                                    SummaryConfigBuilder& summary_config_builder) const
{
    summary_config_builder = new_summary_config;
    for (auto &summary_class : summary_config_builder.classes) {
        for (auto &summary_field : summary_class.fields) {
            if (summary_field.command == attribute_dfw_string) {
                if (is_delayed_add_attribute_aspect(source_field(summary_field))) {
                    remove_docsum_field_rewriter(summary_field);
                }
            } else if (summary_field.command == attribute_combiner_dfw_string) {
                if (is_delayed_add_attribute_aspect_struct(source_field(summary_field))) {
                    remove_docsum_field_rewriter(summary_field);
                }
            } else if (summary_field.command == matched_attribute_elements_filter_dfw_string) {
                if (is_delayed_add_attribute_aspect_struct(source_field(summary_field)) ||
                    is_delayed_add_attribute_aspect(source_field(summary_field))) {
                    summary_field.command = matched_elements_filter_dfw_string;
                }
            } else if (summary_field.command == matched_elements_filter_dfw_string) {
                if (is_delayed_remove_attribute_aspect(source_field(summary_field))) {
                    summary_field.command = matched_attribute_elements_filter_dfw_string;
                }
            } else if (summary_field.command == "") {
                if (is_delayed_remove_attribute_aspect(summary_field.name)) {
                    summary_field.command = attribute_dfw_string;
                    summary_field.source = summary_field.name;
                }
            } else if (summary_field.command == copy_dfw_string) {
                if (is_delayed_remove_attribute_aspect(source_field(summary_field))) {
                    summary_field.command = attribute_dfw_string;
                    summary_field.source = source_field(summary_field);
                }
            }
        }
    }
}

}

AttributeAspectDelayer::AttributeAspectDelayer()
    : _attributesConfig(std::make_shared<AttributesConfigBuilder>()),
      _summarymapConfig(std::make_shared<SummarymapConfigBuilder>()),
      _summaryConfig(std::make_shared<SummaryConfigBuilder>())
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

std::shared_ptr<AttributeAspectDelayer::SummaryConfig>
AttributeAspectDelayer::getSummaryConfig() const
{
    return _summaryConfig;
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
    AttributeAspectConfigRewriter cfg_rewriter(oldAttributesConfig,
                                               newAttributesConfig,
                                               oldIndexschemaInspector,
                                               inspector);
    cfg_rewriter.build_attributes_config(*_attributesConfig);
    cfg_rewriter.build_summary_map_config(oldSummarymapConfig,
                                          newSummarymapConfig,
                                          newSummaryConfig,
                                          *_summarymapConfig);
    cfg_rewriter.build_summary_config(newSummaryConfig,
                                      *_summaryConfig);
}

} // namespace proton
