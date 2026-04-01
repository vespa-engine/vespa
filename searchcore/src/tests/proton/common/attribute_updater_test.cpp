// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/common/attribute_updater.h>
#include <vespa/searchlib/attribute/array_bool_attribute.h>
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
#include <vespa/document/fieldvalue/boolfieldvalue.h>
#include <vespa/document/fieldvalue/bytefieldvalue.h>
#include <vespa/document/fieldvalue/floatfieldvalue.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/rawfieldvalue.h>
#include <vespa/document/fieldvalue/referencefieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/document/repo/newconfigbuilder.h>
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
#include <vespa/vespalib/gtest/gtest.h>
#include <optional>

using namespace document;
using search::AttributeFactory;
using search::AttributeVector;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::attribute::Reference;
using search::attribute::ReferenceAttribute;
using search::attribute::ArrayBoolAttribute;
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

using namespace std::literals;

namespace {

std::vector<char> as_vector(std::string_view value) {
    return {value.data(), value.data() + value.size()};
}

std::vector<char> as_vector(std::span<const char> value) {
    return {value.data(), value.data() + value.size()};
}

}

namespace attribute_updater_test {

using AttributePtr = AttributeVector::SP;
using WeightedInt = AttributeVector::WeightedInt;
using WeightedFloat = AttributeVector::WeightedFloat;
using WeightedString = AttributeVector::WeightedString;

std::unique_ptr<DocumentTypeRepo>
makeDocumentTypeRepo()
{
    new_config_builder::NewConfigBuilder builder;
    auto& doc = builder.document("testdoc", 222);
    auto bool_array = doc.createArray(builder.boolTypeRef()).ref();
    auto int_array = doc.createArray(builder.intTypeRef()).ref();
    auto float_array = doc.createArray(builder.floatTypeRef()).ref();
    auto string_array = doc.createArray(builder.stringTypeRef()).ref();
    auto int_wset = doc.createWset(builder.intTypeRef()).ref();
    auto float_wset = doc.createWset(builder.floatTypeRef()).ref();
    auto string_wset = doc.createWset(builder.stringTypeRef()).ref();
    // Create self-referencing type (reference to testdoc itself)
    auto ref_type = doc.referenceType(doc.idx());
    doc.addField("int", builder.intTypeRef())
            .addField("float", builder.floatTypeRef())
            .addField("string", builder.stringTypeRef())
            .addField("raw", builder.rawTypeRef())
            .addField("abool", bool_array)
            .addField("aint", int_array)
            .addField("afloat", float_array)
            .addField("astring", string_array)
            .addField("wsint", int_wset)
            .addField("wsfloat", float_wset)
            .addField("wsstring", string_wset)
            .addField("ref", ref_type);
    doc.addTensorField("dense_tensor", "tensor(x[2])")
            .addTensorField("sparse_tensor", "tensor(x{})");
    return std::make_unique<DocumentTypeRepo>(builder.config());
}

std::unique_ptr<DocumentTypeRepo> repo = makeDocumentTypeRepo();

struct Fixture {
    const DocumentType *docType;

    Fixture()
        : docType(repo->getDocumentType("testdoc"))
    {
    }
    ~Fixture();

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

Fixture::~Fixture() = default;

template <typename T>
bool
check(const AttributePtr &vec, uint32_t docId, const std::vector<T> &values)
{
    uint32_t sz = vec->getValueCount(docId);
    bool failed = false;
    EXPECT_EQ(sz, values.size()) << (failed = true, "");
    if (failed) {
        return false;
    }
    std::vector<T> buf(sz);
    uint32_t asz = vec->get(docId, buf.data(), sz);
    EXPECT_EQ(sz, asz) << (failed = true, "");
    if (failed) {
        return false;
    }
    std::vector<T> wanted(values.begin(), values.end());
    if (vec->hasWeightedSetType()) {
        std::sort(wanted.begin(), wanted.end(), value_then_weight_order());
        std::sort(buf.begin(), buf.end(), value_then_weight_order());
    }
    for (uint32_t i = 0; i < values.size(); ++i) {
        EXPECT_EQ(buf[i].getValue(), wanted[i].getValue()) << (failed = true, "");
        if (failed) {
            return false;
        }
        EXPECT_EQ(buf[i].getWeight(), wanted[i].getWeight()) << (failed = true, "");
        if (failed) {
            return false;
        }
    }
    return !failed;
}

GlobalId toGid(std::string_view docId) {
    return DocumentId(docId).getGlobalId();
}

std::string doc1("id:test:testdoc::1");
std::string doc2("id:test:testdoc::2");

ReferenceAttribute &asReferenceAttribute(AttributeVector &vec)
{
    return dynamic_cast<ReferenceAttribute &>(vec);
}

std::optional<GlobalId> get_ref(AttributeVector& vec, uint32_t doc)
{
    const Reference *ref = asReferenceAttribute(vec).getReference(doc);
    if (ref == nullptr) {
        return std::nullopt;
    }
    return ref->gid();
}

TEST(AttributeUpdaterTest, require_that_single_attributes_are_updated)
{
    Fixture f;
    using search::attribute::getUndefined;
    CollectionType ct(CollectionType::SINGLE);
    {
        auto vec = AttributeBuilder("in1/int", Config(BasicType::INT32)).fill({ 32, 32, 32}).get();
        f.applyValueUpdate(*vec, 1, std::make_unique<AssignValueUpdate>(std::make_unique<IntFieldValue>(64)));
        f.applyValueUpdate(*vec, 2, std::make_unique<ArithmeticValueUpdate>(ArithmeticValueUpdate::Add, 10));
        f.applyValueUpdate(*vec, 3, std::make_unique<ClearValueUpdate>());
        EXPECT_EQ(4u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 1, std::vector<WeightedInt>{WeightedInt(64)}));
        EXPECT_TRUE(check(vec, 2, std::vector<WeightedInt>{WeightedInt(42)}));
        EXPECT_TRUE(check(vec, 3, std::vector<WeightedInt>{WeightedInt(getUndefined<int32_t>())}));
    }
    {
        auto vec = AttributeBuilder("in1/float", Config(BasicType::FLOAT)).fill({55.5, 55.5, 55.5}).get();
        f.applyValueUpdate(*vec, 1, std::make_unique<AssignValueUpdate>(std::make_unique<FloatFieldValue>(77.7f)));
        f.applyValueUpdate(*vec, 2, std::make_unique<ArithmeticValueUpdate>(ArithmeticValueUpdate::Add, 10));
        f.applyValueUpdate(*vec, 3, std::make_unique<ClearValueUpdate>());
        EXPECT_EQ(4u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 1, std::vector<WeightedFloat>{WeightedFloat(77.7f)}));
        EXPECT_TRUE(check(vec, 2, std::vector<WeightedFloat>{WeightedFloat(65.5f)}));
        EXPECT_TRUE(std::isnan(vec->getFloat(3)));
    }
    {
        auto vec = AttributeBuilder("in1/string", Config(BasicType::STRING)).fill({"first"s, "first"s, "first"s}).get();
        f.applyValueUpdate(*vec, 1, std::make_unique<AssignValueUpdate>(StringFieldValue::make("second")));
        f.applyValueUpdate(*vec, 3, std::make_unique<ClearValueUpdate>());
        EXPECT_EQ(4u, vec->getNumDocs());
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
        EXPECT_EQ(1u, startDoc);
        EXPECT_EQ(3u, endDoc);
        for (uint32_t docId = 1; docId < 4; ++docId) {
            asReferenceAttribute(*vec).update(docId, toGid(doc1));
        }
        vec->commit();
        f.applyValueUpdate(*vec, 1, std::make_unique<AssignValueUpdate>(std::make_unique<ReferenceFieldValue>(dynamic_cast<const ReferenceDataType &>(f.docType->getField("ref").getDataType()), DocumentId(doc2))));
        f.applyValueUpdate(*vec, 3, std::make_unique<ClearValueUpdate>());
        EXPECT_EQ(4u, vec->getNumDocs());
        EXPECT_EQ(std::optional<GlobalId>(toGid(doc2)), get_ref(*vec, 1));
        EXPECT_EQ(std::optional<GlobalId>(toGid(doc1)), get_ref(*vec, 2));
        EXPECT_EQ(std::optional<GlobalId>(), get_ref(*vec, 3));
    }
    {
        std::string first_backing("first");
        std::span<const char> first(first_backing.data(), first_backing.size());
        auto vec = AttributeBuilder("in1/raw", Config(BasicType::RAW)).fill({first, first, first, first}).get();
        f.applyValueUpdate(*vec, 1, std::make_unique<AssignValueUpdate>(std::make_unique<RawFieldValue>("second")));
        f.applyValueUpdate(*vec, 3, std::make_unique<ClearValueUpdate>());
        f.applyValue(*vec, 4, std::make_unique<RawFieldValue>("third"));
        EXPECT_EQ(5u, vec->getNumDocs());
        EXPECT_EQ(as_vector("second"sv), as_vector(vec->get_raw(1)));
        EXPECT_EQ(as_vector("first"sv), as_vector(vec->get_raw(2)));
        EXPECT_EQ(as_vector(""sv), as_vector(vec->get_raw(3)));
        EXPECT_EQ(as_vector("third"sv), as_vector(vec->get_raw(4)));
    }
}

TEST(AttributeUpdaterTest, require_that_array_attributes_are_updated)
{
    Fixture f;
    CollectionType ct(CollectionType::ARRAY);
    {
        using IL = AttributeBuilder::IntList;
        auto vec = AttributeBuilder("in1/aint", Config(BasicType::INT32, ct)).fill_array({IL{32}, {32}, {32}, {32}, {32}}).get();
        auto first = std::make_unique<IntFieldValue>(32);
        auto second = std::make_unique<IntFieldValue>(64);
        auto assign = std::make_unique<ArrayFieldValue>(f.docType->getField("aint").getDataType());
        assign->add(*second);
        f.applyArrayUpdates(*vec, std::move(assign), std::move(first), std::move(second));

        EXPECT_EQ(6u, vec->getNumDocs());
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

        EXPECT_EQ(6u, vec->getNumDocs());
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

        EXPECT_EQ(6u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 1, std::vector<WeightedString>{WeightedString("second")}));
        EXPECT_TRUE(check(vec, 2, std::vector<WeightedString>{WeightedString("first"), WeightedString("second")}));
        EXPECT_TRUE(check(vec, 3, std::vector<WeightedString>{}));
        EXPECT_TRUE(check(vec, 4, std::vector<WeightedString>{}));
        EXPECT_TRUE(check(vec, 5, std::vector<WeightedString>{WeightedString("first")}));
    }
}

TEST(AttributeUpdaterTest, require_that_array_bool_attribute_is_updated)
{
    Fixture f;
    Config cfg(BasicType::BOOL, CollectionType::ARRAY);
    auto attr = AttributeFactory::createAttribute("abool", cfg);
    attr->addReservedDoc();
    attr->addDocs(4);
    auto& bool_attr = dynamic_cast<ArrayBoolAttribute&>(*attr);
    bool_attr.set_bools(1, std::vector<int8_t>{1, 0, 1});
    bool_attr.set_bools(2, std::vector<int8_t>{0, 1});
    bool_attr.set_bools(3, std::vector<int8_t>{1});
    bool_attr.set_bools(4, std::vector<int8_t>{1, 0});
    attr->commit();

    // Assign via handleUpdate
    auto assign = std::make_unique<ArrayFieldValue>(f.docType->getField("abool").getDataType());
    assign->add(BoolFieldValue(false));
    assign->add(BoolFieldValue(true));
    f.applyValueUpdate(*attr, 1, std::make_unique<AssignValueUpdate>(std::move(assign)));
    EXPECT_EQ(2u, attr->getValueCount(1));
    EXPECT_TRUE(check(attr, 1, std::vector<WeightedInt>{WeightedInt(0), WeightedInt(1)}));

    // Add (append) via handleUpdate
    f.applyValueUpdate(*attr, 2, std::make_unique<AddValueUpdate>(std::make_unique<BoolFieldValue>(true)));
    EXPECT_EQ(3u, attr->getValueCount(2));
    EXPECT_TRUE(check(attr, 2, std::vector<WeightedInt>{WeightedInt(0), WeightedInt(1), WeightedInt(1)}));

    // Clear via handleUpdate, then append to empty array
    f.applyValueUpdate(*attr, 3, std::make_unique<ClearValueUpdate>());
    EXPECT_EQ(0u, attr->getValueCount(3));
    f.applyValueUpdate(*attr, 3, std::make_unique<AddValueUpdate>(std::make_unique<BoolFieldValue>(false)));
    EXPECT_EQ(1u, attr->getValueCount(3));
    EXPECT_TRUE(check(attr, 3, std::vector<WeightedInt>{WeightedInt(0)}));

    // Put via handleValue
    auto put_val = std::make_unique<ArrayFieldValue>(f.docType->getField("abool").getDataType());
    put_val->add(BoolFieldValue(true));
    put_val->add(BoolFieldValue(true));
    put_val->add(BoolFieldValue(false));
    f.applyValue(*attr, 4, std::move(put_val));
    attr->commit();
    EXPECT_EQ(3u, attr->getValueCount(4));
    EXPECT_TRUE(check(attr, 4, std::vector<WeightedInt>{WeightedInt(1), WeightedInt(1), WeightedInt(0)}));
}

TEST(AttributeUpdaterTest, require_that_weighted_set_attributes_are_updated)
{
    Fixture f;
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

        EXPECT_EQ(6u, vec->getNumDocs());
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

        EXPECT_EQ(6u, vec->getNumDocs());
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

        EXPECT_EQ(6u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 1, std::vector<WeightedString>{WeightedString("second", 20)}));
        EXPECT_TRUE(check(vec, 2, std::vector<WeightedString>{WeightedString("first", 100), WeightedString("second", 20)}));
        EXPECT_TRUE(check(vec, 3, std::vector<WeightedString>{}));
        EXPECT_TRUE(check(vec, 4, std::vector<WeightedString>{}));
        EXPECT_TRUE(check(vec, 5, std::vector<WeightedString>{WeightedString("first", 110)}));
    }
}

template <typename TensorAttributeType>
std::unique_ptr<TensorAttributeType>
makeTensorAttribute(const std::string &name, const std::string &tensorType)
{
    Config cfg(BasicType::TENSOR, CollectionType::SINGLE);
    cfg.setTensorType(ValueType::from_spec(tensorType));
    auto result = std::make_unique<TensorAttributeType>(name, cfg);
    result->addReservedDoc();
    result->addDocs(1);
    return result;
}

vespalib::hash_map<std::string, std::unique_ptr<const TensorDataType>> tensorTypes;

const TensorDataType &
getTensorDataType(const std::string &spec)
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
    std::string type;
    std::unique_ptr<TensorAttributeType> attribute;

    TensorFixture(const std::string &type_, const std::string &name)
        : type(type_),
          attribute(makeTensorAttribute<TensorAttributeType>(name, type))
    {
    }
    ~TensorFixture();

    void setTensor(const TensorSpec &spec) {
        auto tensor = makeTensor(spec);
        attribute->setTensor(1, *tensor);
        attribute->commit();
    }

    void assertTensor(const TensorSpec &expSpec) {
        auto actual = spec_from_value(*attribute->getTensor(1));
        EXPECT_EQ(expSpec, actual);
    }
};

template <typename TensorAttributeType>
TensorFixture<TensorAttributeType>::~TensorFixture() = default;

TEST(AttributeUpdaterTest, require_that_tensor_modify_update_is_applied)
{
    TensorFixture<DenseTensorAttribute> f("tensor(x[2])", "dense_tensor");
    f.setTensor(TensorSpec(f.type).add({{"x", 0}}, 3).add({{"x", 1}}, 5));
    f.applyValueUpdate(*f.attribute, 1,
                       std::make_unique<TensorModifyUpdate>(TensorModifyUpdate::Operation::REPLACE,
                                          makeTensorFieldValue(TensorSpec("tensor(x{})").add({{"x", "0"}}, 7))));
    f.assertTensor(TensorSpec(f.type).add({{"x", 0}}, 7).add({{"x", 1}}, 5));
}

TEST(AttributeUpdaterTest, require_that_tensor_modify_update_with_create_true_is_applied_to_non_existing_tensor)
{
    TensorFixture<DenseTensorAttribute> f("tensor(x[2])", "dense_tensor");
    f.applyValueUpdate(*f.attribute, 1,
                       std::make_unique<TensorModifyUpdate>(TensorModifyUpdate::Operation::ADD,
                                                            makeTensorFieldValue(TensorSpec("tensor(x{})").add({{"x", "1"}}, 3)), 0.0));
    f.assertTensor(TensorSpec(f.type).add({{"x", 0}}, 0).add({{"x", 1}}, 3));
}

TEST(AttributeUpdaterTest, require_that_tensor_add_update_is_applied)
{
TensorFixture<SerializedFastValueAttribute> f("tensor(x{})", "sparse_tensor");
    f.setTensor(TensorSpec(f.type).add({{"x", "a"}}, 2));
    f.applyValueUpdate(*f.attribute, 1,
                       std::make_unique<TensorAddUpdate>(makeTensorFieldValue(TensorSpec(f.type).add({{"x", "a"}}, 3))));
    f.assertTensor(TensorSpec(f.type).add({{"x", "a"}}, 3));
}

TEST(AttributeUpdaterTest, require_that_tensor_add_update_to_non_existing_tensor_creates_empty_tensor_first)
{
TensorFixture<SerializedFastValueAttribute> f("tensor(x{})", "sparse_tensor");
    f.applyValueUpdate(*f.attribute, 1,
                       std::make_unique<TensorAddUpdate>(makeTensorFieldValue(TensorSpec(f.type).add({{"x", "a"}}, 3))));
    f.assertTensor(TensorSpec(f.type).add({{"x", "a"}}, 3));
}

TEST(AttributeUpdaterTest, require_that_tensor_remove_update_is_applied)
{
TensorFixture<SerializedFastValueAttribute> f("tensor(x{})", "sparse_tensor");
    f.setTensor(TensorSpec(f.type).add({{"x", "a"}}, 2).add({{"x", "b"}}, 3));
    f.applyValueUpdate(*f.attribute, 1,
                       std::make_unique<TensorRemoveUpdate>(makeTensorFieldValue(TensorSpec(f.type).add({{"x", "b"}}, 1))));
    f.assertTensor(TensorSpec(f.type).add({{"x", "a"}}, 2));
}

}
