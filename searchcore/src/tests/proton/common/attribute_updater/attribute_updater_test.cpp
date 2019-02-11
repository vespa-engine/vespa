// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocrepo.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/bytefieldvalue.h>
#include <vespa/document/fieldvalue/floatfieldvalue.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/referencefieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/update/addvalueupdate.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/update/clearvalueupdate.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/update/mapvalueupdate.h>
#include <vespa/document/update/removevalueupdate.h>
#include <vespa/document/update/tensor_add_update.h>
#include <vespa/document/update/tensor_modify_update.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/searchcore/proton/common/attribute_updater.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.hpp>
#include <vespa/searchlib/attribute/reference_attribute.h>
#include <vespa/searchlib/tensor/dense_tensor_attribute.h>
#include <vespa/searchlib/tensor/generic_tensor_attribute.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("attribute_updater_test");

using namespace document;
using document::config_builder::Array;
using document::config_builder::Map;
using document::config_builder::Struct;
using document::config_builder::Wset;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::attribute::Reference;
using search::attribute::ReferenceAttribute;
using search::tensor::ITensorAttribute;
using search::tensor::DenseTensorAttribute;
using search::tensor::GenericTensorAttribute;
using search::tensor::TensorAttribute;
using vespalib::eval::ValueType;
using vespalib::eval::TensorSpec;
using vespalib::tensor::DefaultTensorEngine;
using vespalib::tensor::Tensor;

namespace search {

typedef AttributeVector::SP AttributePtr;
typedef AttributeVector::WeightedInt WeightedInt;
typedef AttributeVector::WeightedFloat WeightedFloat;
typedef AttributeVector::WeightedString WeightedString;

std::unique_ptr<DocumentTypeRepo>
makeDocumentTypeRepo()
{
    config_builder::DocumenttypesConfigBuilderHelper builder;
    builder.document(222, "testdoc",
                     Struct("testdoc.header")
                             .addField("int", DataType::T_INT)
                             .addField("float", DataType::T_FLOAT)
                             .addField("string", DataType::T_STRING)
                             .addField("aint", Array(DataType::T_INT))
                             .addField("afloat", Array(DataType::T_FLOAT))
                             .addField("astring", Array(DataType::T_STRING))
                             .addField("wsint", Wset(DataType::T_INT))
                             .addField("wsfloat", Wset(DataType::T_FLOAT))
                             .addField("wsstring", Wset(DataType::T_STRING))
                             .addField("ref", 333)
                             .addField("dense_tensor", DataType::T_TENSOR),
                     Struct("testdoc.body"))
                     .referenceType(333, 222);
    return std::make_unique<DocumentTypeRepo>(builder.config());
}

struct Fixture {
    std::unique_ptr<DocumentTypeRepo> repo;
    const DocumentType *docType;

    Fixture()
        : repo(makeDocumentTypeRepo()),
          docType(repo->getDocumentType("testdoc"))
    {
    }

    void applyValueUpdate(AttributeVector & vec, uint32_t docId, const ValueUpdate & upd) {
        FieldUpdate fupd(docType->getField(vec.getName()));
        fupd.addUpdate(upd);
        search::AttributeUpdater::handleUpdate(vec, docId, fupd);
        vec.commit();
    }

    void applyArrayUpdates(AttributeVector & vec, const FieldValue & assign,
                           const FieldValue & first, const FieldValue & second) {
        applyValueUpdate(vec, 0, AssignValueUpdate(assign));
        applyValueUpdate(vec, 1, AddValueUpdate(second));
        applyValueUpdate(vec, 2, RemoveValueUpdate(first));
        applyValueUpdate(vec, 3, ClearValueUpdate());
    }

    void applyWeightedSetUpdates(AttributeVector & vec, const FieldValue & assign,
                                 const FieldValue & first, const FieldValue & second) {
        applyValueUpdate(vec, 0, AssignValueUpdate(assign));
        applyValueUpdate(vec, 1, AddValueUpdate(second, 20));
        applyValueUpdate(vec, 2, RemoveValueUpdate(first));
        applyValueUpdate(vec, 3, ClearValueUpdate());
        ArithmeticValueUpdate arithmetic(ArithmeticValueUpdate::Add, 10);
        applyValueUpdate(vec, 4, MapValueUpdate(first, arithmetic));
    }
};

template <typename T, typename VectorType>
AttributePtr
create(uint32_t numDocs, T val, int32_t weight,
       const std::string & baseName,
       const Config &info)
{
    LOG(info, "create attribute vector: %s", baseName.c_str());
    AttributePtr vec = AttributeFactory::createAttribute(baseName, info);
    VectorType * api = static_cast<VectorType *>(vec.get());
    for (uint32_t i = 0; i < numDocs; ++i) {
        if (!api->addDoc(i)) {
            LOG(info, "failed adding doc: %u", i);
            return AttributePtr();
        }
        if (api->hasMultiValue()) {
            if (!api->append(i, val, weight)) {
                LOG(info, "failed append to doc: %u", i);
            }
        } else {
            if (!api->update(i, val)) {
                LOG(info, "failed update doc: %u", i);
                return AttributePtr();
            }
        }
    }
    api->commit();
    return vec;
}

template <typename T>
bool
check(const AttributePtr &vec, uint32_t docId, const std::vector<T> &values)
{
    uint32_t sz = vec->getValueCount(docId);
    if (!EXPECT_EQUAL(sz, values.size())) return false;
    std::vector<T> buf(sz);
    uint32_t asz = vec->get(docId, &buf[0], sz);
    if (!EXPECT_EQUAL(sz, asz)) return false;
    for (uint32_t i = 0; i < values.size(); ++i) {
        if (!EXPECT_EQUAL(buf[i].getValue(), values[i].getValue())) return false;
        if (!EXPECT_EQUAL(buf[i].getWeight(), values[i].getWeight())) return false;
    }
    return true;
}


GlobalId toGid(vespalib::stringref docId) {
    return DocumentId(docId).getGlobalId();
}

vespalib::string doc1("id:test:testdoc::1");
vespalib::string doc2("id:test:testdoc::2");

ReferenceAttribute &asReferenceAttribute(AttributeVector &vec)
{
    return dynamic_cast<ReferenceAttribute &>(vec);
}

void assertNoRef(AttributeVector &vec, uint32_t doc)
{
    EXPECT_TRUE(asReferenceAttribute(vec).getReference(doc) == nullptr);
}

void assertRef(AttributeVector &vec, vespalib::stringref str, uint32_t doc) {
    const Reference *ref = asReferenceAttribute(vec).getReference(doc);
    EXPECT_TRUE(ref != nullptr);
    const GlobalId &gid = ref->gid();
    EXPECT_EQUAL(toGid(str), gid);
}

TEST_F("require that single attributes are updated", Fixture)
{
    using search::attribute::getUndefined;
    CollectionType ct(CollectionType::SINGLE);
    {
        BasicType bt(BasicType::INT32);
        AttributePtr vec = create<int32_t, IntegerAttribute>(3, 32, 0,
                                                             "in1/int",
                                                             Config(bt, ct));
        f.applyValueUpdate(*vec, 0, AssignValueUpdate(IntFieldValue(64)));
        f.applyValueUpdate(*vec, 1, ArithmeticValueUpdate(ArithmeticValueUpdate::Add, 10));
        f.applyValueUpdate(*vec, 2, ClearValueUpdate());
        EXPECT_EQUAL(3u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 0, std::vector<WeightedInt>{WeightedInt(64)}));
        EXPECT_TRUE(check(vec, 1, std::vector<WeightedInt>{WeightedInt(42)}));
        EXPECT_TRUE(check(vec, 2, std::vector<WeightedInt>{WeightedInt(getUndefined<int32_t>())}));
    }
    {
        BasicType bt(BasicType::FLOAT);
        AttributePtr vec = create<float, FloatingPointAttribute>(3, 55.5f, 0,
                                                                 "in1/float",
                                                                 Config(bt,
                                                                        ct));
        f.applyValueUpdate(*vec, 0, AssignValueUpdate(FloatFieldValue(77.7f)));
        f.applyValueUpdate(*vec, 1, ArithmeticValueUpdate(ArithmeticValueUpdate::Add, 10));
        f.applyValueUpdate(*vec, 2, ClearValueUpdate());
        EXPECT_EQUAL(3u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 0, std::vector<WeightedFloat>{WeightedFloat(77.7f)}));
        EXPECT_TRUE(check(vec, 1, std::vector<WeightedFloat>{WeightedFloat(65.5f)}));
        EXPECT_TRUE(std::isnan(vec->getFloat(2)));
    }
    {
        BasicType bt(BasicType::STRING);
        AttributePtr vec = create<std::string, StringAttribute>(3, "first", 0,
                                                                "in1/string",
                                                                Config(bt,
                                                                       ct));
        f.applyValueUpdate(*vec, 0, AssignValueUpdate(StringFieldValue("second")));
        f.applyValueUpdate(*vec, 2, ClearValueUpdate());
        EXPECT_EQUAL(3u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 0, std::vector<WeightedString>{WeightedString("second")}));
        EXPECT_TRUE(check(vec, 1, std::vector<WeightedString>{WeightedString("first")}));
        EXPECT_TRUE(check(vec, 2, std::vector<WeightedString>{WeightedString("")}));
    }
    {
        BasicType bt(BasicType::REFERENCE);
        Config cfg(bt, ct);
        AttributePtr vec = AttributeFactory::createAttribute("in1/ref", cfg);
        uint32_t startDoc = 0;
        uint32_t endDoc = 0;
        EXPECT_TRUE(vec->addDocs(startDoc, endDoc, 3));
        EXPECT_EQUAL(0u, startDoc);
        EXPECT_EQUAL(2u, endDoc);
        for (uint32_t docId = 0; docId < 3; ++docId) {
            asReferenceAttribute(*vec).update(docId, toGid(doc1));
        }
        vec->commit();
        f.applyValueUpdate(*vec, 0, AssignValueUpdate(ReferenceFieldValue(dynamic_cast<const ReferenceDataType &>(f.docType->getField("ref").getDataType()), DocumentId(doc2))));
        f.applyValueUpdate(*vec, 2, ClearValueUpdate());
        EXPECT_EQUAL(3u, vec->getNumDocs());
        TEST_DO(assertRef(*vec, doc2, 0));
        TEST_DO(assertRef(*vec, doc1, 1));
        TEST_DO(assertNoRef(*vec, 2));
    }
}

TEST_F("require that array attributes are updated", Fixture)
{
    CollectionType ct(CollectionType::ARRAY);
    {
        BasicType bt(BasicType::INT32);
        AttributePtr vec = create<int32_t, IntegerAttribute>(5, 32, 1,
                                                             "in1/aint",
                                                             Config(bt, ct));
        IntFieldValue first(32);
        IntFieldValue second(64);
        ArrayFieldValue assign(f.docType->getField("aint").getDataType());
        assign.add(second);
        f.applyArrayUpdates(*vec, assign, first, second);

        EXPECT_EQUAL(5u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 0, std::vector<WeightedInt>{WeightedInt(64)}));
        EXPECT_TRUE(check(vec, 1, std::vector<WeightedInt>{WeightedInt(32), WeightedInt(64)}));
        EXPECT_TRUE(check(vec, 2, std::vector<WeightedInt>{}));
        EXPECT_TRUE(check(vec, 3, std::vector<WeightedInt>{}));
        EXPECT_TRUE(check(vec, 4, std::vector<WeightedInt>{WeightedInt(32)}));
    }
    {
        BasicType bt(BasicType::FLOAT);
        AttributePtr vec = create<float, FloatingPointAttribute>(5, 55.5f, 1,
                                                                 "in1/afloat",
                                                                 Config(bt,
                                                                        ct));
        FloatFieldValue first(55.5f);
        FloatFieldValue second(77.7f);
        ArrayFieldValue assign(f.docType->getField("afloat").getDataType());
        assign.add(second);
        f.applyArrayUpdates(*vec, assign, first, second);

        EXPECT_EQUAL(5u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 0, std::vector<WeightedFloat>{WeightedFloat(77.7f)}));
        EXPECT_TRUE(check(vec, 1, std::vector<WeightedFloat>{WeightedFloat(55.5f), WeightedFloat(77.7f)}));
        EXPECT_TRUE(check(vec, 2, std::vector<WeightedFloat>{}));
        EXPECT_TRUE(check(vec, 3, std::vector<WeightedFloat>{}));
        EXPECT_TRUE(check(vec, 4, std::vector<WeightedFloat>{WeightedFloat(55.5f)}));
    }
    {
        BasicType bt(BasicType::STRING);
        AttributePtr vec = create<std::string, StringAttribute>(5, "first", 1,
                                                                "in1/astring",
                                                                Config(bt, ct));
        StringFieldValue first("first");
        StringFieldValue second("second");
        ArrayFieldValue assign(f.docType->getField("astring").getDataType());
        assign.add(second);
        f.applyArrayUpdates(*vec, assign, first, second);

        EXPECT_EQUAL(5u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 0, std::vector<WeightedString>{WeightedString("second")}));
        EXPECT_TRUE(check(vec, 1, std::vector<WeightedString>{WeightedString("first"), WeightedString("second")}));
        EXPECT_TRUE(check(vec, 2, std::vector<WeightedString>{}));
        EXPECT_TRUE(check(vec, 3, std::vector<WeightedString>{}));
        EXPECT_TRUE(check(vec, 4, std::vector<WeightedString>{WeightedString("first")}));
    }
}

TEST_F("require that weighted set attributes are updated", Fixture)
{
    CollectionType ct(CollectionType::WSET);
    {
        BasicType bt(BasicType::INT32);
        AttributePtr vec = create<int32_t, IntegerAttribute>(5, 32, 100,
                                                             "in1/wsint",
                                                             Config(bt, ct));
        IntFieldValue first(32);
        IntFieldValue second(64);
        WeightedSetFieldValue
            assign(f.docType->getField("wsint").getDataType());
        assign.add(second, 20);
        f.applyWeightedSetUpdates(*vec, assign, first, second);

        EXPECT_EQUAL(5u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 0, std::vector<WeightedInt>{WeightedInt(64, 20)}));
        EXPECT_TRUE(check(vec, 1, std::vector<WeightedInt>{WeightedInt(32, 100), WeightedInt(64, 20)}));
        EXPECT_TRUE(check(vec, 2, std::vector<WeightedInt>{}));
        EXPECT_TRUE(check(vec, 3, std::vector<WeightedInt>{}));
        EXPECT_TRUE(check(vec, 4, std::vector<WeightedInt>{WeightedInt(32, 110)}));
    }
    {
        BasicType bt(BasicType::FLOAT);
        AttributePtr vec = create<float, FloatingPointAttribute>(5, 55.5f, 100,
                                                                 "in1/wsfloat",
                                                                 Config(bt,
                                                                        ct));
        FloatFieldValue first(55.5f);
        FloatFieldValue second(77.7f);
        WeightedSetFieldValue
            assign(f.docType->getField("wsfloat").getDataType());
        assign.add(second, 20);
        f.applyWeightedSetUpdates(*vec, assign, first, second);

        EXPECT_EQUAL(5u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 0, std::vector<WeightedFloat>{WeightedFloat(77.7f, 20)}));
        EXPECT_TRUE(check(vec, 1, std::vector<WeightedFloat>{WeightedFloat(55.5f, 100), WeightedFloat(77.7f, 20)}));
        EXPECT_TRUE(check(vec, 2, std::vector<WeightedFloat>{}));
        EXPECT_TRUE(check(vec, 3, std::vector<WeightedFloat>{}));
        EXPECT_TRUE(check(vec, 4, std::vector<WeightedFloat>{WeightedFloat(55.5f, 110)}));
    }
    {
        BasicType bt(BasicType::STRING);
        AttributePtr vec = create<std::string, StringAttribute>(5, "first",
                                                                100,
                                                                "in1/wsstring",
                                                                Config(bt,
                                                                       ct));
        StringFieldValue first("first");
        StringFieldValue second("second");
        WeightedSetFieldValue
            assign(f.docType->getField("wsstring").getDataType());
        assign.add(second, 20);
        f.applyWeightedSetUpdates(*vec, assign, first, second);

        EXPECT_EQUAL(5u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 0, std::vector<WeightedString>{WeightedString("second", 20)}));
        EXPECT_TRUE(check(vec, 1, std::vector<WeightedString>{WeightedString("first", 100), WeightedString("second", 20)}));
        EXPECT_TRUE(check(vec, 2, std::vector<WeightedString>{}));
        EXPECT_TRUE(check(vec, 3, std::vector<WeightedString>{}));
        EXPECT_TRUE(check(vec, 4, std::vector<WeightedString>{WeightedString("first", 110)}));
    }
}

template <typename TensorAttributeType>
std::unique_ptr<TensorAttributeType>
makeTensorAttribute(const vespalib::string &name, const vespalib::string &tensorType)
{
    Config cfg(BasicType::TENSOR, CollectionType::SINGLE);
    cfg.setTensorType(ValueType::from_spec(tensorType));
    auto result = std::make_unique<TensorAttributeType>(name, cfg);
    result->addReservedDoc();
    result->addDocs(1);
    return result;
}

std::unique_ptr<Tensor>
makeTensor(const TensorSpec &spec)
{
    auto result = DefaultTensorEngine::ref().from_spec(spec);
    return std::unique_ptr<Tensor>(dynamic_cast<Tensor*>(result.release()));
}

std::unique_ptr<TensorFieldValue>
makeTensorFieldValue(const TensorSpec &spec)
{
    auto result = std::make_unique<TensorFieldValue>();
    result->assignDeserialized(makeTensor(spec));
    return result;
}

void
setTensor(TensorAttribute &attribute, uint32_t lid, const TensorSpec &spec)
{
    auto tensor = makeTensor(spec);
    attribute.setTensor(lid, *tensor);
    attribute.commit();
}

TEST_F("require that tensor modify update is applied", Fixture)
{
    vespalib::string type = "tensor(x[2])";
    auto attribute = makeTensorAttribute<DenseTensorAttribute>("dense_tensor", type);
    setTensor(*attribute, 1, TensorSpec(type).add({{"x", 0}}, 3).add({{"x", 1}}, 5));

    f.applyValueUpdate(*attribute, 1,
                       TensorModifyUpdate(TensorModifyUpdate::Operation::REPLACE,
                                          makeTensorFieldValue(TensorSpec("tensor(x{})").add({{"x", 0}}, 7))));
    EXPECT_EQUAL(TensorSpec(type).add({{"x", 0}}, 7).add({{"x", 1}}, 5), attribute->getTensor(1)->toSpec());
}

TEST_F("require that tensor add update is applied", Fixture)
{
    vespalib::string type = "tensor(x{})";
    auto attribute = makeTensorAttribute<GenericTensorAttribute>("dense_tensor", type);
    setTensor(*attribute, 1, TensorSpec(type).add({{"x", "a"}}, 2));

    f.applyValueUpdate(*attribute, 1,
                       TensorAddUpdate(makeTensorFieldValue(TensorSpec(type).add({{"x", "a"}}, 3))));
    EXPECT_EQUAL(TensorSpec(type).add({{"x", "a"}}, 3), attribute->getTensor(1)->toSpec());
}

}

TEST_MAIN() { TEST_RUN_ALL(); }

