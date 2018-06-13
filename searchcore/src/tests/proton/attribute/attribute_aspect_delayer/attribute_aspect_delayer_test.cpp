// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchcore/proton/test/attribute_utils.h>
#include <vespa/searchcore/proton/attribute/attribute_aspect_delayer.h>
#include <vespa/searchcore/proton/common/i_document_type_inspector.h>
#include <vespa/searchcore/proton/common/indexschema_inspector.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/config-indexschema.h>
#include <vespa/config-attributes.h>
#include <vespa/config-summarymap.h>

using vespa::config::search::AttributesConfig;
using vespa::config::search::AttributesConfigBuilder;
using vespa::config::search::IndexschemaConfig;
using vespa::config::search::IndexschemaConfigBuilder;
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

namespace proton
{

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

SummarymapConfig::Override make_attribute_override(const vespalib::string &name)
{
    SummarymapConfig::Override override;
    override.field = name;
    override.command = "attribute";
    override.arguments = name;
    return override;
}

SummarymapConfig::Override make_geopos_override(const vespalib::string &name)
{
    SummarymapConfig::Override override;
    override.field = name;
    override.command = "geopos";
    override.arguments = name;
    return override;
}

SummarymapConfig::Override make_attribute_combiner_override(const vespalib::string &name)
{
    SummarymapConfig::Override override;
    override.field = name;
    override.command = "attributecombiner";
    return override;
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

class Fixture
{
    MyInspector _inspector;
    IndexschemaConfigBuilder _oldIndexSchema;
    AttributeAspectDelayer _delayer;

public:
    Fixture()
        : _inspector(),
          _delayer()
    {
    }
    ~Fixture() { }
    void addFields(const std::vector<vespalib::string> &fields) {
        _inspector.addFields(fields);
    }
    void addOldIndexField(const vespalib::string &name) {
        IndexschemaConfig::Indexfield field;
        field.name = name;
        _oldIndexSchema.indexfield.emplace_back(field);
    }
    void setup(const AttributesConfig &oldAttributesConfig, const SummarymapConfig &oldSummarymapConfig,
               const AttributesConfig &newAttributesConfig, const SummarymapConfig &newSummarymapConfig) {
        IndexschemaInspector indexschemaInspector(_oldIndexSchema);
        _delayer.setup(oldAttributesConfig, oldSummarymapConfig,
                       newAttributesConfig, newSummarymapConfig,
                       indexschemaInspector, _inspector);
    }
    void assertAttributeConfig(const std::vector<AttributesConfig::Attribute> &exp)
    {
        auto actConfig = _delayer.getAttributesConfig();
        EXPECT_TRUE(exp == actConfig->attribute);
    }
    void assertSummarymapConfig(const std::vector<SummarymapConfig::Override> &exp)
    {
        auto summarymapConfig = _delayer.getSummarymapConfig();
        EXPECT_EQUAL(exp, summarymapConfig->override);
    }
};

TEST_F("require that empty config is OK", Fixture)
{
    f.setup(attrCfg({}), smCfg({}), attrCfg({}), smCfg({}));
    TEST_DO(f.assertAttributeConfig({}));
    TEST_DO(f.assertSummarymapConfig({}));
}

TEST_F("require that simple attribute config is OK", Fixture)
{
    f.setup(attrCfg({make_int32_sv_cfg()}), smCfg({make_attribute_override("a")}), attrCfg({make_int32_sv_cfg()}), smCfg({make_attribute_override("a")}));
    TEST_DO(f.assertAttributeConfig({make_int32_sv_cfg()}));
    TEST_DO(f.assertSummarymapConfig({make_attribute_override("a")}));
}

TEST_F("require that adding attribute aspect is delayed if field type is unchanged", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({}), smCfg({}), attrCfg({make_int32_sv_cfg()}), smCfg({make_attribute_override("a")}));
    TEST_DO(f.assertAttributeConfig({}));
    TEST_DO(f.assertSummarymapConfig({}));
}

TEST_F("require that adding attribute aspect is delayed if field type is unchanged, geopos override", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({}), smCfg({}), attrCfg({make_int32_sv_cfg()}), smCfg({make_geopos_override("a")}));
    TEST_DO(f.assertAttributeConfig({}));
    TEST_DO(f.assertSummarymapConfig({make_geopos_override("a")}));
}

TEST_F("require that adding attribute is not delayed if field type changed", Fixture)
{
    f.setup(attrCfg({}), smCfg({}), attrCfg({make_int32_sv_cfg()}), smCfg({make_attribute_override("a")}));
    TEST_DO(f.assertAttributeConfig({make_int32_sv_cfg()}));
    TEST_DO(f.assertSummarymapConfig({make_attribute_override("a")}));
}

TEST_F("require that removing attribute aspect is delayed if field type is unchanged", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({make_int32_sv_cfg()}), smCfg({make_attribute_override("a")}), attrCfg({}), smCfg({}));
    TEST_DO(f.assertAttributeConfig({make_int32_sv_cfg()}));
    TEST_DO(f.assertSummarymapConfig({make_attribute_override("a")}));
}

TEST_F("require that removing attribute aspect is delayed if field type is unchanged, gepos override", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({make_int32_sv_cfg()}), smCfg({make_geopos_override("a")}), attrCfg({}), smCfg({}));
    TEST_DO(f.assertAttributeConfig({make_int32_sv_cfg()}));
    TEST_DO(f.assertSummarymapConfig({}));
}

TEST_F("require that removing attribute aspect is not delayed if field type changed", Fixture)
{
    f.setup(attrCfg({make_int32_sv_cfg()}), smCfg({make_attribute_override("a")}), attrCfg({}), smCfg({}));
    TEST_DO(f.assertAttributeConfig({}));
    TEST_DO(f.assertSummarymapConfig({}));
}

TEST_F("require that removing attribute aspect is not delayed if also indexed", Fixture)
{
    f.addFields({"a"});
    f.addOldIndexField("a");
    f.setup(attrCfg({make_string_sv_cfg()}), smCfg({make_attribute_override("a")}), attrCfg({}), smCfg({}));
    TEST_DO(f.assertAttributeConfig({}));
    TEST_DO(f.assertSummarymapConfig({}));
}

TEST_F("require that removing attribute aspect is not delayed for tensor", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({make_tensor_cfg("tensor(x[10])")}), smCfg({make_attribute_override("a")}), attrCfg({}), smCfg({}));
    TEST_DO(f.assertAttributeConfig({}));
    TEST_DO(f.assertSummarymapConfig({}));
}

TEST_F("require that removing attribute aspect is not delayed for predicate", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({make_predicate_cfg(4)}), smCfg({}), attrCfg({}), smCfg({}));
    TEST_DO(f.assertAttributeConfig({}));
    TEST_DO(f.assertSummarymapConfig({}));
}

TEST_F("require that removing attribute aspect is not delayed for reference", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({make_reference_cfg()}), smCfg({}), attrCfg({}), smCfg({}));
    TEST_DO(f.assertAttributeConfig({}));
    TEST_DO(f.assertSummarymapConfig({}));
}

TEST_F("require that fast access flag change is delayed, false->true edge", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({make_int32_sv_cfg()}), smCfg({make_attribute_override("a")}), attrCfg({make_fa(make_int32_sv_cfg())}), smCfg({make_attribute_override("a")}));
    TEST_DO(f.assertAttributeConfig({make_int32_sv_cfg()}));
    TEST_DO(f.assertSummarymapConfig({make_attribute_override("a")}));
}

TEST_F("require that fast access flag change is delayed, true->false edge", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({make_fa(make_int32_sv_cfg())}), smCfg({make_attribute_override("a")}), attrCfg({make_int32_sv_cfg()}), smCfg({make_attribute_override("a")}));
    TEST_DO(f.assertAttributeConfig({make_fa(make_int32_sv_cfg())}));
    TEST_DO(f.assertSummarymapConfig({make_attribute_override("a")}));
}

TEST_F("require that fast access flag change is delayed, false->true edge, tensor attr", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({make_tensor_cfg("tensor(x[10])")}), smCfg({make_attribute_override("a")}), attrCfg({make_fa(make_tensor_cfg("tensor(x[10])"))}), smCfg({make_attribute_override("a")}));
    TEST_DO(f.assertAttributeConfig({make_tensor_cfg("tensor(x[10])")}));
    TEST_DO(f.assertSummarymapConfig({make_attribute_override("a")}));
}

TEST_F("require that fast access flag change is not delayed, true->false edge, tensor attr", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({make_fa(make_tensor_cfg("tensor(x[10])"))}), smCfg({make_attribute_override("a")}), attrCfg({make_tensor_cfg("tensor(x[10])")}), smCfg({make_attribute_override("a")}));
    TEST_DO(f.assertAttributeConfig({make_tensor_cfg("tensor(x[10])")}));
    TEST_DO(f.assertSummarymapConfig({make_attribute_override("a")}));
}

TEST_F("require that fast access flag change is not delayed, true->false edge, string attribute, indexed field", Fixture)
{
    f.addFields({"a"});
    f.addOldIndexField("a");
    f.setup(attrCfg({make_fa(make_string_sv_cfg())}), smCfg({make_attribute_override("a")}), attrCfg({make_string_sv_cfg()}), smCfg({make_attribute_override("a")}));
    TEST_DO(f.assertAttributeConfig({make_string_sv_cfg()}));
    TEST_DO(f.assertSummarymapConfig({make_attribute_override("a")}));
}

TEST_F("require that adding attribute aspect to struct field is not delayed if field type is changed", Fixture)
{
    f.setup(attrCfg({}), smCfg({}), attrCfg({make_int32_sv_cfg("array.a")}), smCfg({make_attribute_combiner_override("array")}));
    TEST_DO(f.assertAttributeConfig({make_int32_sv_cfg("array.a")}));
    TEST_DO(f.assertSummarymapConfig({make_attribute_combiner_override("array")}));
}

TEST_F("require that adding attribute aspect to struct field is delayed if field type is unchanged", Fixture)
{
    f.addFields({"array.a"});
    f.setup(attrCfg({}), smCfg({}), attrCfg({make_int32_sv_cfg("array.a")}), smCfg({make_attribute_combiner_override("array")}));
    TEST_DO(f.assertAttributeConfig({}));
    TEST_DO(f.assertSummarymapConfig({}));
}

TEST_F("require that removing attribute aspect from struct field is not delayed", Fixture)
{
    f.addFields({"array.a"});
    f.setup(attrCfg({make_int32_sv_cfg("array.a")}), smCfg({make_attribute_combiner_override("array")}), attrCfg({}), smCfg({}));
    TEST_DO(f.assertAttributeConfig({}));
    TEST_DO(f.assertSummarymapConfig({}));
}

}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
