// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>

#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/features/tensor_from_weighted_set_feature.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/test/ftlib.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/indexenvironmentbuilder.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/test/value_compare.h>

using search::feature_t;
using namespace search::fef;
using namespace search::fef::test;
using namespace search::features;
using search::AttributeFactory;
using search::IntegerAttribute;
using search::StringAttribute;
using vespalib::eval::Value;
using vespalib::eval::Function;
using vespalib::eval::TensorSpec;
using vespalib::eval::SimpleValue;

typedef search::attribute::Config AVC;
typedef search::attribute::BasicType AVBT;
typedef search::attribute::CollectionType AVCT;
typedef search::AttributeVector::SP AttributePtr;
typedef FtTestApp FTA;

Value::UP make_tensor(const TensorSpec &spec) {
    return SimpleValue::from_spec(spec);
}

Value::UP make_empty(const vespalib::string &type) {
    return make_tensor(TensorSpec(type));
}

struct SetupFixture
{
    TensorFromWeightedSetBlueprint blueprint;
    IndexEnvironment indexEnv;
    SetupFixture()
        : blueprint(),
          indexEnv()
    {
    }
};

TEST_F("require that blueprint can be created from factory", SetupFixture)
{
    EXPECT_TRUE(FTA::assertCreateInstance(f.blueprint, "tensorFromWeightedSet"));
}

TEST_F("require that setup fails if source spec is invalid", SetupFixture)
{
    FTA::FT_SETUP_FAIL(f.blueprint, f.indexEnv, StringList().add("source(foo)"));
}

TEST_F("require that setup succeeds with attribute source", SetupFixture)
{
    FTA::FT_SETUP_OK(f.blueprint, f.indexEnv, StringList().add("attribute(foo)"),
            StringList(), StringList().add("tensor"));
}

TEST_F("require that setup succeeds with query source", SetupFixture)
{
    FTA::FT_SETUP_OK(f.blueprint, f.indexEnv, StringList().add("query(foo)"),
            StringList(), StringList().add("tensor"));
}

struct ExecFixture
{
    BlueprintFactory factory;
    FtFeatureTest test;
    ExecFixture(const vespalib::string &feature)
        : factory(),
          test(factory, feature)
    {
        setup_search_features(factory);
        setupAttributeVectors();
        setupQueryEnvironment();
        ASSERT_TRUE(test.setup());
    }
    void setupAttributeVectors() {
        std::vector<AttributePtr> attrs;
        attrs.push_back(AttributeFactory::createAttribute("wsstr", AVC(AVBT::STRING,  AVCT::WSET)));
        attrs.push_back(AttributeFactory::createAttribute("wsint", AVC(AVBT::INT32,  AVCT::WSET)));
        attrs.push_back(AttributeFactory::createAttribute("astr", AVC(AVBT::STRING,  AVCT::ARRAY)));

        for (const auto &attr : attrs) {
            attr->addReservedDoc();
            attr->addDocs(1);
            test.getIndexEnv().getAttributeMap().add(attr);
        }

        StringAttribute *wsstr = static_cast<StringAttribute *>(attrs[0].get());
        wsstr->append(1, "a", 3);
        wsstr->append(1, "b", 5);
        wsstr->append(1, "c", 7);

        IntegerAttribute *wsint = static_cast<IntegerAttribute *>(attrs[1].get());
        wsint->append(1, 11, 3);
        wsint->append(1, 13, 5);
        wsint->append(1, 17, 7);

        for (const auto &attr : attrs) {
            attr->commit();
        }
    }
    void setupQueryEnvironment() {
        test.getQueryEnv().getProperties().add("wsquery", "{d:11,e:13,f:17}");
    }
    const Value &extractTensor(uint32_t docid) {
        return test.resolveObjectFeature(docid);
    }
    const Value &execute() {
        return extractTensor(1);
    }
};

TEST_F("require that weighted set string attribute can be converted to tensor (default dimension)",
        ExecFixture("tensorFromWeightedSet(attribute(wsstr))"))
{
    EXPECT_EQUAL(*make_tensor(TensorSpec("tensor(wsstr{})")
                              .add({{"wsstr","b"}}, 5)
                              .add({{"wsstr","c"}}, 7)
                              .add({{"wsstr","a"}}, 3)), f.execute());
}

TEST_F("require that weighted set string attribute can be converted to tensor (explicit dimension)",
        ExecFixture("tensorFromWeightedSet(attribute(wsstr),dim)"))
{
    EXPECT_EQUAL(*make_tensor(TensorSpec("tensor(dim{})")
                              .add({{"dim","a"}}, 3)
                              .add({{"dim","b"}}, 5)
                              .add({{"dim","c"}}, 7)), f.execute());
}

TEST_F("require that weighted set integer attribute can be converted to tensor (default dimension)",
        ExecFixture("tensorFromWeightedSet(attribute(wsint))"))
{
    EXPECT_EQUAL(*make_tensor(TensorSpec("tensor(wsint{})")
                              .add({{"wsint","13"}}, 5)
                              .add({{"wsint","17"}}, 7)
                              .add({{"wsint","11"}}, 3)), f.execute());
}

TEST_F("require that weighted set integer attribute can be converted to tensor (explicit dimension)",
        ExecFixture("tensorFromWeightedSet(attribute(wsint),dim)"))
{
    EXPECT_EQUAL(*make_tensor(TensorSpec("tensor(dim{})")
                              .add({{"dim","17"}}, 7)
                              .add({{"dim","11"}}, 3)
                              .add({{"dim","13"}}, 5)), f.execute());
}

TEST_F("require that weighted set from query can be converted to tensor (default dimension)",
        ExecFixture("tensorFromWeightedSet(query(wsquery))"))
{
    EXPECT_EQUAL(*make_tensor(TensorSpec("tensor(wsquery{})")
                              .add({{"wsquery","f"}}, 17)
                              .add({{"wsquery","d"}}, 11)
                              .add({{"wsquery","e"}}, 13)), f.execute());
}

TEST_F("require that weighted set from query can be converted to tensor (explicit dimension)",
        ExecFixture("tensorFromWeightedSet(query(wsquery),dim)"))
{
    EXPECT_EQUAL(*make_tensor(TensorSpec("tensor(dim{})")
                              .add({{"dim","d"}}, 11)
                              .add({{"dim","e"}}, 13)
                              .add({{"dim","f"}}, 17)), f.execute());
}

TEST_F("require that empty tensor is created if attribute does not exists",
        ExecFixture("tensorFromWeightedSet(attribute(null))"))
{
    EXPECT_EQUAL(*make_empty("tensor(null{})"), f.execute());
}

TEST_F("require that empty tensor is created if attribute type is not supported",
        ExecFixture("tensorFromWeightedSet(attribute(astr))"))
{
    EXPECT_EQUAL(*make_empty("tensor(astr{})"), f.execute());
}

TEST_F("require that empty tensor is created if query parameter is not found",
        ExecFixture("tensorFromWeightedSet(query(null))"))
{
    EXPECT_EQUAL(*make_empty("tensor(null{})"), f.execute());
}

TEST_MAIN() { TEST_RUN_ALL(); }
