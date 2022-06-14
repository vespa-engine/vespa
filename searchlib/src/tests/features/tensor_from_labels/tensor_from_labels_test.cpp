// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>

#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/features/tensor_from_labels_feature.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/test/ftlib.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchcommon/attribute/config.h>
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
    TensorFromLabelsBlueprint blueprint;
    IndexEnvironment indexEnv;
    SetupFixture()
        : blueprint(),
          indexEnv()
    {
    }
};

TEST_F("require that blueprint can be created from factory", SetupFixture)
{
    EXPECT_TRUE(FTA::assertCreateInstance(f.blueprint, "tensorFromLabels"));
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
        attrs.push_back(AttributeFactory::createAttribute("astr", AVC(AVBT::STRING,  AVCT::ARRAY)));
        attrs.push_back(AttributeFactory::createAttribute("aint", AVC(AVBT::INT32,  AVCT::ARRAY)));
        attrs.push_back(AttributeFactory::createAttribute("wsstr", AVC(AVBT::STRING,  AVCT::WSET)));
        attrs.push_back(AttributeFactory::createAttribute("sint", AVC(AVBT::INT32,  AVCT::SINGLE)));

        for (const auto &attr : attrs) {
            attr->addReservedDoc();
            attr->addDocs(1);
            test.getIndexEnv().getAttributeMap().add(attr);
        }

        StringAttribute *astr = static_cast<StringAttribute *>(attrs[0].get());
        // Note that the weight parameter is not used
        astr->append(1, "a", 0);
        astr->append(1, "b", 0);
        astr->append(1, "c", 0);

        IntegerAttribute *aint = static_cast<IntegerAttribute *>(attrs[1].get());
        aint->append(1, 3, 0);
        aint->append(1, 5, 0);
        aint->append(1, 7, 0);
        
        IntegerAttribute *sint = static_cast<IntegerAttribute *>(attrs[3].get());
        sint->update(1, 5);

        for (const auto &attr : attrs) {
            attr->commit();
        }
    }
    void setupQueryEnvironment() {
        test.getQueryEnv().getProperties().add("astr_query", "[d e f e]");
        test.getQueryEnv().getProperties().add("aint_query", "[11 13 17]");
    }
    const Value &extractTensor(uint32_t docid) {
        return test.resolveObjectFeature(docid);
    }
    const Value &execute() {
        return extractTensor(1);
    }
};

// Tests for attribute source:

TEST_F("require that array string attribute can be converted to tensor (default dimension)",
        ExecFixture("tensorFromLabels(attribute(astr))"))
{
    EXPECT_EQUAL(*make_tensor(TensorSpec("tensor(astr{})")
                              .add({{"astr", "a"}}, 1)
                              .add({{"astr", "b"}}, 1)
                              .add({{"astr", "c"}}, 1)), f.execute());
}

TEST_F("require that array string attribute can be converted to tensor (explicit dimension)",
        ExecFixture("tensorFromLabels(attribute(astr),dim)"))
{
    EXPECT_EQUAL(*make_tensor(TensorSpec("tensor(dim{})")
                              .add({{"dim", "a"}}, 1)
                              .add({{"dim", "b"}}, 1)
                              .add({{"dim", "c"}}, 1)), f.execute());
}

TEST_F("require that array integer attribute can be converted to tensor (default dimension)",
        ExecFixture("tensorFromLabels(attribute(aint))"))
{
    EXPECT_EQUAL(*make_tensor(TensorSpec("tensor(aint{})")
                              .add({{"aint", "7"}}, 1)
                              .add({{"aint", "3"}}, 1)
                              .add({{"aint", "5"}}, 1)), f.execute());
}

TEST_F("require that array attribute can be converted to tensor (explicit dimension)",
        ExecFixture("tensorFromLabels(attribute(aint),dim)"))
{
    EXPECT_EQUAL(*make_tensor(TensorSpec("tensor(dim{})")
                              .add({{"dim", "7"}}, 1)
                              .add({{"dim", "3"}}, 1)
                              .add({{"dim", "5"}}, 1)), f.execute());
}

TEST_F("require that single-value integer attribute can be converted to tensor (default dimension)",
        ExecFixture("tensorFromLabels(attribute(sint))"))
{
    EXPECT_EQUAL(*make_tensor(TensorSpec("tensor(sint{})")
                              .add({{"sint", "5"}}, 1)), f.execute());
}

TEST_F("require that single-value integer attribute can be converted to tensor (explicit dimension)",
        ExecFixture("tensorFromLabels(attribute(sint),foobar)"))
{
    EXPECT_EQUAL(*make_tensor(TensorSpec("tensor(foobar{})")
                              .add({{"foobar", "5"}}, 1)), f.execute());
}

TEST_F("require that empty tensor is created if attribute does not exists",
        ExecFixture("tensorFromLabels(attribute(null))"))
{
    EXPECT_EQUAL(*make_empty("tensor(null{})"), f.execute());
}

TEST_F("require that empty tensor is created if attribute type is not supported",
        ExecFixture("tensorFromLabels(attribute(wsstr))"))
{
    EXPECT_EQUAL(*make_empty("tensor(wsstr{})"), f.execute());
}


// Tests for query source:

TEST_F("require that string array from query can be converted to tensor (default dimension)",
        ExecFixture("tensorFromLabels(query(astr_query))"))
{
    EXPECT_EQUAL(*make_tensor(TensorSpec("tensor(astr_query{})")
                              .add({{"astr_query", "d"}}, 1)
                              .add({{"astr_query", "e"}}, 1)
                              .add({{"astr_query", "f"}}, 1)), f.execute());
}

TEST_F("require that integer array from query can be converted to tensor (default dimension)",
        ExecFixture("tensorFromLabels(query(aint_query))"))
{
    EXPECT_EQUAL(*make_tensor(TensorSpec("tensor(aint_query{})")
                              .add({{"aint_query", "13"}}, 1)
                              .add({{"aint_query", "17"}}, 1)
                              .add({{"aint_query", "11"}}, 1)), f.execute());
}

TEST_F("require that string array from query can be converted to tensor (explicit dimension)",
        ExecFixture("tensorFromLabels(query(astr_query),dim)"))
{
    EXPECT_EQUAL(*make_tensor(TensorSpec("tensor(dim{})")
                              .add({{"dim", "d"}}, 1)
                              .add({{"dim", "e"}}, 1)
                              .add({{"dim", "f"}}, 1)), f.execute());
}

TEST_F("require that empty tensor is created if query parameter is not found",
        ExecFixture("tensorFromLabels(query(null))"))
{
    EXPECT_EQUAL(*make_empty("tensor(null{})"), f.execute());
}

TEST_MAIN() { TEST_RUN_ALL(); }
