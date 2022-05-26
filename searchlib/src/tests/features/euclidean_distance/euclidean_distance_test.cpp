// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>

#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/floatbase.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/ftlib.h>
#include <vespa/searchlib/features/euclidean_distance_feature.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchcommon/attribute/config.h>


using search::feature_t;
using namespace search::fef;
using namespace search::fef::test;
using namespace search::features;
using search::AttributeFactory;
using search::IntegerAttribute;
using search::FloatingPointAttribute;

typedef search::attribute::Config AVC;
typedef search::attribute::BasicType AVBT;
typedef search::attribute::CollectionType AVCT;
typedef search::AttributeVector::SP AttributePtr;
typedef FtTestApp FTA;
using CollectionType = FieldInfo::CollectionType;

struct SetupFixture
{
    EuclideanDistanceBlueprint blueprint;
    IndexEnvironment indexEnv;
    SetupFixture()
        : blueprint(),
          indexEnv()
    {
        FieldInfo myField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "myAttribute", 1);
        indexEnv.getFields().push_back(myField); 
    }
};

TEST_F("require that blueprint can be created from factory", SetupFixture)
{
    EXPECT_TRUE(FTA::assertCreateInstance(f.blueprint, "euclideanDistance"));
}

TEST_F("require that setup succeeds with attribute source", SetupFixture)
{
    FTA::FT_SETUP_OK(f.blueprint, f.indexEnv, StringList().add("myAttribute").add("myVector"),
            StringList(), StringList().add("distance"));
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
        attrs.push_back(AttributeFactory::createAttribute("aint", AVC(AVBT::INT32,  AVCT::ARRAY)));
        attrs.push_back(AttributeFactory::createAttribute("afloat", AVC(AVBT::FLOAT,  AVCT::ARRAY)));
        
        test.getIndexEnv().getFields().push_back(FieldInfo(FieldType::ATTRIBUTE, CollectionType::ARRAY, "aint", 0)); 
        test.getIndexEnv().getFields().push_back(FieldInfo(FieldType::ATTRIBUTE, CollectionType::ARRAY, "afloat", 1)); 

        for (const auto &attr : attrs) {
            attr->addReservedDoc();
            attr->addDocs(1);
            test.getIndexEnv().getAttributeMap().add(attr);
        }

        IntegerAttribute *aint = static_cast<IntegerAttribute *>(attrs[0].get());
        aint->append(1, 1, 0);
        aint->append(1, -2, 0);
        aint->append(1, 3, 0);

        FloatingPointAttribute *afloat = static_cast<FloatingPointAttribute *>(attrs[1].get());
        afloat->append(1, 1.3, 0);
        afloat->append(1, 1.5, 0);
        afloat->append(1, -1.7, 0);

        for (const auto &attr : attrs) {
            attr->commit();
        }
    }
    void setupQueryEnvironment() {
        test.getQueryEnv().getProperties().add("euclideanDistance.intquery", "[4 5 -6]");
        test.getQueryEnv().getProperties().add("euclideanDistance.floatquery", "[4.1 15 0.001]");
    }

};

TEST_F("require that distance is calculated for integer vectors",
        ExecFixture("euclideanDistance(aint,intquery)"))
{
    EXPECT_TRUE(f.test.execute(11.789826, 0.000001));
}

TEST_F("require that distance is calculated for floating point vectors",
        ExecFixture("euclideanDistance(afloat,floatquery)"))
{
    EXPECT_TRUE(f.test.execute(13.891846, 0.000001));
}

TEST_MAIN() { TEST_RUN_ALL(); }
