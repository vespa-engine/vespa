// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("attribute_specs_builder_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchcore/proton/test/attribute_utils.h>
#include <vespa/searchcore/proton/attribute/attribute_specs_builder.h>
#include <vespa/searchcore/proton/attribute/attribute_specs.h>
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

namespace {

const char *boolStr(bool val) {
    return val ? "true" : "false";
}

}

namespace std
{

ostream &operator<<(ostream &os, const Config &cfg)
{
    os << "{basicType=" << cfg.basicType().asString();
    os << ", collectionType=" << cfg.collectionType().asString();
    os << ", fastAccess=" << boolStr(cfg.fastAccess());
    os << "}";
    return os;
}

ostream &operator<<(ostream &os, const proton::AttributeSpec &spec)
{
    os << "{name=" << spec.getName();
    os << ", hideFromReading=" << boolStr(spec.getHideFromReading());
    os << ", hideFromWriting=" << boolStr(spec.getHideFromWriting());
    os << ", " << spec.getConfig();
    os << "}";
    return os;
}

ostream &operator<<(ostream &os, const SummarymapConfig::Override &override)
{
    os << "{field=" << override.field << ", command=" << override.command << ", arguments=" << override.arguments << "}";
    return os;
}

}

namespace proton
{

namespace {

AttributesConfig::Attribute make_sv_cfg(AttributesConfig::Attribute::Datatype dataType)
{
    AttributesConfig::Attribute attr;
    attr.name = "a";
    attr.datatype = dataType;
    attr.collectiontype = AttributesConfig::Attribute::Collectiontype::SINGLE;
    return attr;
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

Config make_fa(const Config &cfg)
{
    Config modCfg(cfg);
    modCfg.setFastAccess(true);
    return modCfg;
}

const Config int32_sv(BasicType::Type::INT32);
const Config string_sv(BasicType::Type::STRING);

Config getTensor(const vespalib::string &spec)
{
    Config ret(BasicType::Type::TENSOR);
    ret.setTensorType(vespalib::eval::ValueType::from_spec(spec));
    return ret;
}

SummarymapConfig::Override make_attribute_override(const vespalib::string &name)
{
    SummarymapConfig::Override override;
    override.field = name;
    override.command = "attribute";
    override.arguments = name;
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
    AttributeSpecsBuilder _builder;

public:
    Fixture()
        : _inspector(),
          _builder()
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
    void setup(const AttributesConfig &newAttributesConfig, const SummarymapConfig &newSummarymapConfig) {
        _builder.setup(newAttributesConfig, newSummarymapConfig);
    }
    void setup(const AttributesConfig &oldAttributesConfig, const SummarymapConfig &oldSummarymapConfig,
               const AttributesConfig &newAttributesConfig, const SummarymapConfig &newSummarymapConfig) {
        IndexschemaInspector indexschemaInspector(_oldIndexSchema);
        _builder.setup(oldAttributesConfig, oldSummarymapConfig,
                       newAttributesConfig, newSummarymapConfig,
                       indexschemaInspector, _inspector);
    }
    void assertSpecs(const std::vector<AttributeSpec> &expSpecs)
    {
        const auto &actSpecs = _builder.getAttributeSpecs();
        EXPECT_EQUAL(expSpecs, actSpecs->getSpecs());
    }
    void assertAttributeConfig(const std::vector<AttributesConfig::Attribute> &exp)
    {
        auto actConfig = _builder.getAttributesConfig();
        EXPECT_TRUE(exp == actConfig->attribute);
    }
    void assertSummarymapConfig(const std::vector<SummarymapConfig::Override> &exp)
    {
        auto summarymapConfig = _builder.getSummarymapConfig();
        EXPECT_EQUAL(exp, summarymapConfig->override);
    }
};

TEST_F("require that empty specs is OK", Fixture)
{
    f.setup(attrCfg({}), smCfg({}));
    TEST_DO(f.assertSpecs({}));
    TEST_DO(f.assertAttributeConfig({}));
}

TEST_F("require that simple attribute specs is OK", Fixture)
{
    f.setup(attrCfg({make_int32_sv_cfg()}), smCfg({make_attribute_override("a")}));
    TEST_DO(f.assertSpecs({AttributeSpec("a", int32_sv, false, false)}));
    TEST_DO(f.assertAttributeConfig({make_int32_sv_cfg()}));
    TEST_DO(f.assertSummarymapConfig({make_attribute_override("a")}));
}

TEST_F("require that adding attribute aspect is delayed if field type is unchanged", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({}), smCfg({}), attrCfg({make_int32_sv_cfg()}), smCfg({make_attribute_override("a")}));
    TEST_DO(f.assertSpecs({AttributeSpec("a", int32_sv, false, true)}));
    TEST_DO(f.assertAttributeConfig({}));
    TEST_DO(f.assertSummarymapConfig({}));
}

TEST_F("require that adding attribute is not delayed if field type changed", Fixture)
{
    f.setup(attrCfg({}), smCfg({}), attrCfg({make_int32_sv_cfg()}), smCfg({make_attribute_override("a")}));
    TEST_DO(f.assertSpecs({AttributeSpec("a", int32_sv, false, false)}));
    TEST_DO(f.assertAttributeConfig({make_int32_sv_cfg()}));
    TEST_DO(f.assertSummarymapConfig({make_attribute_override("a")}));
}

TEST_F("require that removing attribute aspect is delayed if field type is unchanged", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({make_int32_sv_cfg()}), smCfg({make_attribute_override("a")}), attrCfg({}), smCfg({}));
    TEST_DO(f.assertSpecs({AttributeSpec("a", int32_sv, true, false)}));
    TEST_DO(f.assertAttributeConfig({make_int32_sv_cfg()}));
    TEST_DO(f.assertSummarymapConfig({make_attribute_override("a")}));
}

TEST_F("require that removing attribute aspect is not delayed if field type changed", Fixture)
{
    f.setup(attrCfg({make_int32_sv_cfg()}), smCfg({make_attribute_override("a")}), attrCfg({}), smCfg({}));
    TEST_DO(f.assertSpecs({}));
    TEST_DO(f.assertAttributeConfig({}));
    TEST_DO(f.assertSummarymapConfig({}));
}

TEST_F("require that removing attribute aspect is not delayed if also indexed", Fixture)
{
    f.addFields({"a"});
    f.addOldIndexField("a");
    f.setup(attrCfg({make_string_sv_cfg()}), smCfg({make_attribute_override("a")}), attrCfg({}), smCfg({}));
    TEST_DO(f.assertSpecs({}));
    TEST_DO(f.assertAttributeConfig({}));
    TEST_DO(f.assertSummarymapConfig({}));
}

TEST_F("require that removing attribute aspect is not delayed for tensor", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({make_tensor_cfg("tensor(x[10])")}), smCfg({make_attribute_override("a")}), attrCfg({}), smCfg({}));
    TEST_DO(f.assertSpecs({}));
    TEST_DO(f.assertAttributeConfig({}));
    TEST_DO(f.assertSummarymapConfig({}));
}

TEST_F("require that removing attribute aspect is not delayed for predicate", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({make_predicate_cfg(4)}), smCfg({}), attrCfg({}), smCfg({}));
    TEST_DO(f.assertSpecs({}));
    TEST_DO(f.assertAttributeConfig({}));
    TEST_DO(f.assertSummarymapConfig({}));
}

TEST_F("require that removing attribute aspect is not delayed for reference", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({make_reference_cfg()}), smCfg({}), attrCfg({}), smCfg({}));
    TEST_DO(f.assertSpecs({}));
    TEST_DO(f.assertAttributeConfig({}));
    TEST_DO(f.assertSummarymapConfig({}));
}

TEST_F("require that fast access flag change is delayed, false->true edge", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({make_int32_sv_cfg()}), smCfg({make_attribute_override("a")}), attrCfg({make_fa(make_int32_sv_cfg())}), smCfg({make_attribute_override("a")}));
    TEST_DO(f.assertSpecs({AttributeSpec("a", int32_sv)}));
    TEST_DO(f.assertAttributeConfig({make_int32_sv_cfg()}));
    TEST_DO(f.assertSummarymapConfig({make_attribute_override("a")}));
}

TEST_F("require that fast access flag change is delayed, true->false edge", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({make_fa(make_int32_sv_cfg())}), smCfg({make_attribute_override("a")}), attrCfg({make_int32_sv_cfg()}), smCfg({make_attribute_override("a")}));
    TEST_DO(f.assertSpecs({AttributeSpec("a", make_fa(int32_sv))}));
    TEST_DO(f.assertAttributeConfig({make_fa(make_int32_sv_cfg())}));
    TEST_DO(f.assertSummarymapConfig({make_attribute_override("a")}));
}

TEST_F("require that fast access flag change is delayed, false->true edge, tensor attr", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({make_tensor_cfg("tensor(x[10])")}), smCfg({make_attribute_override("a")}), attrCfg({make_fa(make_tensor_cfg("tensor(x[10])"))}), smCfg({make_attribute_override("a")}));
    TEST_DO(f.assertSpecs({AttributeSpec("a", getTensor("tensor(x[10])"))}));
    TEST_DO(f.assertAttributeConfig({make_tensor_cfg("tensor(x[10])")}));
    TEST_DO(f.assertSummarymapConfig({make_attribute_override("a")}));
}

TEST_F("require that fast access flag change is not delayed, true->false edge, tensor attr", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({make_fa(make_tensor_cfg("tensor(x[10])"))}), smCfg({make_attribute_override("a")}), attrCfg({make_tensor_cfg("tensor(x[10])")}), smCfg({make_attribute_override("a")}));
    TEST_DO(f.assertSpecs({AttributeSpec("a", getTensor("tensor(x[10])"))}));
    TEST_DO(f.assertAttributeConfig({make_tensor_cfg("tensor(x[10])")}));
    TEST_DO(f.assertSummarymapConfig({make_attribute_override("a")}));
}

TEST_F("require that fast access flag change is not delayed, true->false edge, string attribute, indexed field", Fixture)
{
    f.addFields({"a"});
    f.addOldIndexField("a");
    f.setup(attrCfg({make_fa(make_string_sv_cfg())}), smCfg({make_attribute_override("a")}), attrCfg({make_string_sv_cfg()}), smCfg({make_attribute_override("a")}));
    TEST_DO(f.assertSpecs({AttributeSpec("a", string_sv, false, false)}));
    TEST_DO(f.assertAttributeConfig({make_string_sv_cfg()}));
    TEST_DO(f.assertSummarymapConfig({make_attribute_override("a")}));
}

}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
