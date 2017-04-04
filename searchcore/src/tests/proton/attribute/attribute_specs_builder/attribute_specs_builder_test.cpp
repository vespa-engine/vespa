// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("attribute_specs_builder_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchcore/proton/test/attribute_utils.h>
#include <vespa/searchcore/proton/attribute/attribute_specs_builder.h>
#include <vespa/searchcore/proton/common/i_document_type_inspector.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/config-indexschema.h>
#include <vespa/config-attributes.h>

using vespa::config::search::AttributesConfig;
using vespa::config::search::AttributesConfigBuilder;
using vespa::config::search::IndexschemaConfig;
using vespa::config::search::IndexschemaConfigBuilder;
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

}

namespace proton
{

namespace {

AttributesConfig::Attribute make_int32_sv_cfg() {
    AttributesConfig::Attribute attr;
    attr.name = "a";
    attr.datatype = AttributesConfig::Attribute::Datatype::INT32;
    attr.collectiontype = AttributesConfig::Attribute::Collectiontype::SINGLE;
    return attr;
}

AttributesConfig::Attribute make_string_sv_cfg() {
    AttributesConfig::Attribute attr;
    attr.name = "a";
    attr.datatype = AttributesConfig::Attribute::Datatype::STRING;
    attr.collectiontype = AttributesConfig::Attribute::Collectiontype::SINGLE;
    return attr;
}

AttributesConfig::Attribute make_predicate_cfg(uint32_t arity)
{
    AttributesConfig::Attribute attr;
    attr.name = "a";
    attr.datatype = AttributesConfig::Attribute::Datatype::PREDICATE;
    attr.collectiontype = AttributesConfig::Attribute::Collectiontype::SINGLE;
    attr.arity = arity;
    return attr;
}

AttributesConfig::Attribute make_tensor_cfg(const vespalib::string &spec)
{
    AttributesConfig::Attribute attr;
    attr.name = "a";
    attr.datatype = AttributesConfig::Attribute::Datatype::TENSOR;
    attr.collectiontype = AttributesConfig::Attribute::Collectiontype::SINGLE;
    attr.tensortype = spec;
    return attr;
}

AttributesConfig::Attribute make_reference_cfg()
{
    AttributesConfig::Attribute attr;
    attr.name = "a";
    attr.datatype = AttributesConfig::Attribute::Datatype::REFERENCE;
    attr.collectiontype = AttributesConfig::Attribute::Collectiontype::SINGLE;
    return attr;
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

Config getPredicate(uint32_t arity)
{
    Config ret(BasicType::Type::PREDICATE);
    search::attribute::PredicateParams predicateParams;
    predicateParams.setArity(arity);
    ret.setPredicateParams(predicateParams);
    return ret;
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
    void setup(const AttributesConfig &newConfig) {
        _builder.setup(newConfig);
    }
    void setup(const AttributesConfig &oldConfig, const AttributesConfig &newConfig) {
        _builder.setup(oldConfig, newConfig, _oldIndexSchema, _inspector);
    }
    void assertSpecs(const std::vector<AttributeSpec> &expSpecs)
    {
        auto &actSpecs = _builder.getAttributeSpecs();
        EXPECT_EQUAL(expSpecs, actSpecs);
    }
    void assertConfigs(const std::vector<AttributesConfig::Attribute> &exp)
    {
        auto actConfig = _builder.getAttributesConfig();
        EXPECT_TRUE(exp == actConfig->attribute);
    }
};

TEST_F("require that empty specs is OK", Fixture)
{
    f.setup(attrCfg({}));
    TEST_DO(f.assertSpecs({}));
    TEST_DO(f.assertConfigs({}));
}

TEST_F("require that simple attribute specs is OK", Fixture)
{
    f.setup(attrCfg({make_int32_sv_cfg()}));
    TEST_DO(f.assertSpecs({AttributeSpec("a", int32_sv, false, false)}));
    TEST_DO(f.assertConfigs({make_int32_sv_cfg()}));
}

TEST_F("require that adding attribute aspect is delayed if field type is unchanged", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({}), attrCfg({make_int32_sv_cfg()}));
    TEST_DO(f.assertSpecs({AttributeSpec("a", int32_sv, false, true)}));
    TEST_DO(f.assertConfigs({}));
}

TEST_F("require that adding attribute is not delayed if field type changed", Fixture)
{
    f.setup(attrCfg({}), attrCfg({make_int32_sv_cfg()}));
    TEST_DO(f.assertSpecs({AttributeSpec("a", int32_sv, false, false)}));
    TEST_DO(f.assertConfigs({make_int32_sv_cfg()}));
}

TEST_F("require that removing attribute aspect is delayed if field type is unchanged", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({make_int32_sv_cfg()}), attrCfg({}));
    TEST_DO(f.assertSpecs({AttributeSpec("a", int32_sv, true, false)}));
    TEST_DO(f.assertConfigs({make_int32_sv_cfg()}));
}

TEST_F("require that removing attribute aspect is not delayed if field type changed", Fixture)
{
    f.setup(attrCfg({make_int32_sv_cfg()}), attrCfg({}));
    TEST_DO(f.assertSpecs({}));
    TEST_DO(f.assertConfigs({}));
}

TEST_F("require that removing attribute aspect is not delayed if also indexed", Fixture)
{
    f.addFields({"a"});
    f.addOldIndexField("a");
    f.setup(attrCfg({make_string_sv_cfg()}), attrCfg({}));
    TEST_DO(f.assertSpecs({}));
    TEST_DO(f.assertConfigs({}));
}

TEST_F("require that removing attribute aspect is not delayed for tensor", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({make_tensor_cfg("tensor(x[10])")}), attrCfg({}));
    TEST_DO(f.assertSpecs({}));
    TEST_DO(f.assertConfigs({}));
}

TEST_F("require that removing attribute aspect is not delayed for predicate", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({make_predicate_cfg(4)}), attrCfg({}));
    TEST_DO(f.assertSpecs({}));
    TEST_DO(f.assertConfigs({}));
}

TEST_F("require that removing attribute aspect is not delayed for reference", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({make_reference_cfg()}), attrCfg({}));
    TEST_DO(f.assertSpecs({}));
    TEST_DO(f.assertConfigs({}));
}

TEST_F("require that fast access flag change is delayed, false->true edge", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({make_int32_sv_cfg()}), attrCfg({make_fa(make_int32_sv_cfg())}));
    TEST_DO(f.assertSpecs({AttributeSpec("a", int32_sv)}));
    TEST_DO(f.assertConfigs({make_int32_sv_cfg()}));
}

TEST_F("require that fast access flag change is delayed, true->false edge", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({make_fa(make_int32_sv_cfg())}), attrCfg({make_int32_sv_cfg()}));
    TEST_DO(f.assertSpecs({AttributeSpec("a", make_fa(int32_sv))}));
    TEST_DO(f.assertConfigs({make_fa(make_int32_sv_cfg())}));
}

TEST_F("require that fast access flag change is delayed, false->true edge, predicate attr", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({make_predicate_cfg(4)}), attrCfg({make_fa(make_predicate_cfg(4))}));
    TEST_DO(f.assertSpecs({AttributeSpec("a", getPredicate(4))}));
    TEST_DO(f.assertConfigs({make_predicate_cfg(4)}));
}

TEST_F("require that fast access flag change is not delayed, true->false edge, predicate attr", Fixture)
{
    f.addFields({"a"});
    f.setup(attrCfg({make_fa(make_predicate_cfg(4))}), attrCfg({make_predicate_cfg(4)}));
    TEST_DO(f.assertSpecs({AttributeSpec("a", getPredicate(4))}));
    TEST_DO(f.assertConfigs({make_predicate_cfg(4)}));
}

TEST_F("require that fast access flag change is not delayed, true->false edge, string attribute, indexed field", Fixture)
{
    f.addFields({"a"});
    f.addOldIndexField("a");
    f.setup(attrCfg({make_fa(make_string_sv_cfg())}), attrCfg({make_string_sv_cfg()}));
    TEST_DO(f.assertSpecs({AttributeSpec("a", string_sv, false, false)}));
    TEST_DO(f.assertConfigs({make_string_sv_cfg()}));
}

}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
