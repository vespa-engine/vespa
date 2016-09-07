// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/eval/function.h>
#include <vespa/vespalib/tensor/tensor.h>

#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/test/as_tensor.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/indexenvironmentbuilder.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>
#include <vespa/searchlib/fef/test/ftlib.h>
#include <vespa/searchlib/features/tensor_from_labels_feature.h>
#include <vespa/searchlib/fef/fef.h>

using search::feature_t;
using namespace search::fef;
using namespace search::fef::test;
using namespace search::features;
using search::AttributeFactory;
using search::IntegerAttribute;
using search::StringAttribute;
using vespalib::eval::Value;
using vespalib::eval::Function;
using vespalib::tensor::Tensor;

typedef search::attribute::Config AVC;
typedef search::attribute::BasicType AVBT;
typedef search::attribute::CollectionType AVCT;
typedef search::AttributeVector::SP AttributePtr;
typedef FtTestApp FTA;

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

        for (const auto &attr : attrs) {
            attr->addReservedDoc();
            attr->addDocs(1);
            test.getIndexEnv().getAttributeManager().add(attr);
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

        for (const auto &attr : attrs) {
            attr->commit();
        }
    }
    void setupQueryEnvironment() {
        test.getQueryEnv().getProperties().add("astr_query", "[d e f]");
        test.getQueryEnv().getProperties().add("aint_query", "[11 13 17]");
    }
    const Tensor &extractTensor() {
        const Value::CREF *value = test.resolveObjectFeature();
        ASSERT_TRUE(value != nullptr);
        ASSERT_TRUE(value->get().is_tensor());
        return static_cast<const Tensor &>(*value->get().as_tensor());
    }
    const Tensor &execute() {
        test.executeOnly();
        return extractTensor();
    }
};

// Tests for attribute source:

TEST_F("require that array string attribute can be converted to tensor (default dimension)",
        ExecFixture("tensorFromLabels(attribute(astr))"))
{
    EXPECT_EQUAL(AsTensor("{ {astr:a}:1, {astr:b}:1, {astr:c}:1 }"), f.execute());
}

TEST_F("require that array string attribute can be converted to tensor (explicit dimension)",
        ExecFixture("tensorFromLabels(attribute(astr),dim)"))
{
    EXPECT_EQUAL(AsTensor("{ {dim:a}:1, {dim:b}:1, {dim:c}:1 }"), f.execute());
}

TEST_F("require that array integer attribute can be converted to tensor (default dimension)",
        ExecFixture("tensorFromLabels(attribute(aint))"))
{
    EXPECT_EQUAL(AsTensor("{ {aint:7}:1, {aint:3}:1, {aint:5}:1 }"), f.execute());
}

TEST_F("require that array attribute can be converted to tensor (explicit dimension)",
        ExecFixture("tensorFromLabels(attribute(aint),dim)"))
{
    EXPECT_EQUAL(AsTensor("{ {dim:7}:1, {dim:3}:1, {dim:5}:1 }"), f.execute());
}

TEST_F("require that empty tensor is created if attribute does not exists",
        ExecFixture("tensorFromLabels(attribute(null))"))
{
    EXPECT_EQUAL(AsEmptyTensor("tensor(null{})"), f.execute());
}

TEST_F("require that empty tensor is created if attribute type is not supported",
        ExecFixture("tensorFromLabels(attribute(wsstr))"))
{
    EXPECT_EQUAL(AsEmptyTensor("tensor(wsstr{})"), f.execute());
}


// Tests for query source:

TEST_F("require that string array from query can be converted to tensor (default dimension)",
        ExecFixture("tensorFromLabels(query(astr_query))"))
{
    EXPECT_EQUAL(AsTensor("{ {astr_query:d}:1, {astr_query:e}:1, {astr_query:f}:1 }"), f.execute());
}

TEST_F("require that integer array from query can be converted to tensor (default dimension)",
        ExecFixture("tensorFromLabels(query(aint_query))"))
{
    EXPECT_EQUAL(AsTensor("{ {aint_query:13}:1, {aint_query:17}:1, {aint_query:11}:1 }"), f.execute());
}

TEST_F("require that string array from query can be converted to tensor (explicit dimension)",
        ExecFixture("tensorFromLabels(query(astr_query),dim)"))
{
    EXPECT_EQUAL(AsTensor("{ {dim:d}:1, {dim:e}:1, {dim:f}:1 }"), f.execute());
}

TEST_F("require that empty tensor is created if query parameter is not found",
        ExecFixture("tensorFromLabels(query(null))"))
{
    EXPECT_EQUAL(AsEmptyTensor("tensor(null{})"), f.execute());
}

TEST_MAIN() { TEST_RUN_ALL(); }
