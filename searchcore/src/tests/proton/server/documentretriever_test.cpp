// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for documentretriever.

#include <vespa/searchcore/proton/documentmetastore/documentmetastorecontext.h>
#include <vespa/searchcore/proton/server/documentretriever.h>
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/searchcore/proton/test/dummy_document_store.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/attributemanager.h>
#include <vespa/searchlib/attribute/floatbase.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/predicate_attribute.h>
#include <vespa/searchlib/attribute/single_raw_attribute.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/predicate/predicate_index.h>
#include <vespa/searchlib/tensor/tensor_attribute.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/document/base/documentid.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/document/datatype/datatype.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/doublefieldvalue.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/longfieldvalue.h>
#include <vespa/document/fieldvalue/predicatefieldvalue.h>
#include <vespa/document/fieldvalue/rawfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/structfieldvalue.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/test/fieldvalue_helpers.h>
#include <vespa/vespalib/geo/zcurve.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/test/value_compare.h>
#include <vespa/persistence/spi/bucket.h>
#include <vespa/persistence/spi/test.h>


#include <vespa/log/log.h>
LOG_SETUP("document_retriever_test");

using document::ArrayFieldValue;
using document::FieldValue;
using document::BucketId;
using document::Document;
using document::DocumentId;
using document::DocumentType;
using document::DocumentTypeRepo;
using document::DoubleFieldValue;
using document::GlobalId;
using document::IntFieldValue;
using document::LongFieldValue;
using document::PositionDataType;
using document::PredicateFieldValue;
using document::RawFieldValue;
using document::StringFieldValue;
using document::StructFieldValue;
using document::TensorDataType;
using document::TensorFieldValue;
using document::WeightedSetFieldValue;
using document::WSetHelper;
using search::AttributeFactory;
using search::AttributeGuard;
using search::AttributeVector;
using search::DocumentIdT;
using search::DocumentMetaData;
using search::FloatingPointAttribute;
using search::IDocumentStore;
using search::IntegerAttribute;
using search::PredicateAttribute;
using search::StringAttribute;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::attribute::IAttributeVector;
using search::attribute::SingleRawAttribute;
using search::index::Schema;
using search::index::schema::DataType;
using search::tensor::TensorAttribute;
using storage::spi::Bucket;
using storage::spi::Timestamp;
using storage::spi::test::makeSpiBucket;
using vespalib::CacheStats;
using vespalib::make_string;
using vespalib::string;
using vespalib::eval::SimpleValue;
using vespalib::eval::TensorSpec;
using vespalib::eval::ValueType;
using vespalib::eval::Value;
using proton::documentmetastore::IStore;
using namespace document::config_builder;
using namespace search::index;

using namespace proton;

namespace {

const string doc_type_name = "type_name";
const char static_field[] = "static field";
const char dyn_field_i[] = "dynamic int field";
const char dyn_field_d[] = "dynamic double field";
const char dyn_field_s[] = "dynamic string field";
const char dyn_field_n[] = "dynamic null field"; // not in document, not in attribute
const char dyn_field_nai[] = "dynamic null attr int field"; // in document, not in attribute
const char dyn_field_nas[] = "dynamic null attr string field"; // in document, not in attribute
const char position_field[] = "position_field";
vespalib::string dyn_field_raw("dynamic_raw_field");
vespalib::string dyn_field_tensor("dynamic_tensor_field");
vespalib::string static_raw_backing("static raw");
vespalib::string dynamic_raw_backing("dynamic raw");
vespalib::ConstArrayRef<char> dynamic_raw(dynamic_raw_backing.data(), dynamic_raw_backing.size());
vespalib::string tensor_spec("tensor(x{})");
std::unique_ptr<Value> static_tensor = SimpleValue::from_spec(TensorSpec(tensor_spec).add({{"x", "1"}}, 1.5));
std::unique_ptr<Value> dynamic_tensor = SimpleValue::from_spec(TensorSpec(tensor_spec).add({{"x", "2"}}, 3.5));
const char zcurve_field[] = "position_field_zcurve";
const char position_array_field[] = "position_array";
const char zcurve_array_field[] = "position_array_zcurve";
const char dyn_field_p[] = "dynamic predicate field";
const char dyn_arr_field_i[] = "dynamic int array field";
const char dyn_arr_field_d[] = "dynamic double array field";
const char dyn_arr_field_s[] = "dynamic string array field";
const char dyn_arr_field_n[] = "dynamic null array field";
const char dyn_wset_field_i[] = "dynamic int wset field";
const char dyn_wset_field_d[] = "dynamic double wset field";
const char dyn_wset_field_s[] = "dynamic string wset field";
const char dyn_wset_field_n[] = "dynamic null wset field";
const DocumentId doc_id("id:ns:type_name::1");
const int32_t static_value = 4;
const int32_t dyn_value_i = 17;
const double dyn_value_d = 42.42;
const char dyn_value_s[] = "Batman & Robin";
const char static_value_s[] = "Dynamic duo";
const PredicateFieldValue static_value_p;
const int32_t dyn_weight = 21;
const int64_t static_zcurve_value = 1118035438880ll;
const int64_t dynamic_zcurve_value = 6145423666930817152ll;
const TensorDataType tensorDataType(ValueType::from_spec(tensor_spec));

std::vector<char> as_vector(vespalib::stringref value) {
    return {value.data(), value.data() + value.size()};
}

struct MyDocumentStore : proton::test::DummyDocumentStore {
    mutable std::unique_ptr<Document> _testDoc;
    bool _set_position_struct_field;

    MyDocumentStore()
        : proton::test::DummyDocumentStore(),
          _testDoc(),
          _set_position_struct_field(true)
    {
    }

    ~MyDocumentStore() override;
    
    Document::UP read(DocumentIdT lid, const DocumentTypeRepo &r) const override {
        if (lid == 0) {
            return Document::UP();
        }
        if (_testDoc) {
            return std::move(_testDoc);
        }
        const DocumentType *doc_type = r.getDocumentType(doc_type_name);
        auto doc = std::make_unique<Document>(r, *doc_type, doc_id);
        ASSERT_TRUE(doc);
        doc->setValue(static_field, IntFieldValue::make(static_value));
        doc->setValue(dyn_field_i, IntFieldValue::make(static_value));
        doc->setValue(dyn_field_s, StringFieldValue::make(static_value_s));
        doc->setValue(dyn_field_nai, IntFieldValue::make(static_value));
        doc->setValue(dyn_field_nas, StringFieldValue::make(static_value_s));
        doc->setValue(zcurve_field, LongFieldValue::make(static_zcurve_value));
        doc->setValue(dyn_field_p, static_value_p);
        doc->setValue(dyn_field_raw, RawFieldValue(static_raw_backing.data(), static_raw_backing.size()));
        TensorFieldValue tensorFieldValue(tensorDataType);
        tensorFieldValue = SimpleValue::from_value(*static_tensor);
        doc->setValue(dyn_field_tensor, tensorFieldValue);
        if (_set_position_struct_field) {
            FieldValue::UP fv = PositionDataType::getInstance().createFieldValue();
            auto &pos = dynamic_cast<StructFieldValue &>(*fv);
            pos.setValue(PositionDataType::FIELD_X, IntFieldValue::make(42));
            pos.setValue(PositionDataType::FIELD_Y, IntFieldValue::make(21));
            doc->setValue(doc->getField(position_field), *fv);
        }

        return doc;
    }
    
    uint64_t
    initFlush(uint64_t syncToken) override
    {
        return syncToken;
    }
};

MyDocumentStore::~MyDocumentStore() = default;

DocumenttypesConfig getRepoConfig() {
    const int32_t doc_type_id = 787121340;

    DocumenttypesConfigBuilderHelper builder;
    builder.document(doc_type_id, doc_type_name,
                     Struct(doc_type_name + ".header"),
                     Struct(doc_type_name + ".body")
                     .addField(static_field, document::DataType::T_INT)
                     .addField(dyn_field_i, document::DataType::T_INT)
                     .addField(dyn_field_d, document::DataType::T_DOUBLE)
                     .addField(dyn_field_s, document::DataType::T_STRING)
                     .addField(dyn_field_n, document::DataType::T_FLOAT)
                     .addField(dyn_field_nai, document::DataType::T_INT)
                     .addField(dyn_field_nas, document::DataType::T_STRING)
                     .addField(dyn_field_p, document::DataType::T_PREDICATE)
                     .addField(dyn_arr_field_i, Array(document::DataType::T_INT))
                     .addField(dyn_arr_field_d, Array(document::DataType::T_DOUBLE))
                     .addField(dyn_arr_field_s, Array(document::DataType::T_STRING))
                     .addField(dyn_arr_field_n, Array(document::DataType::T_FLOAT))
                     .addField(dyn_wset_field_i, Wset(document::DataType::T_INT))
                     .addField(dyn_wset_field_d, Wset(document::DataType::T_DOUBLE))
                     .addField(dyn_wset_field_s, Wset(document::DataType::T_STRING))
                     .addField(dyn_wset_field_n, Wset(document::DataType::T_FLOAT))
                     .addField(position_field, PositionDataType::getInstance().getId())
                     .addField(dyn_field_raw, document::DataType::T_RAW)

                     .addTensorField(dyn_field_tensor, tensor_spec)
                     .addField(zcurve_field, document::DataType::T_LONG)
                     .addField(position_array_field, Array(PositionDataType::getInstance().getId()))
                     .addField(zcurve_array_field, Array(document::DataType::T_LONG)));
     return builder.config();
}

BasicType
convertDataType(Schema::DataType t)
{
    switch (t) {
    case DataType::INT32:
        return BasicType::INT32;
    case DataType::INT64:
        return BasicType::INT64;
    case DataType::FLOAT:
        return BasicType::FLOAT;
    case DataType::DOUBLE:
        return BasicType::DOUBLE;
    case DataType::STRING:
        return BasicType::STRING;
    case DataType::BOOLEANTREE:
        return BasicType::PREDICATE;
    case DataType::TENSOR:
        return BasicType::TENSOR;
    case DataType::RAW:
        return BasicType::RAW;
    default:
        throw std::runtime_error(make_string("Data type %u not handled", (uint32_t)t));
    }
}

CollectionType
convertCollectionType(Schema::CollectionType ct)
{
    switch (ct) {
    case schema::CollectionType::SINGLE:
        return CollectionType::SINGLE;
    case schema::CollectionType::ARRAY:
        return CollectionType::ARRAY;
    case schema::CollectionType::WEIGHTEDSET:
        return CollectionType::WSET;
    default:
        throw std::runtime_error(make_string("Collection type %u not handled", (uint32_t)ct));
    }
}

search::attribute::Config
convertConfig(Schema::DataType t, Schema::CollectionType ct)
{
    search::attribute::Config cfg(convertDataType(t), convertCollectionType(ct));
    if (cfg.basicType().type() == BasicType::TENSOR) {
        cfg.setTensorType(ValueType::from_spec(tensor_spec));
    }
    return cfg;
}

struct Fixture {
    DocumentTypeRepo repo;
    DocumentMetaStoreContext meta_store;
    const GlobalId &gid;
    BucketId bucket_id;
    Timestamp timestamp;
    IStore::DocId lid;
    MyDocumentStore doc_store;
    search::AttributeManager attr_manager;
    Schema schema;
    DocTypeName _dtName;
    std::unique_ptr<IDocumentRetriever> _retriever;

    template <typename T>
    T *addAttribute(const char *name, Schema::DataType t, Schema::CollectionType ct) {
        AttributeVector::SP attrPtr = AttributeFactory::createAttribute(name, convertConfig(t, ct));
        T *attr = dynamic_cast<T *>(attrPtr.get());
        AttributeVector::DocId id;
        attr_manager.add(attrPtr);
        attr->addReservedDoc();
        attr->addDoc(id);
        attr->clearDoc(id);
        EXPECT_EQUAL(id, lid);
        schema.addAttributeField(Schema::Field(name, t, ct));
        attr->commit();
        return attr;
    }

    template <typename T, typename U>
    void addAttribute(const char *name, U val, Schema::DataType t, Schema::CollectionType ct) {
        T *attr = addAttribute<T>(name, t, ct);
        if (ct == schema::CollectionType::SINGLE) {
            attr->update(lid, val);
        } else {
            attr->append(lid, val + 1, dyn_weight);
            attr->append(lid, val, dyn_weight);
        }
        attr->commit();
    }
    void addTensorAttribute(const char *name, const Value &val) {
        auto * attr = addAttribute<TensorAttribute>(name, schema::DataType::TENSOR, schema::CollectionType::SINGLE);
        attr->setTensor(lid, val);
        attr->commit();
    }

    void add_raw_attribute(const char *name, vespalib::ConstArrayRef<char> val) {
        auto* attr = addAttribute<SingleRawAttribute>(name, schema::DataType::RAW, schema::CollectionType::SINGLE);
        attr->set_raw(lid, val);
        attr->commit();
    }

    Fixture &
    addIndexField(const Schema::IndexField &field) {
        schema.addIndexField(field);
        return *this;
    }

    void
    build() {
        _retriever = std::make_unique<DocumentRetriever>(_dtName, repo, schema, meta_store, attr_manager, doc_store);
    }

    Fixture()
        : repo(getRepoConfig()),
          meta_store(std::make_shared<bucketdb::BucketDBOwner>()),
          gid(doc_id.getGlobalId()),
          bucket_id(gid.convertToBucketId()),
          timestamp(21),
          lid(),
          doc_store(),
          attr_manager(),
          schema(),
          _dtName(doc_type_name),
          _retriever()
    {
        meta_store.constructFreeList();
        IStore::Result inspect = meta_store.get().inspect(gid, 0u);
        uint32_t docSize = 1;
        IStore::Result putRes(meta_store.get().put(gid, bucket_id, timestamp, docSize, inspect.getLid(), 0u));
        meta_store.get().commit(search::CommitParam(0));
        lid = putRes.getLid();
        ASSERT_TRUE(putRes.ok());
        schema::CollectionType ct = schema::CollectionType::SINGLE;
        addAttribute<IntegerAttribute>(dyn_field_i, dyn_value_i, DataType::INT32, ct);
        addAttribute<FloatingPointAttribute>(dyn_field_d, dyn_value_d, DataType::DOUBLE, ct);
        addAttribute<StringAttribute>(dyn_field_s, dyn_value_s, DataType::STRING, ct);
        addAttribute<FloatingPointAttribute>(dyn_field_n, DataType::FLOAT, ct);
        addAttribute<IntegerAttribute>(dyn_field_nai, DataType::INT32, ct);
        addAttribute<StringAttribute>(dyn_field_nas, DataType::STRING, ct);
        addAttribute<IntegerAttribute>(zcurve_field, dynamic_zcurve_value, DataType::INT64, ct);
        add_raw_attribute(dyn_field_raw.c_str(), dynamic_raw);
        addTensorAttribute(dyn_field_tensor.c_str(), *dynamic_tensor);
        auto * attr = addAttribute<PredicateAttribute>(dyn_field_p, DataType::BOOLEANTREE, ct);
        attr->getIndex().indexEmptyDocument(lid);
        attr->commit();
        ct = schema::CollectionType::ARRAY;
        addAttribute<IntegerAttribute>(dyn_arr_field_i, dyn_value_i, DataType::INT32, ct);
        addAttribute<FloatingPointAttribute>(dyn_arr_field_d, dyn_value_d, DataType::DOUBLE, ct);
        addAttribute<StringAttribute>(dyn_arr_field_s, dyn_value_s, DataType::STRING, ct);
        addAttribute<FloatingPointAttribute>(dyn_arr_field_n, DataType::FLOAT, ct);
        addAttribute<IntegerAttribute>(zcurve_array_field, dynamic_zcurve_value, DataType::INT64, ct);
        ct = schema::CollectionType::WEIGHTEDSET;
        addAttribute<IntegerAttribute>(dyn_wset_field_i, dyn_value_i, DataType::INT32, ct);
        addAttribute<FloatingPointAttribute>(dyn_wset_field_d, dyn_value_d, DataType::DOUBLE, ct);
        addAttribute<StringAttribute>(dyn_wset_field_s, dyn_value_s, DataType::STRING, ct);
        addAttribute<FloatingPointAttribute>(dyn_wset_field_n, DataType::FLOAT, ct);
        build();
    }

    void clearAttributes(const std::vector<vespalib::string> & names) const {
        for (const auto &name : names) {
            auto guard = *attr_manager.getAttribute(name);
            guard->clearDoc(lid);
            guard->commit();
        }
    }
};

TEST_F("require that document retriever can retrieve document meta data", Fixture) {
    DocumentMetaData meta_data = f._retriever->getDocumentMetaData(doc_id);
    EXPECT_EQUAL(f.lid, meta_data.lid);
    EXPECT_EQUAL(f.timestamp, meta_data.timestamp);
}

TEST_F("require that document retriever can retrieve bucket meta data", Fixture) {
    DocumentMetaData::Vector result;
    f._retriever->getBucketMetaData(makeSpiBucket(f.bucket_id), result);
    ASSERT_EQUAL(1u, result.size());
    EXPECT_EQUAL(f.lid, result[0].lid);
    EXPECT_EQUAL(f.timestamp, result[0].timestamp);
    result.clear();
    f._retriever->getBucketMetaData(makeSpiBucket(BucketId(f.bucket_id.getId() + 1)), result);
    EXPECT_EQUAL(0u, result.size());
}

TEST_F("require that document retriever can retrieve document", Fixture) {
    DocumentMetaData meta_data = f._retriever->getDocumentMetaData(doc_id);
    Document::UP doc = f._retriever->getDocument(meta_data.lid, doc_id);
    ASSERT_TRUE(doc);
    EXPECT_EQUAL(doc_id, doc->getId());
}

template <typename T>
bool checkFieldValue(FieldValue::UP field_value, typename T::value_type v) {
    ASSERT_TRUE(field_value);
    T *t_value = dynamic_cast<T *>(field_value.get());
    ASSERT_TRUE(t_value);
    return EXPECT_EQUAL(v, t_value->getValue());
}

template <typename T>
void checkArray(FieldValue::UP array, typename T::value_type v) {
    ASSERT_TRUE(array);
    auto *array_val = dynamic_cast<ArrayFieldValue *>(array.get());
    ASSERT_TRUE(array_val);
    ASSERT_EQUAL(2u, array_val->size());
    T *t_value = dynamic_cast<T *>(&(*array_val)[0]);
    ASSERT_TRUE(t_value);
    t_value = dynamic_cast<T *>(&(*array_val)[1]);
    ASSERT_TRUE(t_value);
    EXPECT_EQUAL(v, t_value->getValue());
}

template <typename T>
void checkWset(FieldValue::UP wset, T v) {
    ASSERT_TRUE(wset);
    auto *wset_val = dynamic_cast<WeightedSetFieldValue *>(wset.get());
    WSetHelper val(*wset_val);
    ASSERT_TRUE(wset_val);
    ASSERT_EQUAL(2u, wset_val->size());
    EXPECT_EQUAL(dyn_weight, val.get(v));
    EXPECT_EQUAL(dyn_weight, val.get(v + 1));
}

TEST_F("require that attributes are patched into stored document", Fixture) {
    DocumentMetaData meta_data = f._retriever->getDocumentMetaData(doc_id);
    Document::UP doc = f._retriever->getDocument(meta_data.lid, doc_id);
    ASSERT_TRUE(doc);

    FieldValue::UP value = doc->getValue(static_field);
    ASSERT_TRUE(value);
    auto *int_value = dynamic_cast<IntFieldValue *>(value.get());
    ASSERT_TRUE(int_value);
    EXPECT_EQUAL(static_value, int_value->getValue());

    EXPECT_TRUE(checkFieldValue<IntFieldValue>(doc->getValue(static_field), static_value));
    EXPECT_TRUE(checkFieldValue<IntFieldValue>(doc->getValue(dyn_field_i), dyn_value_i));
    EXPECT_TRUE(checkFieldValue<DoubleFieldValue>(doc->getValue(dyn_field_d), dyn_value_d));
    EXPECT_TRUE(checkFieldValue<StringFieldValue>(doc->getValue(dyn_field_s), dyn_value_s));
    EXPECT_FALSE(doc->getValue(dyn_field_n));
    EXPECT_FALSE(doc->getValue(dyn_field_nai));
    EXPECT_FALSE(doc->getValue(dyn_field_nas));

    checkArray<IntFieldValue>(doc->getValue(dyn_arr_field_i), dyn_value_i);
    checkArray<DoubleFieldValue>(doc->getValue(dyn_arr_field_d), dyn_value_d);
    checkArray<StringFieldValue>(doc->getValue(dyn_arr_field_s), dyn_value_s);
    EXPECT_FALSE(doc->getValue(dyn_arr_field_n));

    checkWset(doc->getValue(dyn_wset_field_i), dyn_value_i);
    checkWset(doc->getValue(dyn_wset_field_d), dyn_value_d);
    checkWset(doc->getValue(dyn_wset_field_s), dyn_value_s);
    EXPECT_FALSE(doc->getValue(dyn_wset_field_n));
}

TEST_F("require that we can look up NONE and DOCIDONLY field sets", Fixture) {
        DocumentMetaData meta_data = f._retriever->getDocumentMetaData(doc_id);
        Document::UP doc = f._retriever->getPartialDocument(meta_data.lid, doc_id, document::NoFields());
        ASSERT_TRUE(doc);
        EXPECT_TRUE(doc->getFields().empty());
        doc = f._retriever->getPartialDocument(meta_data.lid, doc_id, document::DocIdOnly());
        ASSERT_TRUE(doc);
        EXPECT_TRUE(doc->getFields().empty());
}

TEST_F("require that attributes are patched into stored document unless also index field", Fixture) {
    f.addIndexField(Schema::IndexField(dyn_field_s, DataType::STRING)).build();
    DocumentMetaData meta_data = f._retriever->getDocumentMetaData(doc_id);
    Document::UP doc = f._retriever->getDocument(meta_data.lid, doc_id);
    ASSERT_TRUE(doc);
    checkFieldValue<StringFieldValue>(doc->getValue(dyn_field_s), static_value_s);
}

void verify_position_field_has_expected_values(Fixture& f) {
    DocumentMetaData meta_data = f._retriever->getDocumentMetaData(doc_id);
    Document::UP doc = f._retriever->getDocument(meta_data.lid, doc_id);
    ASSERT_TRUE(doc);

    FieldValue::UP value = doc->getValue(position_field);
    ASSERT_TRUE(value);
    const auto *position = dynamic_cast<StructFieldValue *>(value.get());
    ASSERT_TRUE(position);
    FieldValue::UP x = position->getValue(PositionDataType::FIELD_X);
    FieldValue::UP y = position->getValue(PositionDataType::FIELD_Y);
    EXPECT_EQUAL(-123096000, dynamic_cast<IntFieldValue&>(*x).getValue());
    EXPECT_EQUAL(49401000, dynamic_cast<IntFieldValue&>(*y).getValue());

    checkFieldValue<LongFieldValue>(doc->getValue(zcurve_field), dynamic_zcurve_value);
}

TEST_F("require that single value position fields are regenerated from zcurves", Fixture) {
    verify_position_field_has_expected_values(f);
}

TEST_F("zcurve attribute is authoritative for single value position field existence", Fixture) {
    f.doc_store._set_position_struct_field = false;
    verify_position_field_has_expected_values(f);
}

TEST_F("require that array position field value is generated from zcurve array attribute", Fixture) {
    DocumentMetaData meta_data = f._retriever->getDocumentMetaData(doc_id);
    Document::UP doc = f._retriever->getDocument(meta_data.lid, doc_id);
    ASSERT_TRUE(doc);
    FieldValue::UP value = doc->getValue(position_array_field);
    ASSERT_TRUE(value);
    const auto* array_value = dynamic_cast<const document::ArrayFieldValue*>(value.get());
    ASSERT_TRUE(array_value != nullptr);
    ASSERT_EQUAL(array_value->getNestedType(), document::PositionDataType::getInstance());
    // Should have two elements prepopulated
    ASSERT_EQUAL(2u, array_value->size());
    for (uint32_t i = 0; i < array_value->size(); ++i) {
        // Magic index-specific value set by collection fixture code.
        int64_t zcurve_at_pos = ((i == 0) ? dynamic_zcurve_value + 1 : dynamic_zcurve_value);
        int32_t zx, zy;
        vespalib::geo::ZCurve::decode(zcurve_at_pos, &zx, &zy);

        const auto *position = dynamic_cast<const StructFieldValue*>(&(*array_value)[i]);
        ASSERT_TRUE(position != nullptr);
        FieldValue::UP x = position->getValue(PositionDataType::FIELD_X);
        FieldValue::UP y = position->getValue(PositionDataType::FIELD_Y);
        EXPECT_EQUAL(zx, dynamic_cast<IntFieldValue&>(*x).getValue());
        EXPECT_EQUAL(zy, dynamic_cast<IntFieldValue&>(*y).getValue());
    }
}

TEST_F("require that non-existing lid returns null pointer", Fixture) {
    Document::UP doc = f._retriever->getDocument(0, DocumentId("id:ns:document::1"));
    ASSERT_FALSE(doc);
}

TEST_F("require that predicate attributes can be retrieved", Fixture) {
    DocumentMetaData meta_data = f._retriever->getDocumentMetaData(doc_id);
    Document::UP doc = f._retriever->getDocument(meta_data.lid, doc_id);
    ASSERT_TRUE(doc);

    FieldValue::UP value = doc->getValue(dyn_field_p);
    ASSERT_TRUE(value);
    auto *predicate_value = dynamic_cast<PredicateFieldValue *>(value.get());
    ASSERT_TRUE(predicate_value);
}

TEST_F("require that zero values in multivalue attribute removes fields", Fixture)
{
    auto meta_data = f._retriever->getDocumentMetaData(doc_id);
    auto doc = f._retriever->getDocument(meta_data.lid, doc_id);
    ASSERT_TRUE(doc);
    const Document *docPtr = doc.get();
    ASSERT_TRUE(doc->hasValue(dyn_arr_field_i));
    ASSERT_TRUE(doc->hasValue(dyn_wset_field_i));
    f.doc_store._testDoc = std::move(doc);
    f.clearAttributes({ dyn_arr_field_i, dyn_wset_field_i });
    doc = f._retriever->getDocument(meta_data.lid, doc_id);
    EXPECT_EQUAL(docPtr, doc.get());
    ASSERT_FALSE(doc->hasValue(dyn_arr_field_i));
    ASSERT_FALSE(doc->hasValue(dyn_wset_field_i));
}

TEST_F("require that tensor attribute can be retrieved", Fixture) {
    DocumentMetaData meta_data = f._retriever->getDocumentMetaData(doc_id);
    Document::UP doc = f._retriever->getDocument(meta_data.lid, doc_id);
    ASSERT_TRUE(doc);

    FieldValue::UP value = doc->getValue(dyn_field_tensor);
    ASSERT_TRUE(value);
    auto * tensor_value = dynamic_cast<TensorFieldValue *>(value.get());
    ASSERT_EQUAL(*tensor_value->getAsTensorPtr(), *dynamic_tensor);
}

TEST_F("require that raw attribute can be retrieved", Fixture)
{
    DocumentMetaData meta_data = f._retriever->getDocumentMetaData(doc_id);
    Document::UP doc = f._retriever->getDocument(meta_data.lid, doc_id);
    ASSERT_TRUE(doc);

    auto value = doc->getValue(dyn_field_raw);
    ASSERT_TRUE(value);
    auto& raw_value = dynamic_cast<RawFieldValue&>(*value);
    auto raw_value_ref = raw_value.getValueRef();
    ASSERT_EQUAL(as_vector(dynamic_raw_backing), as_vector(raw_value_ref));;

    f.clearAttributes({ dyn_field_raw });
    doc = f._retriever->getDocument(meta_data.lid, doc_id);
    ASSERT_TRUE(doc);
    value = doc->getValue(dyn_field_raw);
    ASSERT_FALSE(value);
}

struct Lookup : public IFieldInfo
{
    Lookup() : _count(0) {}
    bool isFieldAttribute(const document::Field & field) const override {
        _count++;
        return ((field.getName()[0] % 2) == 1); // a, c, e... are attributes
    }
    mutable unsigned _count;
};

TEST("require that fieldset can figure out their attributeness and rember it") {
    Lookup lookup;
    FieldSetAttributeDB fsDB(lookup);
    document::Field attr1("attr1", 1, *document::DataType::LONG);
    document::Field attr2("cttr2", 2, *document::DataType::LONG);
    document::Field not_attr1("b_not_attr1", 3, *document::DataType::LONG);
    document::Field::Set allAttr = document::Field::Set::Builder().add(&attr1).build();
    EXPECT_TRUE(fsDB.areAllFieldsAttributes(13, allAttr));
    EXPECT_EQUAL(1u, lookup._count);
    EXPECT_TRUE(fsDB.areAllFieldsAttributes(13, allAttr));
    EXPECT_EQUAL(1u, lookup._count);

    allAttr = document::Field::Set::Builder().add(&attr1).add(&attr2).build();
    EXPECT_TRUE(fsDB.areAllFieldsAttributes(17, allAttr));
    EXPECT_EQUAL(3u, lookup._count);
    EXPECT_TRUE(fsDB.areAllFieldsAttributes(17, allAttr));
    EXPECT_EQUAL(3u, lookup._count);

    document::Field::Set notAllAttr = document::Field::Set::Builder().add(&not_attr1).build();
    EXPECT_FALSE(fsDB.areAllFieldsAttributes(33, notAllAttr));
    EXPECT_EQUAL(4u, lookup._count);
    EXPECT_FALSE(fsDB.areAllFieldsAttributes(33, notAllAttr));
    EXPECT_EQUAL(4u, lookup._count);

    notAllAttr = document::Field::Set::Builder().add(&attr1).add(&not_attr1).add(&attr2).build();
    EXPECT_FALSE(fsDB.areAllFieldsAttributes(39, notAllAttr));
    EXPECT_EQUAL(6u, lookup._count);
    EXPECT_FALSE(fsDB.areAllFieldsAttributes(39, notAllAttr));
    EXPECT_EQUAL(6u, lookup._count);
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
