// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/tensor/default_tensor.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/serialization/typed_binary_format.h>
#include <vespa/eval/tensor/tensor_factory.h>
#include <vespa/vespalib/objects/nbostream.h>

using search::feature_t;
using namespace search::fef;
using namespace search::fef::indexproperties;
using namespace search::fef::test;
using namespace search::features;
using search::AttributeFactory;
using search::tensor::TensorAttribute;
using search::AttributeVector;
using vespalib::eval::Function;
using vespalib::eval::Value;
using vespalib::eval::ValueType;
using vespalib::eval::TensorSpec;
using vespalib::tensor::DefaultTensorEngine;
using vespalib::tensor::DenseTensorCells;
using vespalib::tensor::Tensor;
using vespalib::tensor::TensorCells;
using vespalib::tensor::TensorDimensions;
using vespalib::tensor::TensorFactory;

using AVC = search::attribute::Config;
using AVBT = search::attribute::BasicType;
using AVCT = search::attribute::CollectionType;
using AttributePtr = search::AttributeVector::SP;
using FTA = FtTestApp;
using CollectionType = FieldInfo::CollectionType;

namespace
{

Tensor::UP createTensor(const TensorCells &cells,
                        const TensorDimensions &dimensions) {
    vespalib::tensor::DefaultTensor::builder builder;
    return TensorFactory::create(cells, dimensions, builder);
}

Tensor::UP make_tensor(const TensorSpec &spec) {
    auto tensor = DefaultTensorEngine::ref().create(spec);
    return Tensor::UP(dynamic_cast<Tensor*>(tensor.release()));
}

Tensor::UP make_empty(const vespalib::string &type) {
    return make_tensor(TensorSpec(type));
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
    AttributeVector::SP createTensorAttribute(const vespalib::string &attrName, const vespalib::string &type) {
        addAttributeField(attrName);
        AVC config(AVBT::TENSOR, AVCT::SINGLE);
        config.setTensorType(ValueType::from_spec(type));
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
        attrs.push_back(createStringAttribute("singlestr"));
        attrs.push_back(createTensorAttribute("wrongtype", "tensor(y{})"));
        addAttributeField("null");
        setAttributeTensorType("tensorattr", "tensor(x{})");
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

        tensorAttr->setTensor(1, *createTensor({ {{{"x", "a"}}, 3},
                                                 {{{"x", "b"}}, 5},
                                                 {{{"x", "c"}}, 7} },
                                               { "x" }));

        for (const auto &attr : attrs) {
            attr->commit();
        }
    }
    void setQueryTensor(const vespalib::string &tensorName,
                        const vespalib::string &tensorTypeSpec,
                        const TensorCells &cells,
                        const TensorDimensions &dimensions)
    {
        auto tensor = createTensor(cells, dimensions);
        vespalib::nbostream stream;
        vespalib::tensor::TypedBinaryFormat::serialize(stream, *tensor);
        test.getQueryEnv().getProperties().add(tensorName,
                vespalib::stringref(stream.peek(), stream.size()));
        setQueryTensorType(tensorName, tensorTypeSpec);
    }

    void setupQueryEnvironment() {
        setQueryTensor("tensorquery",
                       "tensor(q{})",
                       {   {{{"q", "d"}}, 11 },
                           {{{"q", "e"}}, 13 },
                           {{{"q", "f"}}, 17 } },
                       { "q" });
        setQueryTensor("mappedtensorquery",
                       "tensor(x[2])",
                       {   {{{"x", "0"},{"y", "0"}}, 11 },
                           {{{"x", "0"},{"y", "1"}}, 13 },
                           {{{"x", "1"},{"y", "0"}}, 17 } },
                       { "x", "y" });
        setQueryTensorType("null", "tensor(q{})");
    }
    const Tensor &extractTensor(uint32_t docid) {
        Value::CREF value = test.resolveObjectFeature(docid);
        ASSERT_TRUE(value.get().is_tensor());
        return static_cast<const Tensor &>(*value.get().as_tensor());
    }
    const Tensor &execute(uint32_t docId = 1) {
        return extractTensor(docId);
    }
};

TEST_F("require that tensor attribute can be extracted as tensor in attribute feature",
       ExecFixture("attribute(tensorattr)"))
{
    EXPECT_EQUAL(*make_tensor(TensorSpec("tensor(x{})")
                              .add({{"x", "b"}}, 5)
                              .add({{"x", "c"}}, 7)
                              .add({{"x", "a"}}, 3)), f.execute());
}

TEST_F("require that tensor from query can be extracted as tensor in query feature",
       ExecFixture("query(tensorquery)"))
{
    EXPECT_EQUAL(*make_tensor(TensorSpec("tensor(q{})")
                              .add({{"q", "f"}}, 17)
                              .add({{"q", "d"}}, 11)
                              .add({{"q", "e"}}, 13)), f.execute());
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

TEST_F("require that tensor from query is mapped",
       ExecFixture("query(mappedtensorquery)")) {
    EXPECT_EQUAL(*make_tensor(TensorSpec("tensor(x[2])")
                              .add({{"x", 0}}, 24)
                              .add({{"x", 1}}, 17)), f.execute());
}

TEST_MAIN() { TEST_RUN_ALL(); }
