// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config-attributes.h>
#include <vespa/config-indexschema.h>
#include <vespa/config-summary.h>
#include <vespa/searchcore/proton/attribute/attribute_aspect_delayer.h>
#include <vespa/searchcore/proton/common/i_document_type_inspector.h>
#include <vespa/searchcore/proton/common/indexschema_inspector.h>
#include <vespa/searchcore/proton/test/attribute_utils.h>
#include <vespa/searchsummary/docsummary/docsum_field_writer_commands.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <string>

#include <vespa/log/log.h>
LOG_SETUP("attribute_aspect_delayer_test");

using search::attribute::Config;
using vespa::config::search::AttributesConfig;
using vespa::config::search::AttributesConfigBuilder;
using vespa::config::search::IndexschemaConfig;
using vespa::config::search::IndexschemaConfigBuilder;
using vespa::config::search::SummaryConfig;
using vespa::config::search::SummaryConfigBuilder;

using namespace search::docsummary;

namespace vespa::config::search::internal {

std::ostream& operator<<(std::ostream& os, const SummaryConfig::Classes::Fields& field) {
    return os << "{name=" << field.name << ", command=" << field.command << ", source=" << field.source << "}";
}

} // namespace vespa::config::search::internal

namespace proton {

namespace {

AttributesConfig::Attribute make_sv_cfg(const std::string& name, AttributesConfig::Attribute::Datatype dataType) {
    AttributesConfig::Attribute attr;
    attr.name = name;
    attr.datatype = dataType;
    attr.collectiontype = AttributesConfig::Attribute::Collectiontype::SINGLE;
    return attr;
}

AttributesConfig::Attribute make_sv_cfg(AttributesConfig::Attribute::Datatype dataType) {
    return make_sv_cfg("a", dataType);
}

AttributesConfig::Attribute make_int32_sv_cfg(const std::string& name) {
    return make_sv_cfg(name, AttributesConfig::Attribute::Datatype::INT32);
}

AttributesConfig::Attribute make_int32_sv_cfg() {
    return make_sv_cfg(AttributesConfig::Attribute::Datatype::INT32);
}

AttributesConfig::Attribute make_string_sv_cfg() {
    return make_sv_cfg(AttributesConfig::Attribute::Datatype::STRING);
}

AttributesConfig::Attribute make_predicate_cfg(uint32_t arity) {
    auto attr = make_sv_cfg(AttributesConfig::Attribute::Datatype::PREDICATE);
    attr.arity = arity;
    return attr;
}

AttributesConfig::Attribute make_tensor_cfg(const std::string& spec) {
    auto attr = make_sv_cfg(AttributesConfig::Attribute::Datatype::TENSOR);
    attr.tensortype = spec;
    return attr;
}

AttributesConfig::Attribute make_reference_cfg() {
    return make_sv_cfg(AttributesConfig::Attribute::Datatype::REFERENCE);
}

AttributesConfig attrCfg(std::vector<AttributesConfig::Attribute> attributes) {
    AttributesConfigBuilder result;
    result.attribute = attributes;
    return result;
}

AttributesConfig::FastMapLookups make_fast_map_lookup(const std::string& field_name, const std::string& lookup_name) {
    AttributesConfig::FastMapLookups lookup;
    lookup.fieldName = field_name;
    lookup.lookupName = lookup_name;
    lookup.keyType = "string";
    lookup.valueType = "int";
    return lookup;
}

AttributesConfig attrCfg(std::vector<AttributesConfig::Attribute>       attributes,
                         std::vector<AttributesConfig::FastMapLookups> fast_map_lookups) {
    AttributesConfigBuilder result;
    result.attribute = std::move(attributes);
    result.fastMapLookups = std::move(fast_map_lookups);
    return result;
}

AttributesConfig::Attribute make_fa(const AttributesConfig::Attribute& cfg) {
    AttributesConfig::Attribute attr(cfg);
    attr.fastaccess = true;
    return attr;
}

SummaryConfig::Classes::Fields make_summary_field(const std::string& name) {
    SummaryConfig::Classes::Fields field;
    field.name = name;
    return field;
}

SummaryConfig::Classes::Fields make_summary_field(const std::string& name, const std::string& command,
                                                  const std::string& source) {
    SummaryConfig::Classes::Fields field;
    field.name = name;
    field.command = command;
    field.source = source;
    return field;
}

SummaryConfig sCfg(std::vector<SummaryConfig::Classes::Fields> fields) {
    SummaryConfigBuilder result;
    result.classes.resize(1);
    result.classes.back().id = 0;
    result.classes.back().name = "default";
    result.classes.back().fields = std::move(fields);
    return result;
}

class MyInspector : public IDocumentTypeInspector {
    std::set<std::string> _unchanged;

public:
    bool hasUnchangedField(const std::string& name) const override { return _unchanged.count(name) > 0; }
    MyInspector() : _unchanged() {}
    ~MyInspector() override;
    void addFields(const std::vector<std::string>& fields) {
        for (const auto& field : fields) {
            _unchanged.insert(field);
        }
    }
};

MyInspector::~MyInspector() = default;

} // namespace

class DelayerTest : public ::testing::Test {
private:
    MyInspector              _inspector;
    IndexschemaConfigBuilder _oldIndexSchema;
    AttributeAspectDelayer   _delayer;

public:
    DelayerTest() : _inspector(), _delayer() {}
    ~DelayerTest() override;
    void addFields(const std::vector<std::string>& fields) { _inspector.addFields(fields); }
    void addOldIndexField(const std::string& name) {
        IndexschemaConfig::Indexfield field;
        field.name = name;
        _oldIndexSchema.indexfield.emplace_back(field);
    }
    void setup(const AttributesConfig& oldAttributesConfig, const AttributesConfig& newAttributesConfig,
               const SummaryConfig& newSummaryConfig) {
        IndexschemaInspector indexschemaInspector(_oldIndexSchema);
        _delayer.setup(oldAttributesConfig, newAttributesConfig, newSummaryConfig, indexschemaInspector, _inspector);
    }
    void assertAttributeConfig(const std::vector<AttributesConfig::Attribute>& exp) {
        auto actConfig = _delayer.getAttributesConfig();
        EXPECT_EQ(exp, actConfig->attribute);
    }
    void assertFastMapLookups(const std::vector<AttributesConfig::FastMapLookups>& exp) {
        auto actConfig = _delayer.getAttributesConfig();
        EXPECT_EQ(exp, actConfig->fastMapLookups);
    }
    void assertSummaryConfig(const std::vector<SummaryConfig::Classes::Fields>& exp) {
        auto summaryConfig = _delayer.getSummaryConfig();
        ASSERT_EQ(1, summaryConfig->classes.size());
        EXPECT_EQ(exp, summaryConfig->classes[0].fields);
    }
};

DelayerTest::~DelayerTest() = default;

TEST_F(DelayerTest, require_that_empty_config_is_ok) {
    setup(attrCfg({}), attrCfg({}), sCfg({}));
    assertAttributeConfig({});
    assertSummaryConfig({});
}

TEST_F(DelayerTest, require_that_simple_attribute_config_is_ok) {
    setup(attrCfg({make_int32_sv_cfg()}), attrCfg({make_int32_sv_cfg()}),
          sCfg({make_summary_field("a", command::attribute, "a")}));
    assertAttributeConfig({make_int32_sv_cfg()});
    assertSummaryConfig({make_summary_field("a", command::attribute, "a")});
}

TEST_F(DelayerTest, require_that_adding_attribute_aspect_is_delayed_if_field_type_is_unchanged) {
    addFields({"a"});
    setup(attrCfg({}), attrCfg({make_int32_sv_cfg()}), sCfg({make_summary_field("a", command::attribute, "a")}));
    assertAttributeConfig({});
    assertSummaryConfig({make_summary_field("a")});
}

TEST_F(DelayerTest, require_that_adding_attribute_aspect_is_delayed_if_field_type_is_unchanged_geopos_override) {
    addFields({"a"});
    setup(attrCfg({}), attrCfg({make_int32_sv_cfg()}), sCfg({make_summary_field("a", command::geo_position, "a")}));
    assertAttributeConfig({});
    assertSummaryConfig({make_summary_field("a", command::geo_position, "a")});
}

TEST_F(DelayerTest, require_that_adding_attribute_aspect_is_delayed_if_field_type_is_unchanged_mapped_summary) {
    addFields({"a"});
    setup(attrCfg({}), attrCfg({make_int32_sv_cfg()}),
          sCfg({make_summary_field("a_mapped", command::attribute, "a")}));
    assertAttributeConfig({});
    assertSummaryConfig({make_summary_field("a_mapped", command::copy, "a")});
}

TEST_F(DelayerTest, require_that_adding_attribute_is_not_delayed_if_field_type_changed) {
    setup(attrCfg({}), attrCfg({make_int32_sv_cfg()}), sCfg({make_summary_field("a", command::attribute, "a")}));
    assertAttributeConfig({make_int32_sv_cfg()});
    assertSummaryConfig({make_summary_field("a", command::attribute, "a")});
}

TEST_F(DelayerTest, require_that_removing_attribute_aspect_is_delayed_if_field_type_is_unchanged) {
    addFields({"a"});
    setup(attrCfg({make_int32_sv_cfg()}), attrCfg({}), sCfg({make_summary_field("a")}));
    assertAttributeConfig({make_int32_sv_cfg()});
    assertSummaryConfig({make_summary_field("a", command::attribute, "a")});
}

TEST_F(
    DelayerTest,
    require_that_summary_map_override_is_removed_when_summary_aspect_is_removed_even_if_removing_attribute_aspect_is_delayed) {
    addFields({"a"});
    setup(attrCfg({make_int32_sv_cfg()}), attrCfg({}), sCfg({}));
    assertAttributeConfig({make_int32_sv_cfg()});
    assertSummaryConfig({});
}

TEST_F(DelayerTest, require_that_removing_attribute_aspect_is_delayed_if_field_type_is_unchanged_gepos_override) {
    addFields({"a"});
    setup(attrCfg({make_int32_sv_cfg()}), attrCfg({}), sCfg({}));
    assertAttributeConfig({make_int32_sv_cfg()});
    assertSummaryConfig({});
}

TEST_F(DelayerTest, require_that_removing_attribute_aspect_is_not_delayed_if_field_type_changed) {
    setup(attrCfg({make_int32_sv_cfg()}), attrCfg({}), sCfg({make_summary_field("a")}));
    assertAttributeConfig({});
    assertSummaryConfig({make_summary_field("a")});
}

TEST_F(DelayerTest, require_that_removing_attribute_aspect_is_not_delayed_if_also_indexed) {
    addFields({"a"});
    addOldIndexField("a");
    setup(attrCfg({make_string_sv_cfg()}), attrCfg({}), sCfg({make_summary_field("a")}));
    assertAttributeConfig({});
    assertSummaryConfig({make_summary_field("a")});
}

TEST_F(DelayerTest, require_that_adding_attribute_aspect_is_delayed_for_tensor_field) {
    addFields({"a"});
    setup(attrCfg({}), attrCfg({make_tensor_cfg("tensor(x[10])")}),
          sCfg({make_summary_field("a", command::attribute, "a")}));
    assertAttributeConfig({});
    assertSummaryConfig({make_summary_field("a")});
}

TEST_F(DelayerTest, require_that_removing_attribute_aspect_is_delayed_for_tensor_field) {
    addFields({"a"});
    setup(attrCfg({make_tensor_cfg("tensor(x[10])")}), attrCfg({}), sCfg({make_summary_field("a")}));
    assertAttributeConfig({make_tensor_cfg("tensor(x[10])")});
    assertSummaryConfig({make_summary_field("a", command::attribute, "a")});
}

TEST_F(DelayerTest, require_that_removing_attribute_aspect_is_not_delayed_for_predicate) {
    addFields({"a"});
    setup(attrCfg({make_predicate_cfg(4)}), attrCfg({}), sCfg({make_summary_field("a")}));
    assertAttributeConfig({});
    assertSummaryConfig({make_summary_field("a")});
}

TEST_F(DelayerTest, require_that_removing_attribute_aspect_is_not_delayed_for_reference) {
    addFields({"a"});
    setup(attrCfg({make_reference_cfg()}), attrCfg({}), sCfg({make_summary_field("a")}));
    assertAttributeConfig({});
    assertSummaryConfig({make_summary_field("a")});
}

TEST_F(DelayerTest, require_that_fast_access_flag_change_is_delayed_false_true_edge) {
    addFields({"a"});
    setup(attrCfg({make_int32_sv_cfg()}), attrCfg({make_fa(make_int32_sv_cfg())}),
          sCfg({make_summary_field("a", command::attribute, "a")}));
    assertAttributeConfig({make_int32_sv_cfg()});
    assertSummaryConfig({make_summary_field("a", command::attribute, "a")});
}

TEST_F(DelayerTest, require_that_fast_access_flag_change_is_delayed_true_false_edge) {
    addFields({"a"});
    setup(attrCfg({make_fa(make_int32_sv_cfg())}), attrCfg({make_int32_sv_cfg()}),
          sCfg({make_summary_field("a", command::attribute, "a")}));
    assertAttributeConfig({make_fa(make_int32_sv_cfg())});
    assertSummaryConfig({make_summary_field("a", command::attribute, "a")});
}

TEST_F(DelayerTest, require_that_fast_access_flag_change_is_delayed_false_true_edge_on_tensor_attribute) {
    addFields({"a"});
    setup(attrCfg({make_tensor_cfg("tensor(x[10])")}), attrCfg({make_fa(make_tensor_cfg("tensor(x[10])"))}),
          sCfg({make_summary_field("a", command::attribute, "a")}));
    assertAttributeConfig({make_tensor_cfg("tensor(x[10])")});
    assertSummaryConfig({make_summary_field("a", command::attribute, "a")});
}

TEST_F(DelayerTest, require_that_fast_access_flag_change_is_delayed_true_false_edge_on_tensor_attribute) {
    addFields({"a"});
    setup(attrCfg({make_fa(make_tensor_cfg("tensor(x[10])"))}), attrCfg({make_tensor_cfg("tensor(x[10])")}),
          sCfg({make_summary_field("a", command::attribute, "a")}));
    assertAttributeConfig({make_fa(make_tensor_cfg("tensor(x[10])"))});
    assertSummaryConfig({make_summary_field("a", command::attribute, "a")});
}

TEST_F(DelayerTest,
       require_that_fast_access_flag_change_is_not_delayed_true_false_edge_on_string_attribute_indexed_field) {
    addFields({"a"});
    addOldIndexField("a");
    setup(attrCfg({make_fa(make_string_sv_cfg())}), attrCfg({make_string_sv_cfg()}),
          sCfg({make_summary_field("a", command::attribute, "a")}));
    assertAttributeConfig({make_string_sv_cfg()});
    assertSummaryConfig({make_summary_field("a", command::attribute, "a")});
}

TEST_F(DelayerTest, require_that_adding_attribute_aspect_to_struct_field_is_not_delayed_if_field_type_is_changed) {
    setup(attrCfg({}), attrCfg({make_int32_sv_cfg("array.a")}),
          sCfg({make_summary_field("array", command::attribute_combiner, "array")}));
    assertAttributeConfig({make_int32_sv_cfg("array.a")});
    assertSummaryConfig({make_summary_field("array", command::attribute_combiner, "array")});
}

TEST_F(DelayerTest, require_that_adding_attribute_aspect_to_struct_field_is_delayed_if_field_type_is_unchanged) {
    addFields({"array.a"});
    setup(attrCfg({}), attrCfg({make_int32_sv_cfg("array.a")}),
          sCfg({make_summary_field("array", command::attribute_combiner, "array")}));
    assertAttributeConfig({});
    assertSummaryConfig({make_summary_field("array")});
}

TEST_F(DelayerTest, require_that_removing_attribute_aspect_from_struct_field_is_not_delayed) {
    addFields({"array.a"});
    setup(attrCfg({make_int32_sv_cfg("array.a")}), attrCfg({}), sCfg({make_summary_field("array")}));
    assertAttributeConfig({});
    assertSummaryConfig({make_summary_field("array")});
}

TEST_F(DelayerTest, require_that_fast_map_lookups_are_kept_when_lookup_attribute_is_present) {
    auto lookup = make_fast_map_lookup("m", "lookup");
    setup(attrCfg({make_int32_sv_cfg("m$lookup")}, {lookup}), attrCfg({make_int32_sv_cfg("m$lookup")}, {lookup}),
          sCfg({}));
    assertAttributeConfig({make_int32_sv_cfg("m$lookup")});
    assertFastMapLookups({lookup});
}

TEST_F(DelayerTest, require_that_fast_map_lookup_is_dropped_when_adding_lookup_attribute_is_delayed) {
    addFields({"m$lookup"});
    setup(attrCfg({}), attrCfg({make_int32_sv_cfg("m$lookup")}, {make_fast_map_lookup("m", "lookup")}), sCfg({}));
    assertAttributeConfig({});
    assertFastMapLookups({});
}

TEST_F(DelayerTest, require_that_fast_map_lookup_is_kept_when_removing_lookup_attribute_is_delayed) {
    addFields({"m$lookup"});
    auto lookup = make_fast_map_lookup("m", "lookup");
    setup(attrCfg({make_int32_sv_cfg("m$lookup")}, {lookup}), attrCfg({}), sCfg({}));
    assertAttributeConfig({make_int32_sv_cfg("m$lookup")});
    assertFastMapLookups({lookup});
}

TEST_F(DelayerTest, require_that_fast_map_lookup_is_not_repeated_when_in_both_old_and_new_config) {
    auto old_lookup = make_fast_map_lookup("m", "lookup");
    auto new_lookup = make_fast_map_lookup("m", "lookup");
    new_lookup.valueType = "long";
    setup(attrCfg({make_int32_sv_cfg("m$lookup")}, {old_lookup}),
          attrCfg({make_int32_sv_cfg("m$lookup")}, {new_lookup}), sCfg({}));
    assertFastMapLookups({new_lookup});
}

} // namespace proton

GTEST_MAIN_RUN_ALL_TESTS()
