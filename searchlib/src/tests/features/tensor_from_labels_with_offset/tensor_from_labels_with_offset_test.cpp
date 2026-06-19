// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/test/value_compare.h>
#include <vespa/eval/eval/value.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/features/tensor_from_labels_with_offset_feature.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/test/ft_test_app_base.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::feature_t;
using namespace search::fef;
using namespace search::fef::test;
using namespace search::features;
using search::AttributeFactory;
using search::IntegerAttribute;
using search::StringAttribute;
using vespalib::eval::SimpleValue;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;

using AVC = search::attribute::Config;
using AVBT = search::attribute::BasicType;
using AVCT = search::attribute::CollectionType;
using AttributePtr = search::AttributeVector::SP;
using FTA = FtTestAppBase;

Value::UP make_tensor(const TensorSpec& spec) {
    return SimpleValue::from_spec(spec);
}

Value::UP make_empty(const std::string& type) {
    return make_tensor(TensorSpec(type));
}

struct SetupFixture {
    TensorFromLabelsWithOffsetBlueprint blueprint;
    IndexEnvironment                    indexEnv;
    SetupFixture() : blueprint(), indexEnv() {}
};

TEST(TensorFromLabelsWithOffsetTest, require_that_blueprint_can_be_created_from_factory) {
    SetupFixture f;
    EXPECT_TRUE(FTA::assertCreateInstance(f.blueprint, "tensorFromLabelsWithOffset"));
}

TEST(TensorFromLabelsWithOffsetTest, require_that_setup_fails_if_source_spec_is_invalid) {
    SetupFixture f;
    FTA::FT_SETUP_FAIL(f.blueprint, f.indexEnv, StringList().add("source(foo)").add("dim").add("offset"));
}

TEST(TensorFromLabelsWithOffsetTest, require_that_setup_succeeds_with_attribute_source) {
    SetupFixture f;
    FTA::FT_SETUP_OK(f.blueprint, f.indexEnv, StringList().add("attribute(foo)").add("dim").add("offset"),
                     StringList(), StringList().add("tensor"));
}

TEST(TensorFromLabelsWithOffsetTest, require_that_setup_fails_with_query_source) {
    SetupFixture f;
    FTA::FT_SETUP_FAIL(f.blueprint, f.indexEnv, StringList().add("query(foo)").add("dim").add("offset"));
}

struct ExecFixture {
    BlueprintFactory factory;
    FtFeatureTest    test;
    ExecFixture(const std::string& feature) : factory(), test(factory, feature) {
        setup_search_features(factory);
        setupAttributeVectors();
        EXPECT_TRUE(test.setup());
    }
    void setupAttributeVectors() {
        std::vector<AttributePtr> attrs;
        attrs.push_back(AttributeFactory::createAttribute("astr", AVC(AVBT::STRING, AVCT::ARRAY)));
        attrs.push_back(AttributeFactory::createAttribute("aint", AVC(AVBT::INT32, AVCT::ARRAY)));
        attrs.push_back(AttributeFactory::createAttribute("wsstr", AVC(AVBT::STRING, AVCT::WSET)));
        attrs.push_back(AttributeFactory::createAttribute("sint", AVC(AVBT::INT32, AVCT::SINGLE)));
        attrs.push_back(AttributeFactory::createAttribute("sstr", AVC(AVBT::STRING, AVCT::SINGLE)));
        attrs.push_back(AttributeFactory::createAttribute("adup", AVC(AVBT::STRING, AVCT::ARRAY)));

        for (const auto& attr : attrs) {
            attr->addReservedDoc();
            attr->addDocs(2);
            test.getIndexEnv().getAttributeMap().add(attr);
        }

        StringAttribute& astr = dynamic_cast<StringAttribute&>(*attrs[0]);
        astr.append(1, "a", 0);
        astr.append(1, "b", 0);
        astr.append(1, "c", 0);

        IntegerAttribute& aint = dynamic_cast<IntegerAttribute&>(*attrs[1]);
        aint.append(1, 3, 0);
        aint.append(1, 5, 0);
        aint.append(1, 7, 0);

        IntegerAttribute& sint = dynamic_cast<IntegerAttribute&>(*attrs[3]);
        sint.update(1, 5);

        StringAttribute& sstr = dynamic_cast<StringAttribute&>(*attrs[4]);
        sstr.update(1, "foo");

        StringAttribute& adup = dynamic_cast<StringAttribute&>(*attrs[5]);
        adup.append(1, "a", 0);
        adup.append(1, "a", 0);
        adup.append(1, "b", 0);

        for (const auto& attr : attrs) {
            attr->commit();
        }
    }
    const Value& extractTensor(uint32_t docid) { return test.resolveObjectFeature(docid); }
    const Value& execute(uint32_t docid = 1) { return extractTensor(docid); }
};

// Tests for attribute source — string array:

TEST(TensorFromLabelsWithOffsetTest, array_string_attribute_produces_2d_tensor_with_label_and_offset_dims) {
    ExecFixture f("tensorFromLabelsWithOffset(attribute(astr),dim,offset)");
    // tensor(dim{},offset{}) — 'dim' < 'offset' alphabetically, so dim is index 0 in the type
    EXPECT_EQ(*make_tensor(TensorSpec("tensor<float>(dim{},offset{})")
                               .add({{"dim", "a"}, {"offset", "0"}}, 1)
                               .add({{"dim", "b"}, {"offset", "1"}}, 1)
                               .add({{"dim", "c"}, {"offset", "2"}}, 1)),
              f.execute());
}

TEST(TensorFromLabelsWithOffsetTest, duplicate_labels_are_disambiguated_by_offset) {
    // It's also possible to have duplicate values in the array: [a a b] keeps the
    // two 'a' entries as distinct cells, whereas tensorFromLabels would collide them.
    ExecFixture f("tensorFromLabelsWithOffset(attribute(adup),dim,offset)");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor<float>(dim{},offset{})")
                               .add({{"dim", "a"}, {"offset", "0"}}, 1)
                               .add({{"dim", "a"}, {"offset", "1"}}, 1)
                               .add({{"dim", "b"}, {"offset", "2"}}, 1)),
              f.execute());
}

TEST(TensorFromLabelsWithOffsetTest, dimension_order_in_spec_follows_alphabetical_sort) {
    // 'z' > 'a', so offset dim 'z' sorts after label dim 'a' — address ordering must still be correct
    ExecFixture f("tensorFromLabelsWithOffset(attribute(astr),a,z)");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor<float>(a{},z{})")
                               .add({{"a", "a"}, {"z", "0"}}, 1)
                               .add({{"a", "b"}, {"z", "1"}}, 1)
                               .add({{"a", "c"}, {"z", "2"}}, 1)),
              f.execute());
}

TEST(TensorFromLabelsWithOffsetTest, dimension_order_reversed_when_offset_dim_sorts_first) {
    // offset dim 'aoffset' < label dim 'zlabel' — type has offset first
    ExecFixture f("tensorFromLabelsWithOffset(attribute(astr),zlabel,aoffset)");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor<float>(aoffset{},zlabel{})")
                               .add({{"zlabel", "a"}, {"aoffset", "0"}}, 1)
                               .add({{"zlabel", "b"}, {"aoffset", "1"}}, 1)
                               .add({{"zlabel", "c"}, {"aoffset", "2"}}, 1)),
              f.execute());
}

// Tests for attribute source — integer array:

TEST(TensorFromLabelsWithOffsetTest, array_integer_attribute_values_are_converted_to_string_labels) {
    ExecFixture f("tensorFromLabelsWithOffset(attribute(aint),dim,offset)");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor<float>(dim{},offset{})")
                               .add({{"dim", "3"}, {"offset", "0"}}, 1)
                               .add({{"dim", "5"}, {"offset", "1"}}, 1)
                               .add({{"dim", "7"}, {"offset", "2"}}, 1)),
              f.execute());
}

// Tests for attribute source — single-value:

TEST(TensorFromLabelsWithOffsetTest, single_value_integer_attribute_produces_single_cell_at_offset_0) {
    ExecFixture f("tensorFromLabelsWithOffset(attribute(sint),dim,offset)");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor<float>(dim{},offset{})").add({{"dim", "5"}, {"offset", "0"}}, 1)),
              f.execute());
}

TEST(TensorFromLabelsWithOffsetTest, single_value_string_attribute_produces_single_cell_at_offset_0) {
    ExecFixture f("tensorFromLabelsWithOffset(attribute(sstr),dim,offset)");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor<float>(dim{},offset{})").add({{"dim", "foo"}, {"offset", "0"}}, 1)),
              f.execute());
}

TEST(TensorFromLabelsWithOffsetTest, empty_tensor_is_created_if_single_value_integer_attribute_is_undefined) {
    ExecFixture f("tensorFromLabelsWithOffset(attribute(sint),dim,offset)");
    EXPECT_EQ(*make_empty("tensor<float>(dim{},offset{})"), f.execute(2));
}

TEST(TensorFromLabelsWithOffsetTest, empty_tensor_is_created_if_single_value_string_attribute_is_undefined) {
    ExecFixture f("tensorFromLabelsWithOffset(attribute(sstr),dim,offset)");
    EXPECT_EQ(*make_empty("tensor<float>(dim{},offset{})"), f.execute(2));
}

// Tests for error cases:

TEST(TensorFromLabelsWithOffsetTest, empty_tensor_is_created_if_attribute_does_not_exist) {
    ExecFixture f("tensorFromLabelsWithOffset(attribute(null),dim,offset)");
    EXPECT_EQ(*make_empty("tensor<float>(dim{},offset{})"), f.execute());
}

TEST(TensorFromLabelsWithOffsetTest, empty_tensor_is_created_if_attribute_type_is_weighted_set) {
    ExecFixture f("tensorFromLabelsWithOffset(attribute(wsstr),dim,offset)");
    EXPECT_EQ(*make_empty("tensor<float>(dim{},offset{})"), f.execute());
}

GTEST_MAIN_RUN_ALL_TESTS()
