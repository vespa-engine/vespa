// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>

#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/test/ftlib.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/indexenvironmentbuilder.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>
#include <vespa/searchlib/tensor/tensor_attribute.h>
#include <vespa/searchlib/tensor/direct_tensor_attribute.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/test/value_compare.h>
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
using FTA = FtTestApp;
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
        ASSERT_TRUE(test.setup());
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
        directAttr->set_tensor(1, std::move(doc_tensor));

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
                vespalib::stringref(stream.peek(), stream.size()));
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
    }
    const Value &extractTensor(uint32_t docid) {
        Value::CREF value = test.resolveObjectFeature(docid);
        ASSERT_TRUE(value.get().type().has_dimensions());
        return value.get();
    }
    const Value &execute(uint32_t docId = 1) {
        return extractTensor(docId);
    }
};

TEST_F("require that tensor attribute can be extracted as tensor in attribute feature",
       ExecFixture("attribute(tensorattr)"))
{
    EXPECT_EQUAL(TensorSpec("tensor(x{})")
                 .add({{"x", "b"}}, 5)
                 .add({{"x", "c"}}, 7)
                 .add({{"x", "a"}}, 3), spec_from_value(f.execute()));
}

TEST_F("require that direct tensor attribute can be extracted in attribute feature",
       ExecFixture("attribute(directattr)"))
{
    EXPECT_EQUAL(TensorSpec("tensor(x{})")
                 .add({{"x", "b"}}, 5)
                 .add({{"x", "c"}}, 7)
                 .add({{"x", "a"}}, 3), spec_from_value(f.execute()));
}

TEST_F("require that tensor from query can be extracted as tensor in query feature",
       ExecFixture("query(tensorquery)"))
{
    EXPECT_EQUAL(TensorSpec("tensor(q{})")
                 .add({{"q", "f"}}, 17)
                 .add({{"q", "d"}}, 11)
                 .add({{"q", "e"}}, 13), spec_from_value(f.execute()));
}

TEST_F("require that empty tensor is created if attribute does not exists",
       ExecFixture("attribute(null)"))
{
    EXPECT_EQUAL(*make_empty("tensor(x{})"), f.execute());
}

TEST_F("require that empty tensor is created if tensor type is wrong",
       ExecFixture("attribute(wrongtype)"))
{
    EXPECT_EQUAL(*make_empty("tensor(x{})"), f.execute());
}

TEST_F("require that empty tensor is created if query parameter is not found",
       ExecFixture("query(null)"))
{
    EXPECT_EQUAL(*make_empty("tensor(q{})"), f.execute());
}

TEST_F("require that empty tensor with correct type is created if document has no tensor",
       ExecFixture("attribute(tensorattr)")) {
    EXPECT_EQUAL(*make_empty("tensor(x{})"), f.execute(2));
}

TEST_F("require that empty tensor with correct type is returned by direct tensor attribute",
       ExecFixture("attribute(directattr)")) {
    EXPECT_EQUAL(*make_empty("tensor(x{})"), f.execute(2));
}

TEST_F("require that wrong tensor type from query tensor gives empty tensor",
       ExecFixture("query(mappedtensorquery)")) {
    EXPECT_EQUAL(TensorSpec("tensor(x[2])")
                 .add({{"x", 0}}, 0)
                 .add({{"x", 1}}, 0), spec_from_value(f.execute()));
}

TEST_MAIN() { TEST_RUN_ALL(); }
