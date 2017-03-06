// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for documentretriever.

#include <vespa/fastos/fastos.h>
#include <vespa/document/base/documentid.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/document/datatype/datatype.h>
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/doublefieldvalue.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/longfieldvalue.h>
#include <vespa/document/fieldvalue/predicatefieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/structfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/persistence/spi/bucket.h>
#include <vespa/persistence/spi/result.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastorecontext.h>
#include <vespa/searchcore/proton/server/documentretriever.h>
#include <vespa/searchcore/proton/test/dummy_document_store.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/attributemanager.h>
#include <vespa/searchlib/attribute/floatbase.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/predicate_attribute.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/docstore/cachestats.h>
#include <vespa/searchlib/docstore/idocumentstore.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/stringfmt.h>

using document::ArrayFieldValue;
using document::FieldValue;
using document::BucketId;
using document::DataType;
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
using document::StringFieldValue;
using document::StructFieldValue;
using document::WeightedSetFieldValue;
using search::AttributeFactory;
using search::AttributeGuard;
using search::AttributeVector;
using search::CacheStats;
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
using search::index::Schema;
using storage::spi::Bucket;
using storage::spi::GetResult;
using storage::spi::PartitionId;
using storage::spi::Timestamp;
using vespalib::make_string;
using vespalib::string;
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
const char zcurve_field[] = "position_field_zcurve";
const char dyn_field_p[] = "dynamic predicate field";
const char dyn_arr_field_i[] = "dynamic int array field";
const char dyn_arr_field_d[] = "dynamic double array field";
const char dyn_arr_field_s[] = "dynamic string array field";
const char dyn_arr_field_n[] = "dynamic null array field";
const char dyn_wset_field_i[] = "dynamic int wset field";
const char dyn_wset_field_d[] = "dynamic double wset field";
const char dyn_wset_field_s[] = "dynamic string wset field";
const char dyn_wset_field_n[] = "dynamic null wset field";
const DocumentId doc_id("doc:test:1");
const int32_t static_value = 4;
const int32_t dyn_value_i = 17;
const double dyn_value_d = 42.42;
const char dyn_value_s[] = "Batman & Robin";
const char static_value_s[] = "Dynamic duo";
const PredicateFieldValue static_value_p;
const int32_t dyn_weight = 21;
const int64_t static_zcurve_value = 1118035438880ll;
const int64_t dynamic_zcurve_value = 6145423666930817152ll;

struct MyDocumentStore : proton::test::DummyDocumentStore {
    virtual Document::UP read(DocumentIdT lid,
                              const DocumentTypeRepo &r) const override {
        if (lid == 0) {
            return Document::UP();
        }
        const DocumentType *doc_type = r.getDocumentType(doc_type_name);
        Document::UP doc(new Document(*doc_type, doc_id));
        ASSERT_TRUE(doc.get());
        doc->set(static_field, static_value);
        doc->set(dyn_field_i, static_value);
        doc->set(dyn_field_s, static_value_s);
        doc->set(dyn_field_nai, static_value);
        doc->set(dyn_field_nas, static_value_s);
        doc->set(zcurve_field, static_zcurve_value);
        doc->setValue(dyn_field_p, static_value_p);
        FieldValue::UP fv = PositionDataType::getInstance().createFieldValue();
        StructFieldValue &pos = static_cast<StructFieldValue &>(*fv);
        pos.set(PositionDataType::FIELD_X, 42);
        pos.set(PositionDataType::FIELD_Y, 21);
        doc->setValue(doc->getField(position_field), *fv);

        return doc;
    }
    
    virtual uint64_t
    initFlush(uint64_t syncToken) override
    {
        return syncToken;
    }
};

document::DocumenttypesConfig getRepoConfig() {
    const int32_t doc_type_id = 787121340;

    DocumenttypesConfigBuilderHelper builder;
    builder.document(doc_type_id, doc_type_name,
                     Struct(doc_type_name + ".header"),
                     Struct(doc_type_name + ".body")
                     .addField(static_field, DataType::T_INT)
                     .addField(dyn_field_i, DataType::T_INT)
                     .addField(dyn_field_d, DataType::T_DOUBLE)
                     .addField(dyn_field_s, DataType::T_STRING)
                     .addField(dyn_field_n, DataType::T_FLOAT)
                     .addField(dyn_field_nai, DataType::T_INT)
                     .addField(dyn_field_nas, DataType::T_STRING)
                     .addField(dyn_field_p, DataType::T_PREDICATE)
                     .addField(dyn_arr_field_i, Array(DataType::T_INT))
                     .addField(dyn_arr_field_d, Array(DataType::T_DOUBLE))
                     .addField(dyn_arr_field_s, Array(DataType::T_STRING))
                     .addField(dyn_arr_field_n, Array(DataType::T_FLOAT))
                     .addField(dyn_wset_field_i, Wset(DataType::T_INT))
                     .addField(dyn_wset_field_d, Wset(DataType::T_DOUBLE))
                     .addField(dyn_wset_field_s, Wset(DataType::T_STRING))
                     .addField(dyn_wset_field_n, Wset(DataType::T_FLOAT))
                     .addField(position_field,
                               PositionDataType::getInstance().getId())
                     .addField(zcurve_field, DataType::T_LONG));
     return builder.config();
}

BasicType
convertDataType(Schema::DataType t)
{
    switch (t) {
    case schema::INT32:
        return BasicType::INT32;
    case schema::INT64:
        return BasicType::INT64;
    case schema::FLOAT:
        return BasicType::FLOAT;
    case schema::DOUBLE:
        return BasicType::DOUBLE;
    case schema::STRING:
        return BasicType::STRING;
    case schema::BOOLEANTREE:
        return BasicType::PREDICATE;
    default:
        throw std::runtime_error(make_string("Data type %u not handled", (uint32_t)t));
    }
}

CollectionType
convertCollectionType(Schema::CollectionType ct)
{
    switch (ct) {
    case schema::SINGLE:
        return CollectionType::SINGLE;
    case schema::ARRAY:
        return CollectionType::ARRAY;
    case schema::WEIGHTEDSET:
        return CollectionType::WSET;
    default:
        throw std::runtime_error(make_string("Collection type %u not handled", (uint32_t)ct));
    }
}

search::attribute::Config
convertConfig(Schema::DataType t, Schema::CollectionType ct)
{
    return search::attribute::Config(convertDataType(t), convertCollectionType(ct));
}

struct Fixture {
    DocumentTypeRepo repo;
    DocumentMetaStoreContext meta_store;
    const GlobalId &gid;
    BucketId bucket_id;
    Timestamp timestamp;
    DocumentMetaStore::DocId lid;
    MyDocumentStore doc_store;
    search::AttributeManager attr_manager;
    Schema schema;
    DocTypeName _dtName;
    DocumentRetriever retriever;

    template <typename T>
    T *addAttribute(const char *name,
                      Schema::DataType t, Schema::CollectionType ct) {
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
    void addAttribute(const char *name, U val,
                      Schema::DataType t, Schema::CollectionType ct) {
        T *attr = addAttribute<T>(name, t, ct);
        if (ct == schema::SINGLE) {
            attr->update(lid, val);
        } else {
            attr->append(lid, val + 1, dyn_weight);
            attr->append(lid, val, dyn_weight);
        }
        attr->commit();
    }

    Fixture()
        : repo(getRepoConfig()),
          meta_store(std::make_shared<BucketDBOwner>()),
          gid(doc_id.getGlobalId()),
          bucket_id(gid.convertToBucketId()),
          timestamp(21),
          lid(),
          doc_store(),
          attr_manager(),
          schema(),
          _dtName(doc_type_name),
          retriever(_dtName,
                    repo, schema, meta_store, attr_manager, doc_store)
    {
        typedef DocumentMetaStore::Result Result;
        meta_store.constructFreeList();
        Result inspect = meta_store.get().inspect(gid);
        uint32_t docSize = 1;
        Result putRes(meta_store.get().put(gid, bucket_id, timestamp, docSize, inspect.getLid()));
        lid = putRes.getLid();
        ASSERT_TRUE(putRes.ok());
        schema::CollectionType ct = schema::SINGLE;
        addAttribute<IntegerAttribute>(
                dyn_field_i, dyn_value_i, schema::INT32, ct);
        addAttribute<FloatingPointAttribute>(
                dyn_field_d, dyn_value_d, schema::DOUBLE, ct);
        addAttribute<StringAttribute>(
                dyn_field_s, dyn_value_s, schema::STRING, ct);
        addAttribute<FloatingPointAttribute>(
                dyn_field_n, schema::FLOAT, ct);
        addAttribute<IntegerAttribute>(
                dyn_field_nai, schema::INT32, ct);
        addAttribute<StringAttribute>(
                dyn_field_nas, schema::STRING, ct);
        addAttribute<IntegerAttribute>(
                zcurve_field, dynamic_zcurve_value, schema::INT64, ct);
        PredicateAttribute *attr = addAttribute<PredicateAttribute>(
                dyn_field_p, schema::BOOLEANTREE, ct);
        attr->getIndex().indexEmptyDocument(lid);
        attr->commit();
        ct = schema::ARRAY;
        addAttribute<IntegerAttribute>(
                dyn_arr_field_i, dyn_value_i, schema::INT32, ct);
        addAttribute<FloatingPointAttribute>(
                dyn_arr_field_d, dyn_value_d, schema::DOUBLE, ct);
        addAttribute<StringAttribute>(
                dyn_arr_field_s, dyn_value_s, schema::STRING, ct);
        addAttribute<FloatingPointAttribute>(
                dyn_arr_field_n, schema::FLOAT, ct);
        ct = schema::WEIGHTEDSET;
        addAttribute<IntegerAttribute>(
                dyn_wset_field_i, dyn_value_i, schema::INT32, ct);
        addAttribute<FloatingPointAttribute>(
                dyn_wset_field_d, dyn_value_d, schema::DOUBLE, ct);
        addAttribute<StringAttribute>(
                dyn_wset_field_s, dyn_value_s, schema::STRING, ct);
        addAttribute<FloatingPointAttribute>(
                dyn_wset_field_n, schema::FLOAT, ct);
    }
};

TEST_F("require that document retriever can retrieve document meta data",
       Fixture) {
    DocumentMetaData meta_data = f.retriever.getDocumentMetaData(doc_id);
    EXPECT_EQUAL(f.lid, meta_data.lid);
    EXPECT_EQUAL(f.timestamp, meta_data.timestamp);
}

TEST_F("require that document retriever can retrieve bucket meta data",
       Fixture) {
    DocumentMetaData::Vector result;
    f.retriever.getBucketMetaData(Bucket(f.bucket_id, PartitionId(0)), result);
    ASSERT_EQUAL(1u, result.size());
    EXPECT_EQUAL(f.lid, result[0].lid);
    EXPECT_EQUAL(f.timestamp, result[0].timestamp);
    result.clear();
    f.retriever.getBucketMetaData(Bucket(BucketId(f.bucket_id.getId() + 1),
                                         PartitionId(0)), result);
    EXPECT_EQUAL(0u, result.size());
}

TEST_F("require that document retriever can retrieve document", Fixture) {
    DocumentMetaData meta_data = f.retriever.getDocumentMetaData(doc_id);
    Document::UP doc = f.retriever.getDocument(meta_data.lid);
    ASSERT_TRUE(doc.get());
    EXPECT_EQUAL(doc_id, doc->getId());
}

template <typename T>
bool checkFieldValue(FieldValue::UP field_value, typename T::value_type v) {
    ASSERT_TRUE(field_value.get());
    T *t_value = dynamic_cast<T *>(field_value.get());
    ASSERT_TRUE(t_value);
    return EXPECT_EQUAL(v, t_value->getValue());
}

template <typename T>
void checkArray(FieldValue::UP array, typename T::value_type v) {
    ASSERT_TRUE(array.get());
    ArrayFieldValue *array_val = dynamic_cast<ArrayFieldValue *>(array.get());
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
    ASSERT_TRUE(wset.get());
    WeightedSetFieldValue *wset_val =
        dynamic_cast<WeightedSetFieldValue *>(wset.get());
    ASSERT_TRUE(wset_val);
    ASSERT_EQUAL(2u, wset_val->size());
    EXPECT_EQUAL(dyn_weight, wset_val->get(v));
    EXPECT_EQUAL(dyn_weight, wset_val->get(v + 1));
}

TEST_F("require that attributes are patched into stored document", Fixture) {
    DocumentMetaData meta_data = f.retriever.getDocumentMetaData(doc_id);
    Document::UP doc = f.retriever.getDocument(meta_data.lid);
    ASSERT_TRUE(doc.get());

    FieldValue::UP value = doc->getValue(static_field);
    ASSERT_TRUE(value.get());
    IntFieldValue *int_value = dynamic_cast<IntFieldValue *>(value.get());
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

TEST_F("require that attributes are patched into stored document unless also index field", Fixture) {
    f.schema.addIndexField(Schema::IndexField(dyn_field_s, schema::STRING));
    DocumentMetaData meta_data = f.retriever.getDocumentMetaData(doc_id);
    Document::UP doc = f.retriever.getDocument(meta_data.lid);
    ASSERT_TRUE(doc.get());
    checkFieldValue<StringFieldValue>(doc->getValue(dyn_field_s), static_value_s);
}

TEST_F("require that position fields are regenerated from zcurves", Fixture) {
    DocumentMetaData meta_data = f.retriever.getDocumentMetaData(doc_id);
    Document::UP doc = f.retriever.getDocument(meta_data.lid);
    ASSERT_TRUE(doc.get());

    FieldValue::UP value = doc->getValue(position_field);
    ASSERT_TRUE(value.get());
    StructFieldValue *position = dynamic_cast<StructFieldValue *>(value.get());
    ASSERT_TRUE(position);
    FieldValue::UP x = position->getValue(PositionDataType::FIELD_X);
    FieldValue::UP y = position->getValue(PositionDataType::FIELD_Y);
    EXPECT_EQUAL(-123096000, static_cast<IntFieldValue&>(*x).getValue());
    EXPECT_EQUAL(49401000, static_cast<IntFieldValue&>(*y).getValue());

    checkFieldValue<LongFieldValue>(doc->getValue(zcurve_field),
                                    dynamic_zcurve_value);
}

TEST_F("require that non-existing lid returns null pointer", Fixture) {
    Document::UP doc = f.retriever.getDocument(0);
    ASSERT_FALSE(doc.get());
}

TEST_F("require that predicate attributes can be retrieved", Fixture) {
    DocumentMetaData meta_data = f.retriever.getDocumentMetaData(doc_id);
    Document::UP doc = f.retriever.getDocument(meta_data.lid);
    ASSERT_TRUE(doc.get());

    FieldValue::UP value = doc->getValue(dyn_field_p);
    ASSERT_TRUE(value.get());
    PredicateFieldValue *predicate_value =
        dynamic_cast<PredicateFieldValue *>(value.get());
    ASSERT_TRUE(predicate_value);
}


}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
