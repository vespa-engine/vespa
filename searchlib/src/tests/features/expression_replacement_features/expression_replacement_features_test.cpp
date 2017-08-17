// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>

#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/fef/test/ftlib.h>
#include <vespa/searchlib/features/max_reduce_prod_join_feature.h>
#include <vespa/searchlib/attribute/attribute.h>

using search::feature_t;
using namespace search::fef;
using namespace search::fef::test;
using namespace search::features;
using search::AttributeFactory;
using search::IntegerAttribute;
using CollectionType = FieldInfo::CollectionType;

typedef search::attribute::Config AVC;
typedef search::attribute::BasicType AVBT;
typedef search::attribute::CollectionType AVCT;
typedef search::AttributeVector::SP AttributePtr;
typedef FtTestApp FTA;

struct SetupFixture
{
    InternalMaxReduceProdJoinBlueprint blueprint;
    IndexEnvironment indexEnv;
    SetupFixture(const vespalib::string &attr)
        : blueprint(),
          indexEnv()
    {
        FieldInfo attr_info(FieldType::ATTRIBUTE, CollectionType::ARRAY, attr, 0);
        indexEnv.getFields().push_back(attr_info);
    }
};

TEST_F("require that blueprint can be created", SetupFixture("attribute(foo)"))
{
    EXPECT_TRUE(FTA::assertCreateInstance(f.blueprint, "internalMaxReduceProdJoin"));
}

TEST_F("require that setup fails if source spec is invalid", SetupFixture("attribute(foo)"))
{
    FTA::FT_SETUP_FAIL(f.blueprint, f.indexEnv, StringList().add("source(foo)"));
}

TEST_F("require that setup fails if attribute does not exist", SetupFixture("attribute(foo)"))
{
    FTA::FT_SETUP_FAIL(f.blueprint, f.indexEnv, StringList().add("attribute(bar)").add("query(baz)"));
}

TEST_F("require that setup succeeds with attribute and query parameters", SetupFixture("attribute(foo)"))
{
    FTA::FT_SETUP_OK(f.blueprint, f.indexEnv,
                    StringList().add("attribute(foo)").add("query(bar)"),
                    StringList(),
                    StringList().add("scalar"));
}

struct ExecFixture
{
    BlueprintFactory factory;
    FtFeatureTest test;
    ExecFixture(const vespalib::string &feature)
            : factory(),
              test(factory, feature)
    {
        factory.addPrototype(std::make_shared<InternalMaxReduceProdJoinBlueprint>());
        setupAttributeVectors();
        setupQueryEnvironment();
        ASSERT_TRUE(test.setup());
    }

    void setupAttributeVectors() {
        vespalib::string attrIntArray = "attribute(intarray)";
        vespalib::string attrLongArray = "attribute(longarray)";

        test.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, attrLongArray);
        test.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, attrIntArray);

        std::vector<AttributePtr> attrs;
        attrs.push_back(AttributeFactory::createAttribute(attrLongArray, AVC(AVBT::INT64,  AVCT::ARRAY)));
        attrs.push_back(AttributeFactory::createAttribute(attrIntArray, AVC(AVBT::INT32,  AVCT::ARRAY)));
        for (const auto &attr : attrs) {
            attr->addReservedDoc();
            attr->addDocs(1);
            test.getIndexEnv().getAttributeMap().add(attr);
        }

        IntegerAttribute *longArray = static_cast<IntegerAttribute *>(attrs[0].get());
        longArray->append(1, 1111, 0);
        longArray->append(1, 2222, 0);
        longArray->append(1, 78, 0);

        IntegerAttribute *intArray = static_cast<IntegerAttribute *>(attrs[1].get());
        intArray->append(1, 78, 0);
        intArray->append(1, 1111, 0);

        for (const auto &attr : attrs) {
            attr->commit();
        }
    }

    void setupQueryEnvironment() {
        test.getQueryEnv().getProperties().add("query(wset)", "{1111:1234, 2222:2245}");
        test.getQueryEnv().getProperties().add("query(wsetnomatch)", "{1:1000, 2:2000}");
        test.getQueryEnv().getProperties().add("query(array)", "[1111,2222]");
    }

    bool evaluatesTo(feature_t expected) {
        return test.execute(expected);
    }

};

TEST_F("require that executor returns correct result for long array",
       ExecFixture("internalMaxReduceProdJoin(attribute(longarray),query(wset))"))
{
    EXPECT_TRUE(f.evaluatesTo(2245));
}

TEST_F("require that executor returns correct result for int array",
       ExecFixture("internalMaxReduceProdJoin(attribute(intarray),query(wset))"))
{
    EXPECT_TRUE(f.evaluatesTo(1234));
}

TEST_F("require that executor returns 0 if no items match",
       ExecFixture("internalMaxReduceProdJoin(attribute(longarray),query(wsetnomatch))"))
{
    EXPECT_TRUE(f.evaluatesTo(0.0));
}

TEST_F("require that executor return 0 if query is not a weighted set",
       ExecFixture("internalMaxReduceProdJoin(attribute(longarray),query(array))"))
{
    EXPECT_TRUE(f.evaluatesTo(0.0));
}

TEST_MAIN() { TEST_RUN_ALL(); }
