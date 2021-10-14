// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config-attributes.h>
#include <vespa/config-indexschema.h>
#include <vespa/config-summary.h>
#include <vespa/config-summarymap.h>
#include <vespa/searchcore/proton/attribute/attribute_aspect_delayer.h>
#include <vespa/searchcore/proton/common/i_document_type_inspector.h>
#include <vespa/searchcore/proton/common/indexschema_inspector.h>
#include <vespa/searchcore/proton/test/attribute_utils.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/test/insertion_operators.h>

#include <vespa/log/log.h>
LOG_SETUP("attibute_aspect_delayer_test");

using vespa::config::search::AttributesConfig;
using vespa::config::search::AttributesConfigBuilder;
using vespa::config::search::IndexschemaConfig;
using vespa::config::search::IndexschemaConfigBuilder;
using vespa::config::search::SummaryConfig;
using vespa::config::search::SummaryConfigBuilder;
using vespa::config::search::SummarymapConfig;
using vespa::config::search::SummarymapConfigBuilder;
using search::attribute::Config;
using search::attribute::BasicType;
using search::attribute::CollectionType;

namespace std
{

ostream &operator<<(ostream &os, const SummarymapConfig::Override &override)
{
    os << "{field=" << override.field << ", command=" << override.command << ", arguments=" << override.arguments << "}";
    return os;
}

}

namespace proton {

namespace {

AttributesConfig::Attribute make_sv_cfg(const vespalib::string &name, AttributesConfig::Attribute::Datatype dataType)
{
    AttributesConfig::Attribute attr;
    attr.name = name;
    attr.datatype = dataType;
    attr.collectiontype = AttributesConfig::Attribute::Collectiontype::SINGLE;
    return attr;
}

AttributesConfig::Attribute make_sv_cfg(AttributesConfig::Attribute::Datatype dataType)
{
    return make_sv_cfg("a", dataType);
}

AttributesConfig::Attribute make_int32_sv_cfg(const vespalib::string &name) {
    return make_sv_cfg(name, AttributesConfig::Attribute::Datatype::INT32);
}

AttributesConfig::Attribute make_int32_sv_cfg() {
    return make_sv_cfg(AttributesConfig::Attribute::Datatype::INT32);
}

AttributesConfig::Attribute make_string_sv_cfg() {
    return make_sv_cfg(AttributesConfig::Attribute::Datatype::STRING);
}

AttributesConfig::Attribute make_predicate_cfg(uint32_t arity)
{
    auto attr = make_sv_cfg(AttributesConfig::Attribute::Datatype::PREDICATE);
    attr.arity = arity;
    return attr;
}

AttributesConfig::Attribute make_tensor_cfg(const vespalib::string &spec)
{
    auto attr = make_sv_cfg(AttributesConfig::Attribute::Datatype::TENSOR);
    attr.tensortype = spec;
    return attr;
}

AttributesConfig::Attribute make_reference_cfg()
{
    return make_sv_cfg(AttributesConfig::Attribute::Datatype::REFERENCE);
}

AttributesConfig attrCfg(std::vector<AttributesConfig::Attribute> attributes)
{
    AttributesConfigBuilder result;
    result.attribute = attributes;
    return result;
}

AttributesConfig::Attribute make_fa(const AttributesConfig::Attribute &cfg)
{
    AttributesConfig::Attribute attr(cfg);
    attr.fastaccess = true;
    return attr;
}

SummarymapConfig::Override make_attribute_override(const vespalib::string &name, const vespalib::string &source)
{
    SummarymapConfig::Override override;
    override.field = name;
    override.command = "attribute";
    override.arguments = source;
    return override;
}

SummarymapConfig::Override make_attribute_override(const vespalib::string &name)
{
    return make_attribute_override(name, name);
}

SummarymapConfig::Override make_geopos_override(const vespalib::string &name)
{
    SummarymapConfig::Override override;
    override.field = name;
    override.command = "geopos";
    override.arguments = name;
    return override;
}

SummarymapConfig::Override make_attribute_combiner_override(const vespalib::string &name, const vespalib::string &source)
{
    SummarymapConfig::Override override;
    override.field = name;
    override.command = "attributecombiner";
    override.arguments = source;
    return override;
}

SummarymapConfig::Override make_attribute_combiner_override(const vespalib::string &name)
{
    return make_attribute_combiner_override(name, "");
}

SummarymapConfig::Override make_matched_attribute_elements_filter_override(const vespalib::string &name, const vespalib::string &source)
{
    SummarymapConfig::Override override;
    override.field = name;
    override.command = "matchedattributeelementsfilter";
    override.arguments = source;
    return override;
}

SummarymapConfig::Override make_matched_elements_filter_override(const vespalib::string &name, const vespalib::string &source)
{
    SummarymapConfig::Override override;
    override.field = name;
    override.command = "matchedelementsfilter";
    override.arguments = source;
    return override;
}

SummaryConfig::Classes::Fields make_summary_field(const vespalib::string &name, const vespalib::string &type)
{
    SummaryConfig::Classes::Fields field;
    field.name = name;
    field.type = type;
    return field;
}

SummaryConfig sCfg(std::vector<SummaryConfig::Classes::Fields> fields)
{
    SummaryConfigBuilder result;
    result.classes.resize(1);
    result.classes.back().id = 0;
    result.classes.back().name = "default";
    result.classes.back().fields = std::move(fields);
    return result;
}

SummarymapConfig smCfg(std::vector<SummarymapConfig::Override> overrides)
{
    SummarymapConfigBuilder result;
    result.override = overrides;
    return result;
}

class MyInspector : public IDocumentTypeInspector
{
    std::set<vespalib::string> _unchanged;
public:
    virtual bool hasUnchangedField(const vespalib::string &name) const override {
        return _unchanged.count(name) > 0;
    }
    MyInspector()
        : _unchanged()
    {
    }
    ~MyInspector() { }
    void addFields(const std::vector<vespalib::string> &fields) {
        for (const auto &field : fields) {
            _unchanged.insert(field);
        }
    }
};

}

class DelayerTest : public ::testing::Test {
private:
    MyInspector _inspector;
    IndexschemaConfigBuilder _oldIndexSchema;
    AttributeAspectDelayer _delayer;

public:
    DelayerTest()
        : _inspector(),
          _delayer()
    {
    }
    ~DelayerTest() { }
    void addFields(const std::vector<vespalib::string> &fields) {
        _inspector.addFields(fields);
    }
    void addOldIndexField(const vespalib::string &name) {
        IndexschemaConfig::Indexfield field;
        field.name = name;
        _oldIndexSchema.indexfield.emplace_back(field);
    }
    void setup(const AttributesConfig &oldAttributesConfig, const SummarymapConfig &oldSummarymapConfig,
               const AttributesConfig &newAttributesConfig, const SummaryConfig &newSummaryConfig, const SummarymapConfig &newSummarymapConfig) {
        IndexschemaInspector indexschemaInspector(_oldIndexSchema);
        _delayer.setup(oldAttributesConfig, oldSummarymapConfig,
                       newAttributesConfig, newSummaryConfig, newSummarymapConfig,
                       indexschemaInspector, _inspector);
    }
    void assertAttributeConfig(const std::vector<AttributesConfig::Attribute> &exp)
    {
        auto actConfig = _delayer.getAttributesConfig();
        EXPECT_EQ(exp, actConfig->attribute);
    }
    void assertSummarymapConfig(const std::vector<SummarymapConfig::Override> &exp)
    {
        auto summarymapConfig = _delayer.getSummarymapConfig();
        EXPECT_EQ(exp, summarymapConfig->override);
    }
};

TEST_F(DelayerTest, require_that_empty_config_is_ok)
{
    setup(attrCfg({}), smCfg({}), attrCfg({}), sCfg({}), smCfg({}));
    assertAttributeConfig({});
    assertSummarymapConfig({});
}

TEST_F(DelayerTest, require_that_simple_attribute_config_is_ok)
{
    setup(attrCfg({make_int32_sv_cfg()}), smCfg({make_attribute_override("a")}), attrCfg({make_int32_sv_cfg()}), sCfg({make_summary_field("a", "integer")}), smCfg({make_attribute_override("a")}));
    assertAttributeConfig({make_int32_sv_cfg()});
    assertSummarymapConfig({make_attribute_override("a")});
}

TEST_F(DelayerTest, require_that_adding_attribute_aspect_is_delayed_if_field_type_is_unchanged)
{
    addFields({"a"});
    setup(attrCfg({}), smCfg({}), attrCfg({make_int32_sv_cfg()}), sCfg({make_summary_field("a", "integer")}), smCfg({make_attribute_override("a")}));
    assertAttributeConfig({});
    assertSummarymapConfig({});
}

TEST_F(DelayerTest, require_that_adding_attribute_aspect_is_delayed_if_field_type_is_unchanged_geopos_override)
{
    addFields({"a"});
    setup(attrCfg({}), smCfg({}), attrCfg({make_int32_sv_cfg()}), sCfg({make_summary_field("a", "integer")}), smCfg({make_geopos_override("a")}));
    assertAttributeConfig({});
    assertSummarymapConfig({make_geopos_override("a")});
}

TEST_F(DelayerTest, require_that_adding_attribute_aspect_is_delayed_if_field_type_is_unchanged_mapped_summary)
{
    addFields({"a"});
    setup(attrCfg({}), smCfg({}), attrCfg({make_int32_sv_cfg()}), sCfg({make_summary_field("a_mapped", "integer")}), smCfg({make_attribute_override("a_mapped", "a")}));
    assertAttributeConfig({});
    assertSummarymapConfig({});
}

TEST_F(DelayerTest, require_that_adding_attribute_is_not_delayed_if_field_type_changed)
{
    setup(attrCfg({}), smCfg({}), attrCfg({make_int32_sv_cfg()}), sCfg({make_summary_field("a", "integer")}), smCfg({make_attribute_override("a")}));
    assertAttributeConfig({make_int32_sv_cfg()});
    assertSummarymapConfig({make_attribute_override("a")});
}

TEST_F(DelayerTest, require_that_removing_attribute_aspect_is_delayed_if_field_type_is_unchanged)
{
    addFields({"a"});
    setup(attrCfg({make_int32_sv_cfg()}), smCfg({make_attribute_override("a")}), attrCfg({}), sCfg({make_summary_field("a", "integer")}), smCfg({}));
    assertAttributeConfig({make_int32_sv_cfg()});
    assertSummarymapConfig({make_attribute_override("a")});
}

TEST_F(DelayerTest, require_that_summary_map_override_is_removed_when_summary_aspect_is_removed_even_if_removing_attribute_aspect_is_delayed)
{
    addFields({"a"});
    setup(attrCfg({make_int32_sv_cfg()}), smCfg({make_attribute_override("a")}), attrCfg({}), sCfg({}), smCfg({}));
    assertAttributeConfig({make_int32_sv_cfg()});
    assertSummarymapConfig({});
}

TEST_F(DelayerTest, require_that_removing_attribute_aspect_is_delayed_if_field_type_is_unchanged_gepos_override)
{
    addFields({"a"});
    setup(attrCfg({make_int32_sv_cfg()}), smCfg({make_geopos_override("a")}), attrCfg({}), sCfg({}), smCfg({}));
    assertAttributeConfig({make_int32_sv_cfg()});
    assertSummarymapConfig({});
}

TEST_F(DelayerTest, require_that_removing_attribute_aspect_is_not_delayed_if_field_type_changed)
{
    setup(attrCfg({make_int32_sv_cfg()}), smCfg({make_attribute_override("a")}), attrCfg({}), sCfg({make_summary_field("a", "integer")}), smCfg({}));
    assertAttributeConfig({});
    assertSummarymapConfig({});
}

TEST_F(DelayerTest, require_that_removing_attribute_aspect_is_not_delayed_if_also_indexed)
{
    addFields({"a"});
    addOldIndexField("a");
    setup(attrCfg({make_string_sv_cfg()}), smCfg({make_attribute_override("a")}), attrCfg({}), sCfg({make_summary_field("a", "string")}), smCfg({}));
    assertAttributeConfig({});
    assertSummarymapConfig({});
}

TEST_F(DelayerTest, require_that_adding_attribute_aspect_is_delayed_for_tensor_field)
{
    addFields({"a"});
    setup(attrCfg({}), smCfg({}),
          attrCfg({make_tensor_cfg("tensor(x[10])")}), sCfg({make_summary_field("a", "tensor")}), smCfg({make_attribute_override("a")}));
    assertAttributeConfig({});
    assertSummarymapConfig({});
}

TEST_F(DelayerTest, require_that_removing_attribute_aspect_is_delayed_for_tensor_field)
{
    addFields({"a"});
    setup(attrCfg({make_tensor_cfg("tensor(x[10])")}), smCfg({make_attribute_override("a")}),
          attrCfg({}), sCfg({make_summary_field("a", "tensor")}), smCfg({}));
    assertAttributeConfig({make_tensor_cfg("tensor(x[10])")});
    assertSummarymapConfig({make_attribute_override("a")});
}

TEST_F(DelayerTest, require_that_removing_attribute_aspect_is_not_delayed_for_predicate)
{
    addFields({"a"});
    setup(attrCfg({make_predicate_cfg(4)}), smCfg({}), attrCfg({}), sCfg({make_summary_field("a", "string")}), smCfg({}));
    assertAttributeConfig({});
    assertSummarymapConfig({});
}

TEST_F(DelayerTest, require_that_removing_attribute_aspect_is_not_delayed_for_reference)
{
    addFields({"a"});
    setup(attrCfg({make_reference_cfg()}), smCfg({}), attrCfg({}), sCfg({make_summary_field("a", "longstring")}), smCfg({}));
    assertAttributeConfig({});
    assertSummarymapConfig({});
}

TEST_F(DelayerTest, require_that_fast_access_flag_change_is_delayed_false_true_edge)
{
    addFields({"a"});
    setup(attrCfg({make_int32_sv_cfg()}), smCfg({make_attribute_override("a")}), attrCfg({make_fa(make_int32_sv_cfg())}), sCfg({make_summary_field("a", "integer")}), smCfg({make_attribute_override("a")}));
    assertAttributeConfig({make_int32_sv_cfg()});
    assertSummarymapConfig({make_attribute_override("a")});
}

TEST_F(DelayerTest, require_that_fast_access_flag_change_is_delayed_true_false_edge)
{
    addFields({"a"});
    setup(attrCfg({make_fa(make_int32_sv_cfg())}), smCfg({make_attribute_override("a")}), attrCfg({make_int32_sv_cfg()}), sCfg({make_summary_field("a", "integer")}), smCfg({make_attribute_override("a")}));
    assertAttributeConfig({make_fa(make_int32_sv_cfg())});
    assertSummarymapConfig({make_attribute_override("a")});
}

TEST_F(DelayerTest, require_that_fast_access_flag_change_is_delayed_false_true_edge_on_tensor_attribute)
{
    addFields({"a"});
    setup(attrCfg({make_tensor_cfg("tensor(x[10])")}), smCfg({make_attribute_override("a")}),
          attrCfg({make_fa(make_tensor_cfg("tensor(x[10])"))}), sCfg({make_summary_field("a", "tensor")}), smCfg({make_attribute_override("a")}));
    assertAttributeConfig({make_tensor_cfg("tensor(x[10])")});
    assertSummarymapConfig({make_attribute_override("a")});
}

TEST_F(DelayerTest, require_that_fast_access_flag_change_is_delayed_true_false_edge_on_tensor_attribute)
{
    addFields({"a"});
    setup(attrCfg({make_fa(make_tensor_cfg("tensor(x[10])"))}), smCfg({make_attribute_override("a")}),
          attrCfg({make_tensor_cfg("tensor(x[10])")}), sCfg({make_summary_field("a", "tensor")}), smCfg({make_attribute_override("a")}));
    assertAttributeConfig({make_fa(make_tensor_cfg("tensor(x[10])"))});
    assertSummarymapConfig({make_attribute_override("a")});
}

TEST_F(DelayerTest, require_that_fast_access_flag_change_is_not_delayed_true_false_edge_on_string_attribute_indexed_field)
{
    addFields({"a"});
    addOldIndexField("a");
    setup(attrCfg({make_fa(make_string_sv_cfg())}), smCfg({make_attribute_override("a")}), attrCfg({make_string_sv_cfg()}), sCfg({make_summary_field("a", "tensor")}), smCfg({make_attribute_override("a")}));
    assertAttributeConfig({make_string_sv_cfg()});
    assertSummarymapConfig({make_attribute_override("a")});
}

TEST_F(DelayerTest, require_that_adding_attribute_aspect_to_struct_field_is_not_delayed_if_field_type_is_changed)
{
    setup(attrCfg({}), smCfg({}), attrCfg({make_int32_sv_cfg("array.a")}), sCfg({make_summary_field("array", "jsonstring")}), smCfg({make_attribute_combiner_override("array")}));
    assertAttributeConfig({make_int32_sv_cfg("array.a")});
    assertSummarymapConfig({make_attribute_combiner_override("array")});
}

TEST_F(DelayerTest, require_that_adding_attribute_aspect_to_struct_field_is_delayed_if_field_type_is_unchanged)
{
    addFields({"array.a"});
    setup(attrCfg({}), smCfg({}), attrCfg({make_int32_sv_cfg("array.a")}), sCfg({make_summary_field("array", "jsonstring")}), smCfg({make_attribute_combiner_override("array")}));
    assertAttributeConfig({});
    assertSummarymapConfig({});
}

TEST_F(DelayerTest, require_that_removing_attribute_aspect_from_struct_field_is_not_delayed)
{
    addFields({"array.a"});
    setup(attrCfg({make_int32_sv_cfg("array.a")}), smCfg({make_attribute_combiner_override("array")}), attrCfg({}), sCfg({make_summary_field("array", "jsonstring")}), smCfg({}));
    assertAttributeConfig({});
    assertSummarymapConfig({});
}

TEST_F(DelayerTest, require_that_adding_attribute_aspect_to_struct_field_is_delayed_if_field_type_is_unchanged_with_filtering_docsum)
{
    addFields({"array.a"});
    setup(attrCfg({}), smCfg({}), attrCfg({make_int32_sv_cfg("array.a")}), sCfg({make_summary_field("array", "jsonstring")}), smCfg({make_attribute_combiner_override("array"), make_matched_attribute_elements_filter_override("array_filtered", "array")}));
    assertAttributeConfig({});
    assertSummarymapConfig({make_matched_elements_filter_override("array_filtered", "array")});
}

}

GTEST_MAIN_RUN_ALL_TESTS()
