// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/common/attribute_updater.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/reference_attribute.h>
#include <vespa/searchlib/attribute/single_raw_attribute.h>
#include <vespa/searchlib/tensor/dense_tensor_attribute.h>
#include <vespa/searchlib/tensor/serialized_fast_value_attribute.h>
#include <vespa/searchlib/test/attribute_builder.h>
#include <vespa/searchlib/test/weighted_type_test_utils.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/document/base/testdocrepo.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/bytefieldvalue.h>
#include <vespa/document/fieldvalue/floatfieldvalue.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/rawfieldvalue.h>
#include <vespa/document/fieldvalue/referencefieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/update/addvalueupdate.h>
#include <vespa/document/update/arithmeticvalueupdate.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/update/clearvalueupdate.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/update/mapvalueupdate.h>
#include <vespa/document/update/removevalueupdate.h>
#include <vespa/document/update/tensor_add_update.h>
#include <vespa/document/update/tensor_modify_update.h>
#include <vespa/document/update/tensor_remove_update.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/test/insertion_operators.h>
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
using search::attribute::SingleRawAttribute;
using search::attribute::test::AttributeBuilder;
using search::tensor::ITensorAttribute;
using search::tensor::DenseTensorAttribute;
using search::tensor::SerializedFastValueAttribute;
using search::tensor::TensorAttribute;
using vespalib::eval::SimpleValue;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;
using vespalib::eval::ValueType;

namespace {

std::vector<char> as_vector(vespalib::stringref value) {
    return {value.data(), value.data() + value.size()};
}

std::vector<char> as_vector(vespalib::ConstArrayRef<char> value) {
    return {value.data(), value.data() + value.size()};
}

}

namespace search {

using AttributePtr = AttributeVector::SP;
using WeightedInt = AttributeVector::WeightedInt;
using WeightedFloat = AttributeVector::WeightedFloat;
using WeightedString = AttributeVector::WeightedString;

std::unique_ptr<DocumentTypeRepo>
makeDocumentTypeRepo()
{
    config_builder::DocumenttypesConfigBuilderHelper builder;
    builder.document(222, "testdoc",
                     Struct("testdoc.header")
                             .addField("int", DataType::T_INT)
                             .addField("float", DataType::T_FLOAT)
                             .addField("string", DataType::T_STRING)
                             .addField("raw", DataType::T_RAW)
                             .addField("aint", Array(DataType::T_INT))
                             .addField("afloat", Array(DataType::T_FLOAT))
                             .addField("astring", Array(DataType::T_STRING))
                             .addField("wsint", Wset(DataType::T_INT))
                             .addField("wsfloat", Wset(DataType::T_FLOAT))
                             .addField("wsstring", Wset(DataType::T_STRING))
                             .addField("ref", 333)
                             .addField("dense_tensor", DataType::T_TENSOR)
                             .addField("sparse_tensor", DataType::T_TENSOR),
                     Struct("testdoc.body"))
                     .referenceType(333, 222);
    return std::make_unique<DocumentTypeRepo>(builder.config());
}

std::unique_ptr<DocumentTypeRepo> repo = makeDocumentTypeRepo();

struct Fixture {
    const DocumentType *docType;

    Fixture()
        : docType(repo->getDocumentType("testdoc"))
    {
    }

    void applyValueUpdate(AttributeVector & vec, uint32_t docId, std::unique_ptr<ValueUpdate> upd) {
        FieldUpdate fupd(docType->getField(vec.getName()));
        fupd.addUpdate(std::move(upd));
        search::AttributeUpdater::handleUpdate(vec, docId, fupd);
        vec.commit();
    }

    void applyArrayUpdates(AttributeVector & vec, std::unique_ptr<FieldValue> assign,
                           std::unique_ptr<FieldValue> first, std::unique_ptr<FieldValue> second) {
        applyValueUpdate(vec, 1, std::make_unique<AssignValueUpdate>(std::move(assign)));
        applyValueUpdate(vec, 2, std::make_unique<AddValueUpdate>(std::move(second)));
        applyValueUpdate(vec, 3, std::make_unique<RemoveValueUpdate>(std::move(first)));
        applyValueUpdate(vec, 4, std::make_unique<ClearValueUpdate>());
    }

    void applyWeightedSetUpdates(AttributeVector & vec, std::unique_ptr<FieldValue> assign,
                                 std::unique_ptr<FieldValue> first, std::unique_ptr<FieldValue> copyOfFirst, std::unique_ptr<FieldValue> second) {
        applyValueUpdate(vec, 1, std::make_unique<AssignValueUpdate>(std::move(assign)));
        applyValueUpdate(vec, 2, std::make_unique<AddValueUpdate>(std::move(second), 20));
        applyValueUpdate(vec, 3, std::make_unique<RemoveValueUpdate>(std::move(first)));
        applyValueUpdate(vec, 4, std::make_unique<ClearValueUpdate>());
        applyValueUpdate(vec, 5, std::make_unique<MapValueUpdate>(std::move(copyOfFirst), std::make_unique<ArithmeticValueUpdate>(ArithmeticValueUpdate::Add, 10)));
    }

    void applyValue(AttributeVector& vec, uint32_t docid, std::unique_ptr<FieldValue> value) {
        search::AttributeUpdater::handleValue(vec, docid, *value);
    }
};

template <typename T>
bool
check(const AttributePtr &vec, uint32_t docId, const std::vector<T> &values)
{
    uint32_t sz = vec->getValueCount(docId);
    if (!EXPECT_EQUAL(sz, values.size())) return false;
    std::vector<T> buf(sz);
    uint32_t asz = vec->get(docId, buf.data(), sz);
    if (!EXPECT_EQUAL(sz, asz)) return false;
    std::vector<T> wanted(values.begin(), values.end());
    if (vec->hasWeightedSetType()) {
        std::sort(wanted.begin(), wanted.end(), value_then_weight_order());
        std::sort(buf.begin(), buf.end(), value_then_weight_order());
    }
    for (uint32_t i = 0; i < values.size(); ++i) {
        if (!EXPECT_EQUAL(buf[i].getValue(), wanted[i].getValue())) return false;
        if (!EXPECT_EQUAL(buf[i].getWeight(), wanted[i].getWeight())) return false;
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
        auto vec = AttributeBuilder("in1/int", Config(BasicType::INT32)).fill({ 32, 32, 32}).get();
        f.applyValueUpdate(*vec, 1, std::make_unique<AssignValueUpdate>(std::make_unique<IntFieldValue>(64)));
        f.applyValueUpdate(*vec, 2, std::make_unique<ArithmeticValueUpdate>(ArithmeticValueUpdate::Add, 10));
        f.applyValueUpdate(*vec, 3, std::make_unique<ClearValueUpdate>());
        EXPECT_EQUAL(4u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 1, std::vector<WeightedInt>{WeightedInt(64)}));
        EXPECT_TRUE(check(vec, 2, std::vector<WeightedInt>{WeightedInt(42)}));
        EXPECT_TRUE(check(vec, 3, std::vector<WeightedInt>{WeightedInt(getUndefined<int32_t>())}));
    }
    {
        auto vec = AttributeBuilder("in1/float", Config(BasicType::FLOAT)).fill({55.5, 55.5, 55.5}).get();
        f.applyValueUpdate(*vec, 1, std::make_unique<AssignValueUpdate>(std::make_unique<FloatFieldValue>(77.7f)));
        f.applyValueUpdate(*vec, 2, std::make_unique<ArithmeticValueUpdate>(ArithmeticValueUpdate::Add, 10));
        f.applyValueUpdate(*vec, 3, std::make_unique<ClearValueUpdate>());
        EXPECT_EQUAL(4u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 1, std::vector<WeightedFloat>{WeightedFloat(77.7f)}));
        EXPECT_TRUE(check(vec, 2, std::vector<WeightedFloat>{WeightedFloat(65.5f)}));
        EXPECT_TRUE(std::isnan(vec->getFloat(3)));
    }
    {
        auto vec = AttributeBuilder("in1/string", Config(BasicType::STRING)).fill({"first", "first", "first"}).get();
        f.applyValueUpdate(*vec, 1, std::make_unique<AssignValueUpdate>(StringFieldValue::make("second")));
        f.applyValueUpdate(*vec, 3, std::make_unique<ClearValueUpdate>());
        EXPECT_EQUAL(4u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 1, std::vector<WeightedString>{WeightedString("second")}));
        EXPECT_TRUE(check(vec, 2, std::vector<WeightedString>{WeightedString("first")}));
        EXPECT_TRUE(check(vec, 3, std::vector<WeightedString>{WeightedString("")}));
    }
    {
        BasicType bt(BasicType::REFERENCE);
        Config cfg(bt, ct);
        AttributePtr vec = AttributeFactory::createAttribute("in1/ref", cfg);
        vec->addReservedDoc();
        uint32_t startDoc = 0;
        uint32_t endDoc = 0;
        EXPECT_TRUE(vec->addDocs(startDoc, endDoc, 3));
        EXPECT_EQUAL(1u, startDoc);
        EXPECT_EQUAL(3u, endDoc);
        for (uint32_t docId = 1; docId < 4; ++docId) {
            asReferenceAttribute(*vec).update(docId, toGid(doc1));
        }
        vec->commit();
        f.applyValueUpdate(*vec, 1, std::make_unique<AssignValueUpdate>(std::make_unique<ReferenceFieldValue>(dynamic_cast<const ReferenceDataType &>(f.docType->getField("ref").getDataType()), DocumentId(doc2))));
        f.applyValueUpdate(*vec, 3, std::make_unique<ClearValueUpdate>());
        EXPECT_EQUAL(4u, vec->getNumDocs());
        TEST_DO(assertRef(*vec, doc2, 1));
        TEST_DO(assertRef(*vec, doc1, 2));
        TEST_DO(assertNoRef(*vec, 3));
    }
    {
        vespalib::string first_backing("first");
        vespalib::ConstArrayRef<char> first(first_backing.data(), first_backing.size());
        auto vec = AttributeBuilder("in1/raw", Config(BasicType::RAW)).fill({first, first, first, first}).get();
        f.applyValueUpdate(*vec, 1, std::make_unique<AssignValueUpdate>(std::make_unique<RawFieldValue>("second")));
        f.applyValueUpdate(*vec, 3, std::make_unique<ClearValueUpdate>());
        f.applyValue(*vec, 4, std::make_unique<RawFieldValue>("third"));
        EXPECT_EQUAL(5u, vec->getNumDocs());
        EXPECT_EQUAL(as_vector("second"), as_vector(vec->get_raw(1)));
        EXPECT_EQUAL(as_vector("first"), as_vector(vec->get_raw(2)));
        EXPECT_EQUAL(as_vector(""), as_vector(vec->get_raw(3)));
        EXPECT_EQUAL(as_vector("third"), as_vector(vec->get_raw(4)));
    }
}

TEST_F("require that array attributes are updated", Fixture)
{
    CollectionType ct(CollectionType::ARRAY);
    {
        using IL = AttributeBuilder::IntList;
        auto vec = AttributeBuilder("in1/aint", Config(BasicType::INT32, ct)).fill_array({IL{32}, {32}, {32}, {32}, {32}}).get();
        auto first = std::make_unique<IntFieldValue>(32);
        auto second = std::make_unique<IntFieldValue>(64);
        auto assign = std::make_unique<ArrayFieldValue>(f.docType->getField("aint").getDataType());
        assign->add(*second);
        f.applyArrayUpdates(*vec, std::move(assign), std::move(first), std::move(second));

        EXPECT_EQUAL(6u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 1, std::vector<WeightedInt>{WeightedInt(64)}));
        EXPECT_TRUE(check(vec, 2, std::vector<WeightedInt>{WeightedInt(32), WeightedInt(64)}));
        EXPECT_TRUE(check(vec, 3, std::vector<WeightedInt>{}));
        EXPECT_TRUE(check(vec, 4, std::vector<WeightedInt>{}));
        EXPECT_TRUE(check(vec, 5, std::vector<WeightedInt>{WeightedInt(32)}));
    }
    {
        using DL = AttributeBuilder::DoubleList;
        auto vec = AttributeBuilder("in1/afloat", Config(BasicType::FLOAT, ct)).fill_array({DL{55.5}, {55.5}, {55.5}, {55.5}, {55.5}}).get();
        auto first = std::make_unique<FloatFieldValue>(55.5f);
        auto second = std::make_unique<FloatFieldValue>(77.7f);
        auto assign = std::make_unique<ArrayFieldValue>(f.docType->getField("afloat").getDataType());
        assign->add(*second);
        f.applyArrayUpdates(*vec, std::move(assign), std::move(first), std::move(second));

        EXPECT_EQUAL(6u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 1, std::vector<WeightedFloat>{WeightedFloat(77.7f)}));
        EXPECT_TRUE(check(vec, 2, std::vector<WeightedFloat>{WeightedFloat(55.5f), WeightedFloat(77.7f)}));
        EXPECT_TRUE(check(vec, 3, std::vector<WeightedFloat>{}));
        EXPECT_TRUE(check(vec, 4, std::vector<WeightedFloat>{}));
        EXPECT_TRUE(check(vec, 5, std::vector<WeightedFloat>{WeightedFloat(55.5f)}));
    }
    {
        auto vec = AttributeBuilder("in1/astring", Config(BasicType::STRING, ct)).fill_array({{"first"}, {"first"}, {"first"}, {"first"}, {"first"}}).get();
        auto first = StringFieldValue::make("first");
        auto second = StringFieldValue::make("second");
        auto assign = std::make_unique<ArrayFieldValue>(f.docType->getField("astring").getDataType());
        assign->add(*second);
        f.applyArrayUpdates(*vec, std::move(assign), std::move(first), std::move(second));

        EXPECT_EQUAL(6u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 1, std::vector<WeightedString>{WeightedString("second")}));
        EXPECT_TRUE(check(vec, 2, std::vector<WeightedString>{WeightedString("first"), WeightedString("second")}));
        EXPECT_TRUE(check(vec, 3, std::vector<WeightedString>{}));
        EXPECT_TRUE(check(vec, 4, std::vector<WeightedString>{}));
        EXPECT_TRUE(check(vec, 5, std::vector<WeightedString>{WeightedString("first")}));
    }
}

TEST_F("require that weighted set attributes are updated", Fixture)
{
    CollectionType ct(CollectionType::WSET);
    {
        using WIL = AttributeBuilder::WeightedIntList;
        auto vec = AttributeBuilder("in1/wsint", Config(BasicType::INT32, ct)).fill_wset({WIL{{32, 100}}, {{32, 100}}, {{32, 100}}, {{32, 100}}, {{32, 100}}}).get();
        auto first = std::make_unique<IntFieldValue>(32);
        auto copyOfFirst = std::make_unique<IntFieldValue>(32);
        auto second = std::make_unique<IntFieldValue>(64);
        auto assign = std::make_unique<WeightedSetFieldValue>(f.docType->getField("wsint").getDataType());
        assign->add(*second, 20);
        f.applyWeightedSetUpdates(*vec, std::move(assign), std::move(first), std::move(copyOfFirst), std::move(second));

        EXPECT_EQUAL(6u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 1, std::vector<WeightedInt>{WeightedInt(64, 20)}));
        EXPECT_TRUE(check(vec, 2, std::vector<WeightedInt>{WeightedInt(32, 100), WeightedInt(64, 20)}));
        EXPECT_TRUE(check(vec, 3, std::vector<WeightedInt>{}));
        EXPECT_TRUE(check(vec, 4, std::vector<WeightedInt>{}));
        EXPECT_TRUE(check(vec, 5, std::vector<WeightedInt>{WeightedInt(32, 110)}));
    }
    {
        using WDL = AttributeBuilder::WeightedDoubleList;
        auto vec = AttributeBuilder("in1/wsfloat", Config(BasicType::FLOAT, ct)).fill_wset({WDL{{55.5, 100}}, {{55.5, 100}}, {{55.5, 100}}, {{55.5, 100}}, {{55.5, 100}}}).get();
        auto first = std::make_unique<FloatFieldValue>(55.5f);
        auto copyOfFirst = std::make_unique<FloatFieldValue>(55.5f);
        auto second = std::make_unique<FloatFieldValue>(77.7f);
        auto assign = std::make_unique<WeightedSetFieldValue>(f.docType->getField("wsfloat").getDataType());
        assign->add(*second, 20);
        f.applyWeightedSetUpdates(*vec, std::move(assign), std::move(first), std::move(copyOfFirst), std::move(second));

        EXPECT_EQUAL(6u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 1, std::vector<WeightedFloat>{WeightedFloat(77.7f, 20)}));
        EXPECT_TRUE(check(vec, 2, std::vector<WeightedFloat>{WeightedFloat(55.5f, 100), WeightedFloat(77.7f, 20)}));
        EXPECT_TRUE(check(vec, 3, std::vector<WeightedFloat>{}));
        EXPECT_TRUE(check(vec, 4, std::vector<WeightedFloat>{}));
        EXPECT_TRUE(check(vec, 5, std::vector<WeightedFloat>{WeightedFloat(55.5f, 110)}));
    }
    {
        auto vec = AttributeBuilder("in1/wsstring", Config(BasicType::STRING, ct)).fill_wset({{{"first", 100}}, {{"first", 100}}, {{"first", 100}}, {{"first", 100}}, {{"first", 100}}}).get();
        auto first = StringFieldValue::make("first");
        auto copyOfFirst = StringFieldValue::make("first");
        auto second = StringFieldValue::make("second");
        auto assign = std::make_unique<WeightedSetFieldValue>(f.docType->getField("wsstring").getDataType());
        assign->add(*second, 20);
        f.applyWeightedSetUpdates(*vec, std::move(assign), std::move(first), std::move(copyOfFirst), std::move(second));

        EXPECT_EQUAL(6u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 1, std::vector<WeightedString>{WeightedString("second", 20)}));
        EXPECT_TRUE(check(vec, 2, std::vector<WeightedString>{WeightedString("first", 100), WeightedString("second", 20)}));
        EXPECT_TRUE(check(vec, 3, std::vector<WeightedString>{}));
        EXPECT_TRUE(check(vec, 4, std::vector<WeightedString>{}));
        EXPECT_TRUE(check(vec, 5, std::vector<WeightedString>{WeightedString("first", 110)}));
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

vespalib::hash_map<vespalib::string, std::unique_ptr<const TensorDataType>> tensorTypes;

const TensorDataType &
getTensorDataType(const vespalib::string &spec)
{
    auto insres = tensorTypes.insert(std::make_pair(spec, TensorDataType::fromSpec(spec)));
    return *insres.first->second;
}

std::unique_ptr<Value>
makeTensor(const TensorSpec &spec)
{
    return SimpleValue::from_spec(spec);
}

std::unique_ptr<TensorFieldValue>
makeTensorFieldValue(const TensorSpec &spec)
{
    auto tensor = makeTensor(spec);
    auto result = std::make_unique<TensorFieldValue>(getTensorDataType(tensor->type().to_spec()));
    result->assignDeserialized(std::move(tensor));
    return result;
}

template <typename TensorAttributeType>
struct TensorFixture : public Fixture {
    vespalib::string type;
    std::unique_ptr<TensorAttributeType> attribute;

    TensorFixture(const vespalib::string &type_, const vespalib::string &name)
        : type(type_),
          attribute(makeTensorAttribute<TensorAttributeType>(name, type))
    {
    }

    void setTensor(const TensorSpec &spec) {
        auto tensor = makeTensor(spec);
        attribute->setTensor(1, *tensor);
        attribute->commit();
    }

    void assertTensor(const TensorSpec &expSpec) {
        auto actual = spec_from_value(*attribute->getTensor(1));
        EXPECT_EQUAL(expSpec, actual);
    }
};

TEST_F("require that tensor modify update is applied",
        TensorFixture<DenseTensorAttribute>("tensor(x[2])", "dense_tensor"))
{
    f.setTensor(TensorSpec(f.type).add({{"x", 0}}, 3).add({{"x", 1}}, 5));
    f.applyValueUpdate(*f.attribute, 1,
                       std::make_unique<TensorModifyUpdate>(TensorModifyUpdate::Operation::REPLACE,
                                          makeTensorFieldValue(TensorSpec("tensor(x{})").add({{"x", "0"}}, 7))));
    f.assertTensor(TensorSpec(f.type).add({{"x", 0}}, 7).add({{"x", 1}}, 5));
}

TEST_F("require that tensor add update is applied",
        TensorFixture<SerializedFastValueAttribute>("tensor(x{})", "sparse_tensor"))
{
    f.setTensor(TensorSpec(f.type).add({{"x", "a"}}, 2));
    f.applyValueUpdate(*f.attribute, 1,
                       std::make_unique<TensorAddUpdate>(makeTensorFieldValue(TensorSpec(f.type).add({{"x", "a"}}, 3))));
    f.assertTensor(TensorSpec(f.type).add({{"x", "a"}}, 3));
}

TEST_F("require that tensor add update to non-existing tensor creates empty tensor first",
       TensorFixture<SerializedFastValueAttribute>("tensor(x{})", "sparse_tensor"))
{
    f.applyValueUpdate(*f.attribute, 1,
                       std::make_unique<TensorAddUpdate>(makeTensorFieldValue(TensorSpec(f.type).add({{"x", "a"}}, 3))));
    f.assertTensor(TensorSpec(f.type).add({{"x", "a"}}, 3));
}

TEST_F("require that tensor remove update is applied",
        TensorFixture<SerializedFastValueAttribute>("tensor(x{})", "sparse_tensor"))
{
    f.setTensor(TensorSpec(f.type).add({{"x", "a"}}, 2).add({{"x", "b"}}, 3));
    f.applyValueUpdate(*f.attribute, 1,
                       std::make_unique<TensorRemoveUpdate>(makeTensorFieldValue(TensorSpec(f.type).add({{"x", "b"}}, 1))));
    f.assertTensor(TensorSpec(f.type).add({{"x", "a"}}, 2));
}

}

TEST_MAIN() { TEST_RUN_ALL(); }

