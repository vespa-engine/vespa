// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/extendableattributes.h>
#include <vespa/searchlib/attribute/single_raw_ext_attribute.h>
#include <vespa/searchlib/tensor/tensor_ext_attribute.h>
#include <vespa/searchlib/tensor/vector_bundle.h>
#include <vespa/vespalib/stllike/asciistream.h>

using search::attribute::Config;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::SingleRawExtAttribute;
using search::tensor::TensorExtAttribute;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;
using vespalib::eval::ValueType;

namespace search {

std::vector<char> as_vector(vespalib::stringref value) {
    return {value.data(), value.data() + value.size()};
}

std::vector<char> as_vector(vespalib::ConstArrayRef<char> value) {
    return {value.data(), value.data() + value.size()};
}

std::vector<double> as_vector(vespalib::ConstArrayRef<double> value) {
    return {value.data(), value.data() + value.size()};
}

vespalib::string vec_2d_spec("tensor(x[2])");
vespalib::string vec_mixed_2d_spec("tensor(a{},x[2])");

TensorSpec
vec_2d(double x0, double x1)
{
    return TensorSpec(vec_2d_spec).add({{"x", 0}}, x0).add({{"x", 1}}, x1);
}

TensorSpec
vec_mixed_2d(std::vector<std::vector<double>> val)
{
    TensorSpec spec(vec_mixed_2d_spec);
    for (uint32_t a = 0; a < val.size(); ++a) {
        vespalib::asciistream a_stream;
        a_stream << a;
        vespalib::string a_as_string = a_stream.str();
        for (uint32_t x = 0; x < val[a].size(); ++x) {
            spec.add({{"a", a_as_string.c_str()},{"x", x}}, val[a][x]);
	}
    }
    return spec;
}

void add_doc(AttributeVector& attr, uint32_t exp_docid)
{
    uint32_t docid(0);
    EXPECT_EQ(exp_docid, attr.getNumDocs());
    attr.addDoc(docid);
    EXPECT_EQ(exp_docid, docid);
    EXPECT_EQ(exp_docid + 1, attr.getNumDocs());
}

class ExtendAttributeTest : public ::testing::Test
{
    std::vector<std::unique_ptr<Value>> _tensors;
protected:
    ExtendAttributeTest() = default;
    ~ExtendAttributeTest() override = default;
    template <typename Attribute>
    void testExtendInteger(Attribute & attr);
    template <typename Attribute>
    void testExtendFloat(Attribute & attr);
    template <typename Attribute>
    void testExtendString(Attribute & attr);
    void testExtendRaw(AttributeVector& attr);
    void testExtendTensor(AttributeVector& attr);
    const Value& create_tensor(const TensorSpec &spec);
};

const Value&
ExtendAttributeTest::create_tensor(const TensorSpec &spec)
{
    auto value = value_from_spec(spec, FastValueBuilderFactory::get());
    _tensors.emplace_back(std::move(value));
    return *_tensors.back();
}

template <typename Attribute>
void ExtendAttributeTest::testExtendInteger(Attribute & attr)
{
    add_doc(attr, 0);
    attr.add(1, 10);
    EXPECT_EQ(attr.getInt(0), 1);
    attr.add(2, 20);
    EXPECT_EQ(attr.getInt(0), attr.hasMultiValue() ? 1 : 2);
    if (attr.hasMultiValue()) {
        AttributeVector::WeightedInt v[2];
        EXPECT_EQ((static_cast<AttributeVector &>(attr)).get(0, v, 2), 2u);
        EXPECT_EQ(v[0].getValue(), 1);
        EXPECT_EQ(v[1].getValue(), 2);
        if (attr.hasWeightedSetType()) {
            EXPECT_EQ(v[0].getWeight(), 10);
            EXPECT_EQ(v[1].getWeight(), 20);
        }
    }
    add_doc(attr, 1);
    attr.add(3, 30);
    EXPECT_EQ(attr.getInt(1), 3);
    if (attr.hasMultiValue()) {
        AttributeVector::WeightedInt v[1];
        EXPECT_EQ((static_cast<AttributeVector &>(attr)).get(1, v, 1), 1u);
        EXPECT_EQ(v[0].getValue(), 3);
        if (attr.hasWeightedSetType()) {
            EXPECT_EQ(v[0].getWeight(), 30);
        }
    }
}

template <typename Attribute>
void ExtendAttributeTest::testExtendFloat(Attribute & attr)
{
    add_doc(attr, 0);
    attr.add(1.7, 10);
    EXPECT_EQ(attr.getInt(0), 1);
    EXPECT_EQ(attr.getFloat(0), 1.7);
    attr.add(2.3, 20);
    EXPECT_EQ(attr.getFloat(0), attr.hasMultiValue() ? 1.7 : 2.3);
    if (attr.hasMultiValue()) {
        AttributeVector::WeightedFloat v[2];
        EXPECT_EQ((static_cast<AttributeVector &>(attr)).get(0, v, 2), 2u);
        EXPECT_EQ(v[0].getValue(), 1.7);
        EXPECT_EQ(v[1].getValue(), 2.3);
        if (attr.hasWeightedSetType()) {
            EXPECT_EQ(v[0].getWeight(), 10);
            EXPECT_EQ(v[1].getWeight(), 20);
        }
    }
    add_doc(attr, 1);
    attr.add(3.6, 30);
    EXPECT_EQ(attr.getFloat(1), 3.6);
    if (attr.hasMultiValue()) {
        AttributeVector::WeightedFloat v[1];
        EXPECT_EQ((static_cast<AttributeVector &>(attr)).get(1, v, 1), 1u);
        EXPECT_EQ(v[0].getValue(), 3.6);
        if (attr.hasWeightedSetType()) {
            EXPECT_EQ(v[0].getWeight(), 30);
        }
    }
}

template <typename Attribute>
void ExtendAttributeTest::testExtendString(Attribute & attr)
{
    add_doc(attr, 0);
    attr.add("1.7", 10);
    auto buf = attr.get_raw(0);
    EXPECT_EQ(std::string(buf.data(), buf.size()), "1.7");
    attr.add("2.3", 20);
    buf = attr.get_raw(0);
    EXPECT_EQ(std::string(buf.data(), buf.size()), attr.hasMultiValue() ? "1.7" : "2.3");
    if (attr.hasMultiValue()) {
        AttributeVector::WeightedString v[2];
        EXPECT_EQ((static_cast<AttributeVector &>(attr)).get(0, v, 2), 2u);
        EXPECT_EQ(v[0].getValue(), "1.7");
        EXPECT_EQ(v[1].getValue(), "2.3");
        if (attr.hasWeightedSetType()) {
            EXPECT_EQ(v[0].getWeight(), 10);
            EXPECT_EQ(v[1].getWeight(), 20);
        }
    }
    add_doc(attr, 1);
    attr.add("3.6", 30);
    buf = attr.get_raw(1);
    EXPECT_EQ(std::string(buf.data(), buf.size()), "3.6");
    if (attr.hasMultiValue()) {
        AttributeVector::WeightedString v[1];
        EXPECT_EQ((static_cast<AttributeVector &>(attr)).get(1, v, 1), 1u);
        EXPECT_EQ(v[0].getValue(), "3.6");
        if (attr.hasWeightedSetType()) {
            EXPECT_EQ(v[0].getWeight(), 30);
        }
    }
}

void ExtendAttributeTest::testExtendRaw(AttributeVector& attr)
{
    std::vector<char> empty;
    std::vector<char> zeros{10, 0, 0, 11};
    auto* ext_attr = attr.getExtendInterface();
    EXPECT_NE(nullptr, ext_attr);
    add_doc(attr, 0);
    ext_attr->add(as_vector("1.7"));
    auto buf = attr.get_raw(0);
    EXPECT_EQ(as_vector("1.7"), as_vector(buf));
    ext_attr->add(vespalib::ConstArrayRef<char>(as_vector("2.3")));
    buf = attr.get_raw(0);
    EXPECT_EQ(as_vector("2.3"), as_vector(buf));
    add_doc(attr, 1);
    ext_attr->add(as_vector("3.6"));
    buf = attr.get_raw(1);
    EXPECT_EQ(as_vector("3.6"), as_vector(buf));
    buf = attr.get_raw(0);
    EXPECT_EQ(as_vector("2.3"), as_vector(buf));
    add_doc(attr, 2);
    ext_attr->add(zeros);
    buf = attr.get_raw(2);
    EXPECT_EQ(zeros, as_vector(buf));
    add_doc(attr, 3);
    buf = attr.get_raw(3);
    EXPECT_EQ(empty, as_vector(buf));
    add_doc(attr, 4);
    ext_attr->add(empty);
    buf = attr.get_raw(4);
    EXPECT_EQ(empty, as_vector(buf));
}

void ExtendAttributeTest::testExtendTensor(AttributeVector& attr)
{
    std::vector<double> empty_cells{0.0, 0.0};
    std::vector<double> spec0_dense_cells{1.0, 2.0};
    std::vector<double> spec0_mixed_cells0{3.0, 4.0};
    std::vector<double> spec0_mixed_cells1{5.0, 6.0};
    bool dense = attr.getConfig().tensorType().is_dense();
    auto* ext_attr = attr.getExtendInterface();
    EXPECT_NE(nullptr, ext_attr);
    auto* tensor_attr = attr.asTensorAttribute();
    EXPECT_NE(nullptr, tensor_attr);
    add_doc(attr, 0);
    TensorSpec spec0 = dense ? vec_2d(1.0, 2.0) : vec_mixed_2d({{3.0, 4.0}, {5.0, 6.0}});
    EXPECT_TRUE(ext_attr->add(create_tensor(spec0)));
    auto tensor = tensor_attr->getTensor(0);
    EXPECT_NE(nullptr, tensor.get());
    EXPECT_EQ(spec0, TensorSpec::from_value(*tensor));
    EXPECT_EQ(dense, tensor_attr->supports_extract_cells_ref());
    if (dense) {
        EXPECT_EQ(spec0_dense_cells, as_vector(tensor_attr->extract_cells_ref(0).typify<double>()));
    }
    EXPECT_TRUE(tensor_attr->supports_get_tensor_ref());
    EXPECT_EQ(spec0, TensorSpec::from_value(tensor_attr->get_tensor_ref(0)));
    EXPECT_FALSE(tensor_attr->supports_get_serialized_tensor_ref());
    auto vectors = tensor_attr->get_vectors(0);
    if (dense) {
        EXPECT_EQ(1, vectors.subspaces());
        EXPECT_EQ(spec0_dense_cells, as_vector(vectors.cells(0).typify<double>()));
        EXPECT_EQ(spec0_dense_cells, as_vector(tensor_attr->get_vector(0, 0).typify<double>()));
        EXPECT_EQ(empty_cells, as_vector(tensor_attr->get_vector(0, 1).typify<double>()));
    } else {
        EXPECT_EQ(2, vectors.subspaces());
        EXPECT_EQ(spec0_mixed_cells0, as_vector(vectors.cells(0).typify<double>()));
        EXPECT_EQ(spec0_mixed_cells1, as_vector(vectors.cells(1).typify<double>()));
        EXPECT_EQ(spec0_mixed_cells0, as_vector(tensor_attr->get_vector(0, 0).typify<double>()));
        EXPECT_EQ(spec0_mixed_cells1, as_vector(tensor_attr->get_vector(0, 1).typify<double>()));
        EXPECT_EQ(empty_cells, as_vector(tensor_attr->get_vector(0, 2).typify<double>()));
    }
    add_doc(attr, 1);
    vectors = tensor_attr->get_vectors(1);
    EXPECT_EQ(0, vectors.subspaces());
    EXPECT_EQ(empty_cells, as_vector(tensor_attr->get_vector(1, 0).typify<double>()));
    EXPECT_EQ(nullptr, tensor_attr->getTensor(1).get());
}

TEST_F(ExtendAttributeTest, single_integer_ext_attribute)
{
    SingleIntegerExtAttribute siattr("si1");
    EXPECT_TRUE( ! siattr.hasMultiValue() );
    testExtendInteger(siattr);
}

TEST_F(ExtendAttributeTest, array_integer_ext_attribute)
{
    MultiIntegerExtAttribute miattr("mi1");
    EXPECT_TRUE( miattr.hasMultiValue() );
    testExtendInteger(miattr);
}

TEST_F(ExtendAttributeTest, weighted_set_integer_ext_attribute)
{
    WeightedSetIntegerExtAttribute wsiattr("wsi1");
    EXPECT_TRUE( wsiattr.hasWeightedSetType() );
    testExtendInteger(wsiattr);
}

TEST_F(ExtendAttributeTest, single_float_ext_attribute)
{
    SingleFloatExtAttribute sdattr("sd1");
    EXPECT_TRUE( ! sdattr.hasMultiValue() );
    testExtendFloat(sdattr);
}

TEST_F(ExtendAttributeTest, array_float_ext_attribute)
{
    MultiFloatExtAttribute mdattr("md1");
    EXPECT_TRUE( mdattr.hasMultiValue() );
    testExtendFloat(mdattr);
}

TEST_F(ExtendAttributeTest, weighted_set_float_ext_attribute)
{
    WeightedSetFloatExtAttribute wsdattr("wsd1");
    EXPECT_TRUE( wsdattr.hasWeightedSetType() );
    testExtendFloat(wsdattr);
}

TEST_F(ExtendAttributeTest, single_string_ext_attribute)
{
    SingleStringExtAttribute ssattr("ss1");
    EXPECT_TRUE( ! ssattr.hasMultiValue() );
    testExtendString(ssattr);
}

TEST_F(ExtendAttributeTest, array_string_ext_attribute)
{
    MultiStringExtAttribute msattr("ms1");
    EXPECT_TRUE( msattr.hasMultiValue() );
    testExtendString(msattr);
}

TEST_F(ExtendAttributeTest, weighted_set_string_ext_attribute)
{
    WeightedSetStringExtAttribute wssattr("wss1");
    EXPECT_TRUE( wssattr.hasWeightedSetType() );
    testExtendString(wssattr);
}

TEST_F(ExtendAttributeTest, single_raw_ext_attribute)
{
    SingleRawExtAttribute srattr("sr1");
    EXPECT_TRUE(! srattr.hasMultiValue());
    testExtendRaw(srattr);
}

TEST_F(ExtendAttributeTest, tensor_ext_attribute_dense)
{
    Config cfg(BasicType::TENSOR, CollectionType::SINGLE);
    cfg.setTensorType(ValueType::from_spec(vec_2d_spec));
    TensorExtAttribute tattr("td1", cfg);
    EXPECT_TRUE(! tattr.hasMultiValue());
    testExtendTensor(tattr);
}

TEST_F(ExtendAttributeTest, tensor_ext_attribute_mixed)
{
    Config cfg(BasicType::TENSOR, CollectionType::SINGLE);
    cfg.setTensorType(ValueType::from_spec(vec_mixed_2d_spec));
    TensorExtAttribute tattr("tm1", cfg);
    EXPECT_TRUE(! tattr.hasMultiValue());
    testExtendTensor(tattr);
}

}

GTEST_MAIN_RUN_ALL_TESTS()
