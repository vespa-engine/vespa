// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchcore/proton/server/configvalidator.h>

using namespace proton;
using namespace search::index;
using vespa::config::search::AttributesConfig;
using vespa::config::search::AttributesConfigBuilder;

typedef Schema::AttributeField AField;
typedef Schema::IndexField     IField;
typedef Schema::SummaryField   SField;

using proton::configvalidator::ResultType;
using proton::configvalidator::Result;

const ResultType OK  = ResultType::OK;
const ResultType DTC = ResultType::DATA_TYPE_CHANGED;
const ResultType CTC = ResultType::COLLECTION_TYPE_CHANGED;
const ResultType IAA = ResultType::INDEX_ASPECT_ADDED;
const ResultType IAR = ResultType::INDEX_ASPECT_REMOVED;
const ResultType AAA = ResultType::ATTRIBUTE_ASPECT_ADDED;
const ResultType AAR = ResultType::ATTRIBUTE_ASPECT_REMOVED;
const ResultType AFAA = ResultType::ATTRIBUTE_FAST_ACCESS_ADDED;
const ResultType AFAR = ResultType::ATTRIBUTE_FAST_ACCESS_REMOVED;
const ResultType ATTC = ResultType::ATTRIBUTE_TENSOR_TYPE_CHANGED;

enum FType {
    INDEX,
    ATTRIBUTE,
    SUMMARY
};

namespace std {

std::ostream &operator<<(std::ostream &os, const ResultType &value)
{
    os << static_cast<int>(value);
    return os;
}

}

struct SchemaBuilder
{
    Schema _schema;
    SchemaBuilder() : _schema() {}
    SchemaBuilder &add(const vespalib::string &name, FType ftype,
                       schema::DataType dtype, schema::CollectionType ctype = schema::SINGLE) {
        switch (ftype) {
        case INDEX:
            _schema.addIndexField(IField(name, dtype, ctype));
            break;
        case ATTRIBUTE:
            _schema.addAttributeField(AField(name, dtype, ctype));
            break;
        case SUMMARY:
            _schema.addSummaryField(SField(name, dtype, ctype));
            break;
        }
        return *this;
    }
    const Schema &schema() const { return _schema; }
};

Schema
create(FType ftype, Schema::DataType dtype, Schema::CollectionType ctype)
{
    SchemaBuilder bld;
    return bld.add("f1", ftype, dtype, ctype).schema();
}

Schema
created(FType ftype, schema::DataType dtype)
{
    return create(ftype, dtype, schema::SINGLE);
}

Schema
createc(FType ftype, schema::CollectionType ctype)
{
    return create(ftype, schema::STRING, ctype);
}

ResultType
checkSchema(const Schema &newSchema,
            const Schema &oldSchema,
            const Schema &oldHistory)
{
    return ConfigValidator::validate(ConfigValidator::Config(newSchema, AttributesConfig()),
            ConfigValidator::Config(oldSchema, AttributesConfig()), oldHistory).type();
}

ResultType
checkAttribute(const AttributesConfig &newCfg,
               const AttributesConfig &oldCfg)
{
    return ConfigValidator::validate(ConfigValidator::Config(Schema(), newCfg),
            ConfigValidator::Config(Schema(), oldCfg), Schema()).type();
}

void
requireThatChangedDataTypeIsDiscovered(FType ftype)
{
    EXPECT_EQUAL(DTC,
                 checkSchema(created(ftype, schema::INT32),
                         created(ftype, schema::STRING),
                         Schema()));
    EXPECT_EQUAL(DTC,
                 checkSchema(created(ftype, schema::INT32),
                         Schema(),
                         created(ftype, schema::STRING)));
}

TEST("require that changed data type is discovered")
{
    requireThatChangedDataTypeIsDiscovered(INDEX);
    requireThatChangedDataTypeIsDiscovered(ATTRIBUTE);
    requireThatChangedDataTypeIsDiscovered(SUMMARY);
}

void
requireThatChangedCollectionTypeIsDiscovered(FType ftype)
{
    EXPECT_EQUAL(CTC,
                 checkSchema(createc(ftype, schema::ARRAY),
                         createc(ftype, schema::SINGLE),
                         Schema()));
    EXPECT_EQUAL(CTC,
                 checkSchema(createc(ftype, schema::ARRAY),
                         Schema(),
                         createc(ftype, schema::SINGLE)));
}

TEST("require that changed collection type is discovered")
{
    requireThatChangedCollectionTypeIsDiscovered(INDEX);
    requireThatChangedCollectionTypeIsDiscovered(ATTRIBUTE);
    requireThatChangedCollectionTypeIsDiscovered(SUMMARY);
}

TEST("require that changed index aspect is discovered")
{
    Schema s1 = created(SUMMARY, schema::STRING);
    s1.addIndexField(IField("f1", schema::STRING));
    Schema s2 = created(SUMMARY, schema::STRING);
    Schema s2h = created(INDEX, schema::STRING);

    Schema s3 = created(ATTRIBUTE, schema::STRING);
    s3.addIndexField(IField("f1", schema::STRING));
    Schema s4 = created(ATTRIBUTE, schema::STRING);
    Schema s4h = created(INDEX, schema::STRING);
    { // remove as index field
        EXPECT_EQUAL(IAR, checkSchema(s2, s1, Schema()));
        EXPECT_EQUAL(IAR, checkSchema(s2, Schema(), s1));
        EXPECT_EQUAL(IAR, checkSchema(s4, s3, Schema()));
        EXPECT_EQUAL(IAR, checkSchema(s4, Schema(), s3));
    }
    {
        // undo field removal
        EXPECT_EQUAL(OK, checkSchema(s1, Schema(), s1));
        EXPECT_EQUAL(OK, checkSchema(s3, Schema(), s3));
    }
    { // add as index field
        EXPECT_EQUAL(IAA, checkSchema(s1, s2, Schema()));
        EXPECT_EQUAL(IAA, checkSchema(s1, s2, s2h));
        EXPECT_EQUAL(IAA, checkSchema(s1, Schema(), s2));
        EXPECT_EQUAL(IAA, checkSchema(s3, s4, Schema()));
        EXPECT_EQUAL(IAA, checkSchema(s3, s4, s4h));
        EXPECT_EQUAL(IAA, checkSchema(s3, Schema(), s4));
    }
}

TEST("require that changed attribute aspect is discovered")
{
    Schema s1 = created(SUMMARY, schema::STRING);
    s1.addAttributeField(AField("f1", schema::STRING));
    Schema s2 = created(SUMMARY, schema::STRING);
    Schema s2h = created(ATTRIBUTE, schema::STRING);

    Schema s3 = created(INDEX, schema::STRING);
    s3.addAttributeField(AField("f1", schema::STRING));
    Schema s4 = created(INDEX, schema::STRING);
    Schema s4h = created(ATTRIBUTE, schema::STRING);

    Schema s5 = created(INDEX, schema::STRING);
    s5.addSummaryField(SField("f1", schema::STRING));
    s5.addAttributeField(AField("f1", schema::STRING));
    Schema s6 = created(INDEX, schema::STRING);
    s6.addSummaryField(SField("f1", schema::STRING));
    { // remove as attribute field
        EXPECT_EQUAL(AAR, checkSchema(s2, s1, Schema()));
        EXPECT_EQUAL(AAR, checkSchema(s2, Schema(), s1));
        // remove as attribute is allowed when still existing as index.
        EXPECT_EQUAL(OK, checkSchema(s4, s3, Schema()));
        EXPECT_EQUAL(OK, checkSchema(s6, s5, Schema()));
        EXPECT_EQUAL(IAA, checkSchema(s4, Schema(), s3));
    }
    {
        // undo field removal
        EXPECT_EQUAL(OK, checkSchema(s1, Schema(), s1));
        EXPECT_EQUAL(OK, checkSchema(s3, Schema(), s3));
    }
    { // add as attribute field
        EXPECT_EQUAL(AAA, checkSchema(s1, s2, Schema()));
        EXPECT_EQUAL(AAA, checkSchema(s1, s2, s2h));
        EXPECT_EQUAL(AAA, checkSchema(s1, Schema(), s2));
        EXPECT_EQUAL(AAA, checkSchema(s3, s4, Schema()));
        EXPECT_EQUAL(AAA, checkSchema(s3, s4, s4h));
        EXPECT_EQUAL(AAA, checkSchema(s3, Schema(), s4));
    }
}

TEST("require that changed summary aspect is allowed")
{
    Schema s1 = created(INDEX, schema::STRING);
    s1.addSummaryField(SField("f1", schema::STRING));
    Schema s2 = created(INDEX, schema::STRING);
    Schema s2h = created(SUMMARY, schema::STRING);

    Schema s3 = created(ATTRIBUTE, schema::STRING);
    s3.addSummaryField(SField("f1", schema::STRING));
    Schema s4 = created(ATTRIBUTE, schema::STRING);
    Schema s4h = created(SUMMARY, schema::STRING);
    { // remove as summary field
        EXPECT_EQUAL(OK, checkSchema(s2, s1, Schema()));
        EXPECT_EQUAL(IAA, checkSchema(s2, Schema(), s1));
        EXPECT_EQUAL(OK, checkSchema(s4, s3, Schema()));
        EXPECT_EQUAL(AAA, checkSchema(s4, Schema(), s3));
    }
    { // add as summary field
        EXPECT_EQUAL(OK, checkSchema(s1, s2, Schema()));
        EXPECT_EQUAL(OK, checkSchema(s1, s2, s2h));
        EXPECT_EQUAL(OK, checkSchema(s1, Schema(), s2));
        EXPECT_EQUAL(OK, checkSchema(s3, s4, Schema()));
        EXPECT_EQUAL(OK, checkSchema(s3, s4, s4h));
        EXPECT_EQUAL(OK, checkSchema(s3, Schema(), s4));
    }
}

TEST("require that fields can be added and removed")
{
    Schema e;
    Schema s1 = created(INDEX, schema::STRING);
    Schema s2 = created(ATTRIBUTE, schema::STRING);
    Schema s3 = created(SUMMARY, schema::STRING);
    Schema s4 = created(SUMMARY, schema::STRING);
    s4.addIndexField(IField("f1", schema::STRING));
    Schema s5 = created(SUMMARY, schema::STRING);
    s5.addAttributeField(AField("f1", schema::STRING));
    Schema s6 = created(SUMMARY, schema::STRING);
    s6.addIndexField(IField("f1", schema::STRING));
    s6.addAttributeField(AField("f1", schema::STRING));
    { // addition of field
        EXPECT_EQUAL(OK, checkSchema(s1, e, e));
        EXPECT_EQUAL(OK, checkSchema(s2, e, e));
        EXPECT_EQUAL(OK, checkSchema(s3, e, e));
        EXPECT_EQUAL(OK, checkSchema(s4, e, e));
        EXPECT_EQUAL(OK, checkSchema(s5, e, e));
        EXPECT_EQUAL(OK, checkSchema(s6, e, e));
    }
    { // removal of field
        EXPECT_EQUAL(OK, checkSchema(e, s1, e));
        EXPECT_EQUAL(OK, checkSchema(e, e, s1));
        EXPECT_EQUAL(OK, checkSchema(e, s2, e));
        EXPECT_EQUAL(OK, checkSchema(e, e, s2));
        EXPECT_EQUAL(OK, checkSchema(e, s3, e));
        EXPECT_EQUAL(OK, checkSchema(e, e, s3));
        EXPECT_EQUAL(OK, checkSchema(e, s4, e));
        EXPECT_EQUAL(OK, checkSchema(e, e, s4));
        EXPECT_EQUAL(OK, checkSchema(e, s5, e));
        EXPECT_EQUAL(OK, checkSchema(e, e, s5));
        EXPECT_EQUAL(OK, checkSchema(e, s6, e));
        EXPECT_EQUAL(OK, checkSchema(e, e, s6));
    }
}

TEST("require that data type changed precedes collection type changed")
{
    Schema olds = SchemaBuilder().add("f1", FType::SUMMARY, schema::STRING).
            add("f2", FType::INDEX, schema::STRING).schema();
    Schema news = SchemaBuilder().add("f1", FType::SUMMARY, schema::INT32).
            add("f2", FType::INDEX, schema::STRING, schema::ARRAY).schema();
    EXPECT_EQUAL(DTC, checkSchema(news, olds, Schema()));
}

TEST("require that collection type change precedes index aspect added")
{
    Schema olds = SchemaBuilder().add("f1", FType::SUMMARY, schema::STRING).
            add("f2", FType::SUMMARY, schema::STRING).schema();
    Schema news = SchemaBuilder().add("f1", FType::SUMMARY, schema::STRING, schema::ARRAY).
            add("f2", FType::SUMMARY, schema::STRING).
            add("f2", FType::INDEX, schema::STRING).schema();
    EXPECT_EQUAL(CTC, checkSchema(news, olds, Schema()));
}

TEST("require that index aspect added precedes index aspect removed")
{
    Schema olds = SchemaBuilder().add("f1", FType::SUMMARY, schema::STRING).
            add("f2", FType::SUMMARY, schema::STRING).
            add("f2", FType::INDEX, schema::STRING).schema();
    Schema news = SchemaBuilder().add("f1", FType::SUMMARY, schema::STRING).
            add("f1", FType::INDEX, schema::STRING).
            add("f2", FType::SUMMARY, schema::STRING).schema();
    EXPECT_EQUAL(IAA, checkSchema(news, olds, Schema()));
}

TEST("require that index aspect removed precedes attribute aspect removed")
{
    Schema olds = SchemaBuilder().add("f1", FType::SUMMARY, schema::STRING).
            add("f1", FType::INDEX, schema::STRING).
            add("f2", FType::SUMMARY, schema::STRING).
            add("f2", FType::ATTRIBUTE, schema::STRING).schema();
    Schema news = SchemaBuilder().add("f1", FType::SUMMARY, schema::STRING).
            add("f2", FType::SUMMARY, schema::STRING).schema();
    EXPECT_EQUAL(IAR, checkSchema(news, olds, Schema()));
}

TEST("require that attribute aspect removed precedes attribute aspect added")
{
    Schema olds = SchemaBuilder().add("f1", FType::SUMMARY, schema::STRING).
            add("f1", FType::ATTRIBUTE, schema::STRING).
            add("f2", FType::SUMMARY, schema::STRING).schema();
    Schema news = SchemaBuilder().add("f1", FType::SUMMARY, schema::STRING).
            add("f2", FType::SUMMARY, schema::STRING).
            add("f2", FType::ATTRIBUTE, schema::STRING).schema();
    EXPECT_EQUAL(AAR, checkSchema(news, olds, Schema()));
}

AttributesConfigBuilder::Attribute
createAttribute(const vespalib::string &name, bool fastAccess)
{
    AttributesConfigBuilder::Attribute attr;
    attr.name = name;
    attr.fastaccess = fastAccess;
    return attr;
}

AttributesConfigBuilder
createAttributesConfig(const AttributesConfigBuilder::Attribute &attribute)
{
    AttributesConfigBuilder result;
    result.attribute.push_back(attribute);
    return result;
}

TEST("require that adding attribute fast-access is discovered")
{
    EXPECT_EQUAL(AFAA, checkAttribute(createAttributesConfig(createAttribute("a1", true)),
                                      createAttributesConfig(createAttribute("a1", false))));
}

TEST("require that removing attribute fast-access is discovered")
{
    EXPECT_EQUAL(AFAR, checkAttribute(createAttributesConfig(createAttribute("a1", false)),
                                      createAttributesConfig(createAttribute("a1", true))));
}

AttributesConfigBuilder::Attribute
createTensorAttribute(const vespalib::string &name, const vespalib::string &tensorType)
{
    AttributesConfigBuilder::Attribute attr;
    attr.name = name;
    attr.tensortype = tensorType;
    return attr;
}

TEST("require that changing attribute tensor type is discovered")
{
    EXPECT_EQUAL(ATTC, checkAttribute(createAttributesConfig(createTensorAttribute("a1", "tensor(x[10])")),
                                      createAttributesConfig(createTensorAttribute("a1", "tensor(x[11])"))));
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
