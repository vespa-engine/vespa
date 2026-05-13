// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/test/value_compare.h>
#include <vespa/eval/eval/value.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/floatbase.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/features/tensor_from_structs_feature.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/test/ft_test_app_base.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::feature_t;
using namespace search::fef;
using namespace search::fef::test;
using namespace search::features;
using search::AttributeFactory;
using search::FloatingPointAttribute;
using search::IntegerAttribute;
using search::StringAttribute;
using vespalib::eval::Function;
using vespalib::eval::SimpleValue;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;

using AVC = search::attribute::Config;
using AVBT = search::attribute::BasicType;
using AVCT = search::attribute::CollectionType;
using AttributePtr = search::AttributeVector::SP;
using FTA = FtTestAppBase;
using CollectionType = FieldInfo::CollectionType;

Value::UP make_tensor(const TensorSpec& spec) {
    return SimpleValue::from_spec(spec);
}

Value::UP make_empty(const std::string& type) {
    return make_tensor(TensorSpec(type));
}

struct SetupFixture {
    TensorFromStructsBlueprint blueprint;
    FtIndexEnvironment         indexEnv;
    SetupFixture() : blueprint(), indexEnv() {}
};

TEST(TensorFromStructsTest, require_that_blueprint_can_be_created_from_factory) {
    SetupFixture f;
    EXPECT_TRUE(FTA::assertCreateInstance(f.blueprint, "tensorFromStructs"));
}

TEST(TensorFromStructsTest, require_that_setup_fails_if_source_spec_is_invalid) {
    SetupFixture f;
    FTA::FT_SETUP_FAIL(f.blueprint, f.indexEnv,
                       StringList().add("source(foo)").add("key").add("value").add("double"));
}

TEST(TensorFromStructsTest, require_that_setup_fails_for_query_source) {
    SetupFixture f;
    FTA::FT_SETUP_FAIL(f.blueprint, f.indexEnv, StringList().add("query(foo)").add("key").add("value").add("double"));
}

TEST(TensorFromStructsTest, require_that_setup_fails_with_invalid_cell_type) {
    SetupFixture f;
    FTA::FT_SETUP_FAIL(f.blueprint, f.indexEnv,
                       StringList().add("attribute(items)").add("key").add("value").add("invalid"));
}

struct ExecFixture {
    BlueprintFactory factory;
    FtFeatureTest    test;
    ExecFixture(const std::string& feature) : factory(), test(factory, feature) {
        setupAttributeVectors();
        setup_search_features(factory);
        EXPECT_TRUE(test.setup());
    }
    void setupAttributeVectors() {
        std::vector<AttributePtr> attrs;
        // Create struct array attributes: items.name (string), items.price (float)
        attrs.push_back(AttributeFactory::createAttribute("items.name", AVC(AVBT::STRING, AVCT::ARRAY)));
        attrs.push_back(AttributeFactory::createAttribute("items.price", AVC(AVBT::FLOAT, AVCT::ARRAY)));

        // Create struct array attributes with integer keys: ids.id (int32), ids.score (float)
        attrs.push_back(AttributeFactory::createAttribute("ids.id", AVC(AVBT::INT32, AVCT::ARRAY)));
        attrs.push_back(AttributeFactory::createAttribute("ids.score", AVC(AVBT::FLOAT, AVCT::ARRAY)));

        // Create struct array attributes with integer values: data.key (string), data.count (int32)
        attrs.push_back(AttributeFactory::createAttribute("data.key", AVC(AVBT::STRING, AVCT::ARRAY)));
        attrs.push_back(AttributeFactory::createAttribute("data.count", AVC(AVBT::INT32, AVCT::ARRAY)));

        // Create struct array attributes with mismatched sizes for testing
        attrs.push_back(AttributeFactory::createAttribute("mismatch.key", AVC(AVBT::STRING, AVCT::ARRAY)));
        attrs.push_back(AttributeFactory::createAttribute("mismatch.value", AVC(AVBT::FLOAT, AVCT::ARRAY)));

        // Create weighted set attributes (should fail)
        attrs.push_back(AttributeFactory::createAttribute("wset.key", AVC(AVBT::STRING, AVCT::WSET)));
        attrs.push_back(AttributeFactory::createAttribute("wset.value", AVC(AVBT::FLOAT, AVCT::WSET)));

        // Create single value attributes
        attrs.push_back(AttributeFactory::createAttribute("single.key", AVC(AVBT::STRING, AVCT::SINGLE)));
        attrs.push_back(AttributeFactory::createAttribute("single.value", AVC(AVBT::FLOAT, AVCT::SINGLE)));

        // Multi-key struct array: inv.category (string), inv.brand (string), inv.sku (int32), inv.qty (float)
        attrs.push_back(AttributeFactory::createAttribute("inv.category", AVC(AVBT::STRING, AVCT::ARRAY)));
        attrs.push_back(AttributeFactory::createAttribute("inv.brand", AVC(AVBT::STRING, AVCT::ARRAY)));
        attrs.push_back(AttributeFactory::createAttribute("inv.sku", AVC(AVBT::INT32, AVCT::ARRAY)));
        attrs.push_back(AttributeFactory::createAttribute("inv.qty", AVC(AVBT::FLOAT, AVCT::ARRAY)));

        // Mixed collection-type struct for negative test (one ARRAY key, one SINGLE key, ARRAY value)
        attrs.push_back(AttributeFactory::createAttribute("mixinv.a", AVC(AVBT::STRING, AVCT::ARRAY)));
        attrs.push_back(AttributeFactory::createAttribute("mixinv.b", AVC(AVBT::STRING, AVCT::SINGLE)));
        attrs.push_back(AttributeFactory::createAttribute("mixinv.qty", AVC(AVBT::FLOAT, AVCT::ARRAY)));

        // 5-key struct array: pent.a/b/c/d/e (string) + pent.val (float)
        attrs.push_back(AttributeFactory::createAttribute("pent.a", AVC(AVBT::STRING, AVCT::ARRAY)));
        attrs.push_back(AttributeFactory::createAttribute("pent.b", AVC(AVBT::STRING, AVCT::ARRAY)));
        attrs.push_back(AttributeFactory::createAttribute("pent.c", AVC(AVBT::STRING, AVCT::ARRAY)));
        attrs.push_back(AttributeFactory::createAttribute("pent.d", AVC(AVBT::STRING, AVCT::ARRAY)));
        attrs.push_back(AttributeFactory::createAttribute("pent.e", AVC(AVBT::STRING, AVCT::ARRAY)));
        attrs.push_back(AttributeFactory::createAttribute("pent.val", AVC(AVBT::FLOAT, AVCT::ARRAY)));

        // Register attributes in index environment
        test.getIndexEnv()
            .getBuilder()
            .addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "items.name")
            .addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "items.price")
            .addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "items.missing")
            .addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "ids.id")
            .addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "ids.score")
            .addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "data.key")
            .addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "data.count")
            .addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "mismatch.key")
            .addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "mismatch.value")
            .addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "wset.key")
            .addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "wset.value")
            .addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "single.key")
            .addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "single.value")
            .addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "missing.key")
            .addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "missing.value")
            .addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "inv.category")
            .addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "inv.brand")
            .addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "inv.sku")
            .addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "inv.qty")
            .addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "mixinv.a")
            .addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "mixinv.b")
            .addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "mixinv.qty")
            .addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "pent.a")
            .addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "pent.b")
            .addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "pent.c")
            .addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "pent.d")
            .addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "pent.e")
            .addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "pent.val");

        for (const auto& attr : attrs) {
            attr->addReservedDoc();
            attr->addDocs(3);
            test.getIndexEnv().getAttributeMap().add(attr);
        }

        // Setup document 1: items with 3 products
        StringAttribute& itemsName = dynamic_cast<StringAttribute&>(*attrs[0]);
        itemsName.append(1, "apple", 0);
        itemsName.append(1, "banana", 0);
        itemsName.append(1, "cherry", 0);

        FloatingPointAttribute& itemsPrice = dynamic_cast<FloatingPointAttribute&>(*attrs[1]);
        itemsPrice.append(1, 1.5, 0);
        itemsPrice.append(1, 0.75, 0);
        itemsPrice.append(1, 2.25, 0);

        // Setup document 1: ids with integer keys
        IntegerAttribute& idsId = dynamic_cast<IntegerAttribute&>(*attrs[2]);
        idsId.append(1, 100, 0);
        idsId.append(1, 200, 0);
        idsId.append(1, 300, 0);

        FloatingPointAttribute& idsScore = dynamic_cast<FloatingPointAttribute&>(*attrs[3]);
        idsScore.append(1, 10.5, 0);
        idsScore.append(1, 20.75, 0);
        idsScore.append(1, 30.25, 0);

        // Setup document 1: data with integer values
        StringAttribute& dataKey = dynamic_cast<StringAttribute&>(*attrs[4]);
        dataKey.append(1, "x", 0);
        dataKey.append(1, "y", 0);
        dataKey.append(1, "z", 0);

        IntegerAttribute& dataCount = dynamic_cast<IntegerAttribute&>(*attrs[5]);
        dataCount.append(1, 42, 0);
        dataCount.append(1, 17, 0);
        dataCount.append(1, 99, 0);

        // Setup document 1: mismatched array sizes
        StringAttribute& mismatchKey = dynamic_cast<StringAttribute&>(*attrs[6]);
        mismatchKey.append(1, "one", 0);
        mismatchKey.append(1, "two", 0);
        mismatchKey.append(1, "three", 0);
        mismatchKey.append(1, "four", 0);
        mismatchKey.append(1, "five", 0);

        FloatingPointAttribute& mismatchValue = dynamic_cast<FloatingPointAttribute&>(*attrs[7]);
        mismatchValue.append(1, 1.0, 0);
        mismatchValue.append(1, 2.0, 0);

        // Setup document 1: single value attributes
        StringAttribute& singleKey = dynamic_cast<StringAttribute&>(*attrs[10]);
        singleKey.update(1, "single_key");

        FloatingPointAttribute& singleValue = dynamic_cast<FloatingPointAttribute&>(*attrs[11]);
        singleValue.update(1, 42.5);

        // Setup document 1: multi-key struct array (inv)
        StringAttribute& invCategory = dynamic_cast<StringAttribute&>(*attrs[12]);
        invCategory.append(1, "food", 0);
        invCategory.append(1, "food", 0);
        invCategory.append(1, "drink", 0);

        StringAttribute& invBrand = dynamic_cast<StringAttribute&>(*attrs[13]);
        invBrand.append(1, "acme", 0);
        invBrand.append(1, "globex", 0);
        invBrand.append(1, "acme", 0);

        IntegerAttribute& invSku = dynamic_cast<IntegerAttribute&>(*attrs[14]);
        invSku.append(1, 10, 0);
        invSku.append(1, 20, 0);
        invSku.append(1, 30, 0);

        FloatingPointAttribute& invQty = dynamic_cast<FloatingPointAttribute&>(*attrs[15]);
        invQty.append(1, 1.5, 0);
        invQty.append(1, 2.0, 0);
        invQty.append(1, 3.5, 0);

        // Setup document 1: mixed collection-type struct (for negative test)
        StringAttribute& mixinvA = dynamic_cast<StringAttribute&>(*attrs[16]);
        mixinvA.append(1, "x", 0);

        StringAttribute& mixinvB = dynamic_cast<StringAttribute&>(*attrs[17]);
        mixinvB.update(1, "y");

        FloatingPointAttribute& mixinvQty = dynamic_cast<FloatingPointAttribute&>(*attrs[18]);
        mixinvQty.append(1, 9.0, 0);

        // Setup document 1: 5-key struct array (pent)
        StringAttribute&        pentA = dynamic_cast<StringAttribute&>(*attrs[19]);
        StringAttribute&        pentB = dynamic_cast<StringAttribute&>(*attrs[20]);
        StringAttribute&        pentC = dynamic_cast<StringAttribute&>(*attrs[21]);
        StringAttribute&        pentD = dynamic_cast<StringAttribute&>(*attrs[22]);
        StringAttribute&        pentE = dynamic_cast<StringAttribute&>(*attrs[23]);
        FloatingPointAttribute& pentVal = dynamic_cast<FloatingPointAttribute&>(*attrs[24]);
        pentA.append(1, "a1", 0);
        pentB.append(1, "b1", 0);
        pentC.append(1, "c1", 0);
        pentD.append(1, "d1", 0);
        pentE.append(1, "e1", 0);
        pentVal.append(1, 5.0, 0);
        pentA.append(1, "a2", 0);
        pentB.append(1, "b2", 0);
        pentC.append(1, "c2", 0);
        pentD.append(1, "d2", 0);
        pentE.append(1, "e2", 0);
        pentVal.append(1, 7.5, 0);

        // Setup document 2: empty arrays
        // (no appends, so arrays remain empty)

        // Setup document 3: single element arrays
        itemsName.append(3, "grape", 0);
        itemsPrice.append(3, 3.5, 0);
        invCategory.append(3, "food", 0);
        invBrand.append(3, "acme", 0);
        invSku.append(3, 99, 0);
        invQty.append(3, 7.5, 0);

        for (const auto& attr : attrs) {
            attr->commit();
        }
    }
    const Value& extractTensor(uint32_t docid) { return test.resolveObjectFeature(docid); }
    const Value& execute(uint32_t docid = 1) { return extractTensor(docid); }
};

// Tests for basic functionality with string keys and float values

TEST(TensorFromStructsTest, require_that_struct_array_with_string_keys_and_float_values_creates_tensor) {
    ExecFixture f("tensorFromStructs(attribute(items),name,price,float)");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor<float>(name{})")
                               .add({{"name", "apple"}}, 1.5)
                               .add({{"name", "banana"}}, 0.75)
                               .add({{"name", "cherry"}}, 2.25)),
              f.execute());
}

TEST(TensorFromStructsTest, require_that_struct_array_with_string_keys_and_float_values_creates_double_tensor) {
    ExecFixture f("tensorFromStructs(attribute(items),name,price,double)");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor(name{})")
                               .add({{"name", "apple"}}, 1.5)
                               .add({{"name", "banana"}}, 0.75)
                               .add({{"name", "cherry"}}, 2.25)),
              f.execute());
}

TEST(TensorFromStructsTest, require_that_struct_array_with_integer_keys_and_float_values_creates_tensor) {
    ExecFixture f("tensorFromStructs(attribute(ids),id,score,float)");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor<float>(id{})")
                               .add({{"id", "100"}}, 10.5)
                               .add({{"id", "200"}}, 20.75)
                               .add({{"id", "300"}}, 30.25)),
              f.execute());
}

TEST(TensorFromStructsTest, require_that_struct_array_with_string_keys_and_integer_values_creates_tensor) {
    ExecFixture f("tensorFromStructs(attribute(data),key,count,float)");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor<float>(key{})")
                               .add({{"key", "x"}}, 42.0)
                               .add({{"key", "y"}}, 17.0)
                               .add({{"key", "z"}}, 99.0)),
              f.execute());
}

TEST(TensorFromStructsTest, require_that_struct_array_with_string_keys_and_integer_values_creates_double_tensor) {
    ExecFixture f("tensorFromStructs(attribute(data),key,count,double)");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor(key{})")
                               .add({{"key", "x"}}, 42.0)
                               .add({{"key", "y"}}, 17.0)
                               .add({{"key", "z"}}, 99.0)),
              f.execute());
}

// Tests for edge cases

TEST(TensorFromStructsTest, require_that_mismatched_array_sizes_use_minimum_length) {
    ExecFixture f("tensorFromStructs(attribute(mismatch),key,value,float)");
    // Should only create 2 tensor cells (min of 5 keys and 2 values)
    EXPECT_EQ(*make_tensor(TensorSpec("tensor<float>(key{})").add({{"key", "one"}}, 1.0).add({{"key", "two"}}, 2.0)),
              f.execute());
}

TEST(TensorFromStructsTest, require_that_empty_arrays_create_empty_tensor) {
    ExecFixture f("tensorFromStructs(attribute(items),name,price,float)");
    EXPECT_EQ(*make_empty("tensor<float>(name{})"), f.execute(2));
}

TEST(TensorFromStructsTest, require_that_single_element_arrays_create_single_cell_tensor) {
    ExecFixture f("tensorFromStructs(attribute(items),name,price,float)");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor<float>(name{})").add({{"name", "grape"}}, 3.5)), f.execute(3));
}

TEST(TensorFromStructsTest, require_that_single_value_attributes_create_single_cell_tensor) {
    ExecFixture f("tensorFromStructs(attribute(single),key,value,float)");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor<float>(key{})").add({{"key", "single_key"}}, 42.5)), f.execute());
}

TEST(TensorFromStructsTest, require_that_missing_key_attribute_creates_empty_tensor) {
    ExecFixture f("tensorFromStructs(attribute(missing),key,value,float)");
    EXPECT_EQ(*make_empty("tensor<float>(key{})"), f.execute());
}

TEST(TensorFromStructsTest, require_that_missing_value_attribute_creates_empty_tensor) {
    ExecFixture f("tensorFromStructs(attribute(items),name,missing,float)");
    EXPECT_EQ(*make_empty("tensor<float>(name{})"), f.execute());
}

TEST(TensorFromStructsTest, require_that_weighted_set_attributes_create_empty_tensor) {
    ExecFixture f("tensorFromStructs(attribute(wset),key,value,float)");
    EXPECT_EQ(*make_empty("tensor<float>(key{})"), f.execute());
}

// Tests for different dimension names

TEST(TensorFromStructsTest, require_that_dimension_name_matches_key_field_parameter) {
    ExecFixture f("tensorFromStructs(attribute(items),name,price,float)");
    const auto& result = f.execute();
    EXPECT_EQ("tensor<float>(name{})", result.type().to_spec());
}

TEST(TensorFromStructsTest, require_that_custom_dimension_name_is_used) {
    ExecFixture f("tensorFromStructs(attribute(ids),id,score,float)");
    const auto& result = f.execute();
    EXPECT_EQ("tensor<float>(id{})", result.type().to_spec());
}

// Tests for cell type preservation

TEST(TensorFromStructsTest, require_that_float_cell_type_is_preserved) {
    ExecFixture f("tensorFromStructs(attribute(items),name,price,float)");
    const auto& result = f.execute();
    EXPECT_TRUE(result.type().to_spec().find("tensor<float>") == 0);
}

TEST(TensorFromStructsTest, require_that_double_cell_type_is_preserved) {
    ExecFixture f("tensorFromStructs(attribute(items),name,price,double)");
    const auto& result = f.execute();
    EXPECT_TRUE(result.type().to_spec().find("tensor(") == 0 || result.type().to_spec().find("tensor<double>") == 0);
}

// Tests for multi-key (2 or 3 key fields)

TEST(TensorFromStructsTest, require_that_two_string_keys_create_2d_sparse_tensor) {
    ExecFixture f("tensorFromStructs(attribute(inv),category,brand,qty,float)");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor<float>(brand{},category{})")
                               .add({{"category", "food"}, {"brand", "acme"}}, 1.5)
                               .add({{"category", "food"}, {"brand", "globex"}}, 2.0)
                               .add({{"category", "drink"}, {"brand", "acme"}}, 3.5)),
              f.execute());
}

TEST(TensorFromStructsTest, require_that_string_and_integer_keys_create_2d_sparse_tensor) {
    ExecFixture f("tensorFromStructs(attribute(inv),category,sku,qty,float)");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor<float>(category{},sku{})")
                               .add({{"category", "food"}, {"sku", "10"}}, 1.5)
                               .add({{"category", "food"}, {"sku", "20"}}, 2.0)
                               .add({{"category", "drink"}, {"sku", "30"}}, 3.5)),
              f.execute());
}

TEST(TensorFromStructsTest, require_that_three_keys_create_3d_sparse_tensor) {
    ExecFixture f("tensorFromStructs(attribute(inv),category,brand,sku,qty,float)");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor<float>(brand{},category{},sku{})")
                               .add({{"category", "food"}, {"brand", "acme"}, {"sku", "10"}}, 1.5)
                               .add({{"category", "food"}, {"brand", "globex"}, {"sku", "20"}}, 2.0)
                               .add({{"category", "drink"}, {"brand", "acme"}, {"sku", "30"}}, 3.5)),
              f.execute());
}

TEST(TensorFromStructsTest, require_that_empty_arrays_create_empty_multi_dim_tensor) {
    ExecFixture f("tensorFromStructs(attribute(inv),category,brand,qty,float)");
    EXPECT_EQ(*make_empty("tensor<float>(brand{},category{})"), f.execute(2));
}

TEST(TensorFromStructsTest, require_that_single_element_arrays_create_single_cell_multi_dim_tensor) {
    ExecFixture f("tensorFromStructs(attribute(inv),category,brand,qty,float)");
    EXPECT_EQ(
        *make_tensor(
            TensorSpec("tensor<float>(brand{},category{})").add({{"category", "food"}, {"brand", "acme"}}, 7.5)),
        f.execute(3));
}

TEST(TensorFromStructsTest, require_that_duplicate_key_field_name_fails_setup) {
    SetupFixture f;
    FTA::FT_SETUP_FAIL(f.blueprint, f.indexEnv,
                       StringList().add("attribute(inv)").add("category").add("category").add("qty").add("float"));
}

TEST(TensorFromStructsTest, require_that_collection_type_mismatch_across_keys_creates_empty_tensor) {
    ExecFixture f("tensorFromStructs(attribute(mixinv),a,b,qty,float)");
    EXPECT_EQ(*make_empty("tensor<float>(a{},b{})"), f.execute());
}

// Tests for 4 and 5 key fields

TEST(TensorFromStructsTest, require_that_four_keys_create_4d_sparse_tensor) {
    ExecFixture f("tensorFromStructs(attribute(pent),a,b,c,d,val,float)");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor<float>(a{},b{},c{},d{})")
                               .add({{"a", "a1"}, {"b", "b1"}, {"c", "c1"}, {"d", "d1"}}, 5.0)
                               .add({{"a", "a2"}, {"b", "b2"}, {"c", "c2"}, {"d", "d2"}}, 7.5)),
              f.execute());
}

TEST(TensorFromStructsTest, require_that_five_keys_create_5d_sparse_tensor) {
    ExecFixture f("tensorFromStructs(attribute(pent),a,b,c,d,e,val,float)");
    EXPECT_EQ(*make_tensor(TensorSpec("tensor<float>(a{},b{},c{},d{},e{})")
                               .add({{"a", "a1"}, {"b", "b1"}, {"c", "c1"}, {"d", "d1"}, {"e", "e1"}}, 5.0)
                               .add({{"a", "a2"}, {"b", "b2"}, {"c", "c2"}, {"d", "d2"}, {"e", "e2"}}, 7.5)),
              f.execute());
}

TEST(TensorFromStructsTest, require_that_five_key_mapped_dimensions_are_sorted_alphabetically_in_type_spec) {
    ExecFixture f("tensorFromStructs(attribute(pent),e,d,c,b,a,val,float)");
    const auto& result = f.execute();
    EXPECT_EQ("tensor<float>(a{},b{},c{},d{},e{})", result.type().to_spec());
}

GTEST_MAIN_RUN_ALL_TESTS()
