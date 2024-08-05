// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/test/value_compare.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/test/ftlib.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/tensor/tensor_attribute.h>
#include <vespa/searchlib/tensor/direct_tensor_attribute.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/objects/nbostream.h>

using search::feature_t;
using namespace search::fef;
using namespace search::fef::indexproperties;
using namespace search::fef::test;
using namespace search::features;
using search::AttributeFactory;
using search::tensor::TensorAttribute;
using search::tensor::DirectTensorAttribute;
using search::AttributeVector;
using vespalib::eval::Function;
using vespalib::eval::SimpleValue;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;
using vespalib::eval::ValueType;
using vespalib::eval::spec_from_value;

using AVC = search::attribute::Config;
using AVBT = search::attribute::BasicType;
using AVCT = search::attribute::CollectionType;
using AttributePtr = search::AttributeVector::SP;
using CollectionType = FieldInfo::CollectionType;

namespace
{

Value::UP make_empty(const vespalib::string &type) {
    return SimpleValue::from_spec(TensorSpec(type));
}

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
        EXPECT_TRUE(test.setup());
    }
    void addAttributeField(const vespalib::string &attrName) {
        test.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, attrName);
    }
    AttributeVector::SP createStringAttribute(const vespalib::string &attrName) {
        addAttributeField(attrName);
        return AttributeFactory::createAttribute(attrName, AVC(AVBT::STRING, AVCT::SINGLE));
    }
    AttributeVector::SP createTensorAttribute(const vespalib::string &attrName,
                                              const vespalib::string &type,
                                              bool direct = false)
    {
        addAttributeField(attrName);
        AVC config(AVBT::TENSOR, AVCT::SINGLE);
        config.setTensorType(ValueType::from_spec(type));
        config.setFastSearch(direct);
        return AttributeFactory::createAttribute(attrName, config);
    }
    void setAttributeTensorType(const vespalib::string &attrName, const vespalib::string &type) {
        type::Attribute::set(test.getIndexEnv().getProperties(), attrName, type);
    }
    void setQueryTensorType(const vespalib::string &queryFeatureName, const vespalib::string &type) {
        type::QueryFeature::set(test.getIndexEnv().getProperties(), queryFeatureName, type);
    }
    void setQueryTensorDefault(const vespalib::string &tensorName, const vespalib::string &expr) {
        vespalib::string key = "query(" + tensorName + ")";
        test.getIndexEnv().getProperties().add(key, expr);
    }
    void setupAttributeVectors() {
        std::vector<AttributePtr> attrs;
        attrs.push_back(createTensorAttribute("tensorattr", "tensor(x{})"));
        attrs.push_back(createTensorAttribute("directattr", "tensor(x{})", true));
        attrs.push_back(createStringAttribute("singlestr"));
        attrs.push_back(createTensorAttribute("wrongtype", "tensor(y{})"));
        addAttributeField("null");
        setAttributeTensorType("tensorattr", "tensor(x{})");
        setAttributeTensorType("directattr", "tensor(x{})");
        setAttributeTensorType("wrongtype", "tensor(x{})");
        setAttributeTensorType("null", "tensor(x{})");

        for (const auto &attr : attrs) {
            attr->addReservedDoc();
            attr->addDocs(2);
            attr->clearDoc(1);
            attr->clearDoc(2);
            attr->commit();
            test.getIndexEnv().getAttributeMap().add(attr);
        }

        TensorAttribute *tensorAttr =
            dynamic_cast<TensorAttribute *>(attrs[0].get());
        DirectTensorAttribute *directAttr =
            dynamic_cast<DirectTensorAttribute *>(attrs[1].get());

        auto doc_tensor = SimpleValue::from_spec(TensorSpec("tensor(x{})")
                                                 .add({{"x", "a"}}, 3)
                                                 .add({{"x", "b"}}, 5)
                                                 .add({{"x", "c"}}, 7));
        tensorAttr->setTensor(1, *doc_tensor);
        directAttr->setTensor(1, *doc_tensor);

        for (const auto &attr : attrs) {
            attr->commit();
        }
    }
    void setQueryTensor(const vespalib::string &tensorName,
                        const vespalib::string &tensorTypeSpec,
                        std::unique_ptr<Value> tensor)
    {
        vespalib::nbostream stream;
        encode_value(*tensor, stream);
        test.getQueryEnv().getProperties().add(tensorName,
                std::string_view(stream.peek(), stream.size()));
        setQueryTensorType(tensorName, tensorTypeSpec);
    }

    void setupQueryEnvironment() {
        setQueryTensor("tensorquery",
                       "tensor(q{})",
                       SimpleValue::from_spec(TensorSpec("tensor(q{})")
                                              .add({{"q", "d"}}, 11 )
                                              .add({{"q", "e"}}, 13 )
                                              .add({{"q", "f"}}, 17 )));
        setQueryTensor("mappedtensorquery",
                       "tensor(x[2])",
                       SimpleValue::from_spec(TensorSpec("tensor(x{},y{})")
                                              .add({{"x", "0"},{"y", "0"}}, 11 )
                                              .add({{"x", "0"},{"y", "1"}}, 13 )
                                              .add({{"x", "1"},{"y", "0"}}, 17 )));
        setQueryTensorType("null", "tensor(q{})");
        setQueryTensorType("with_default", "tensor(x[3])");
        setQueryTensorDefault("with_default", "tensor(x[3])(x+1)");
    }
    const Value &extractTensor(uint32_t docid) {
        Value::CREF value = test.resolveObjectFeature(docid);
        EXPECT_TRUE(value.get().type().has_dimensions());
        return value.get();
    }
    const Value &execute(uint32_t docId = 1) {
        return extractTensor(docId);
    }
};

TEST(TensorTest, require_that_tensor_attribute_can_be_extracted_as_tensor_in_attribute_feature)
{
    ExecFixture f("attribute(tensorattr)");
    EXPECT_EQ(TensorSpec("tensor(x{})")
              .add({{"x", "b"}}, 5)
              .add({{"x", "c"}}, 7)
              .add({{"x", "a"}}, 3), spec_from_value(f.execute()));
}

TEST(TensorTest, require_that_direct_tensor_attribute_can_be_extracted_in_attribute_feature)
{
    ExecFixture f("attribute(directattr)");
    EXPECT_EQ(TensorSpec("tensor(x{})")
              .add({{"x", "b"}}, 5)
              .add({{"x", "c"}}, 7)
              .add({{"x", "a"}}, 3), spec_from_value(f.execute()));
}

TEST(TensorTest, require_that_tensor_from_query_can_be_extracted_as_tensor_in_query_feature)
{
    ExecFixture f("query(tensorquery)");
    EXPECT_EQ(TensorSpec("tensor(q{})")
              .add({{"q", "f"}}, 17)
              .add({{"q", "d"}}, 11)
              .add({{"q", "e"}}, 13), spec_from_value(f.execute()));
}

TEST(TensorTest, require_that_tensor_from_query_can_have_default_value)
{
    ExecFixture f("query(with_default)");
    EXPECT_EQ(TensorSpec("tensor(x[3])")
              .add({{"x", 0}}, 1)
              .add({{"x", 1}}, 2)
              .add({{"x", 2}}, 3), spec_from_value(f.execute()));
}

TEST(TensorTest, require_that_empty_tensor_is_created_if_attribute_does_not_exists)
{
    ExecFixture f("attribute(null)");
    EXPECT_EQ(*make_empty("tensor(x{})"), f.execute());
}

TEST(TensorTest, require_that_empty_tensor_is_created_if_tensor_type_is_wrong)
{
    ExecFixture f("attribute(wrongtype)");
    EXPECT_EQ(*make_empty("tensor(x{})"), f.execute());
}

TEST(TensorTest, require_that_empty_tensor_is_created_if_query_parameter_is_not_found)
{
    ExecFixture f("query(null)");
    EXPECT_EQ(*make_empty("tensor(q{})"), f.execute());
}

TEST(TensorTest, require_that_empty_tensor_with_correct_type_is_created_if_document_has_no_tensor)
{
    ExecFixture f("attribute(tensorattr)");
    EXPECT_EQ(*make_empty("tensor(x{})"), f.execute(2));
}

TEST(TensorTest, require_that_empty_tensor_with_correct_type_is_returned_by_direct_tensor_attribute)
{
    ExecFixture f("attribute(directattr)");
    EXPECT_EQ(*make_empty("tensor(x{})"), f.execute(2));
}

TEST(TensorTest, require_that_wrong_tensor_type_from_query_tensor_gives_empty_tensor)
{
    ExecFixture f("query(mappedtensorquery)");
    EXPECT_EQ(TensorSpec("tensor(x[2])")
              .add({{"x", 0}}, 0)
              .add({{"x", 1}}, 0), spec_from_value(f.execute()));
}

GTEST_MAIN_RUN_ALL_TESTS()
