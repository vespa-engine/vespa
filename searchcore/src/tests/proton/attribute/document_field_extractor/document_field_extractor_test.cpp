// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/documentid.h>
#include <vespa/document/base/exceptions.h>
#include <vespa/document/base/field.h>
#include <vespa/document/base/fieldpath.h>
#include <vespa/document/datatype/datatypes.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/mapfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/structfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/searchcommon/common/undefinedvalues.h>
#include <vespa/searchcore/proton/attribute/document_field_extractor.h>
#include <vespa/vespalib/testkit/testapp.h>

using document::Field;
using document::DataType;
using document::DocumentType;
using document::StructDataType;
using document::ArrayDataType;
using document::WeightedSetDataType;
using document::MapDataType;
using document::StructFieldValue;
using document::ArrayFieldValue;
using document::WeightedSetFieldValue;
using document::IntFieldValue;
using document::StringFieldValue;
using document::MapFieldValue;
using document::Document;
using document::DocumentId;
using document::FieldPath;
using document::FieldValue;
using document::FieldNotFoundException;
using proton::DocumentFieldExtractor;

namespace
{

const ArrayDataType arrayTypeInt(*DataType::INT);
const ArrayDataType arrayTypeString(*DataType::STRING);
const WeightedSetDataType weightedSetTypeInt(*DataType::INT, false, false);
const WeightedSetDataType weightedSetTypeString(*DataType::STRING, false, false);
const int32_t noInt(search::attribute::getUndefined<int32_t>());
const vespalib::string noString("");

std::unique_ptr<FieldValue>
makeIntArray(const std::vector<int32_t> &array)
{
    auto result = std::make_unique<ArrayFieldValue>(arrayTypeInt);
    for (const auto &elem : array) {
        result->append(std::make_unique<IntFieldValue>(elem));
    }
    return result;
}

std::unique_ptr<FieldValue>
makeStringArray(const std::vector<vespalib::string> &array)
{
    auto result = std::make_unique<ArrayFieldValue>(arrayTypeString);
    for (const auto &elem : array) {
        result->append(std::make_unique<StringFieldValue>(elem));
    }
    return result;
}

std::unique_ptr<FieldValue>
makeIntWeightedSet(const std::vector<std::pair<int32_t, int32_t>> &array)
{
    auto result = std::make_unique<WeightedSetFieldValue>(weightedSetTypeInt);
    for (const auto &elem : array) {
        result->add(IntFieldValue(elem.first), elem.second);
    }
    return result;
}

std::unique_ptr<FieldValue>
makeStringWeightedSet(const std::vector<std::pair<vespalib::string, int32_t>> &array)
{
    auto result = std::make_unique<WeightedSetFieldValue>(weightedSetTypeString);
    for (const auto &elem : array) {
        result->add(StringFieldValue(elem.first), elem.second);
    }
    return result;
}

template <typename F1, typename F2>
void
checkFieldPathChange(F1 f1, F2 f2, const vespalib::string &path, bool same)
{
    FieldPath fieldPath1 = f1.makeFieldPath(path);
    FieldPath fieldPath2 = f2.makeFieldPath(path);
    EXPECT_TRUE(!fieldPath1.empty());
    EXPECT_TRUE(!fieldPath2.empty());
    EXPECT_TRUE(DocumentFieldExtractor::isSupported(fieldPath1));
    EXPECT_TRUE(DocumentFieldExtractor::isSupported(fieldPath2));
    EXPECT_EQUAL(same, DocumentFieldExtractor::isCompatible(fieldPath1, fieldPath2));
}

}

struct FixtureBase
{
    DocumentType   type;
    const Field    weightField;
    const Field    nameField;
    std::unique_ptr<Document> doc;
    std::unique_ptr<DocumentFieldExtractor> extractor;

    FixtureBase(bool byteWeight)
        : type("test"),
          weightField("weight", 1, byteWeight ? *DataType::BYTE : *DataType::INT, true),
          nameField("name", 2, *DataType::STRING, true),
          doc(),
          extractor()
    {
    }

    ~FixtureBase();

    Document *
    makeDoc()
    {
        doc = std::make_unique<Document>(type, DocumentId("id::test::1"));
        extractor = std::make_unique<DocumentFieldExtractor>(*doc);
        return doc.get();
    }

    FieldPath
    makeFieldPath(const vespalib::string &path)
    {
        FieldPath fieldPath;
        try {
            type.buildFieldPath(fieldPath, path);
        } catch (FieldNotFoundException &) {
            fieldPath = FieldPath();
        }
        if (!DocumentFieldExtractor::isSupported(fieldPath)) {
            fieldPath = FieldPath();
        }
        return fieldPath;
    }

    void
    assertExtracted(const vespalib::string &path,
                    std::unique_ptr<FieldValue> expected) {
        FieldPath fieldPath(makeFieldPath(path));
        std::unique_ptr<FieldValue> fv = extractor->getFieldValue(fieldPath);
        if (expected) {
            ASSERT_TRUE(fv);
            EXPECT_EQUAL(*expected, *fv);
        } else {
            EXPECT_TRUE(!fv);
        }
    }
};

FixtureBase::~FixtureBase() = default;

struct SimpleFixture : public FixtureBase
{
    SimpleFixture(bool byteWeight = false)
        : FixtureBase(byteWeight)
    {
        type.addField(weightField);
        type.addField(nameField);
    }
};

TEST_F("require that simple fields give simple values", SimpleFixture)
{
    auto doc = f.makeDoc();
    doc->setValue(f.weightField, IntFieldValue(200));
    doc->setValue(f.nameField, StringFieldValue("name200b"));
    TEST_DO(f.assertExtracted("weight", std::make_unique<IntFieldValue>(200)));
    TEST_DO(f.assertExtracted("name", std::make_unique<StringFieldValue>("name200b")));
}

struct ArrayFixture : public FixtureBase
{
    const ArrayDataType weightArrayFieldType;
    const Field weightArrayField;
    const ArrayDataType valueArrayFieldType;
    const Field valueArrayField;

    ArrayFixture(bool byteWeight = false)
        : FixtureBase(byteWeight),
          weightArrayFieldType(weightField.getDataType()),
          weightArrayField("weight", weightArrayFieldType, true),
          valueArrayFieldType(nameField.getDataType()),
          valueArrayField("val", valueArrayFieldType, true)
    {
        type.addField(weightArrayField);
        type.addField(valueArrayField);
    }

    ~ArrayFixture();
};

ArrayFixture::~ArrayFixture() = default;

TEST_F("require that array fields give array values", ArrayFixture)
{
    auto doc = f.makeDoc();
    doc->setValue(f.weightArrayField, *makeIntArray({ 300, 301 }));
    doc->setValue(f.valueArrayField, *makeStringArray({"v500", "v502"}));
    TEST_DO(f.assertExtracted("weight", makeIntArray({ 300, 301})));
    TEST_DO(f.assertExtracted("val", makeStringArray({"v500", "v502"})));
}

struct WeightedSetFixture : public FixtureBase
{
    const WeightedSetDataType weightWeightedSetFieldType;
    const Field weightWeightedSetField;
    const WeightedSetDataType valueWeightedSetFieldType;
    const Field valueWeightedSetField;

    WeightedSetFixture(bool byteWeight = false)
        : FixtureBase(byteWeight),
          weightWeightedSetFieldType(weightField.getDataType(), false, false),
          weightWeightedSetField("weight", weightWeightedSetFieldType, true),
          valueWeightedSetFieldType(*DataType::STRING, false, false),
          valueWeightedSetField("val", valueWeightedSetFieldType, true)
    {
        type.addField(weightWeightedSetField);
        type.addField(valueWeightedSetField);
    }

    ~WeightedSetFixture();
};

WeightedSetFixture::~WeightedSetFixture() = default;

TEST_F("require that weighted set fields give weighted set values", WeightedSetFixture)
{
    auto doc = f.makeDoc();
    doc->setValue(f.weightWeightedSetField, *makeIntWeightedSet({{400, 10}, { 401, 13}}));
    doc->setValue(f.valueWeightedSetField, *makeStringWeightedSet({{"600", 17}, {"604", 19}}));
    TEST_DO(f.assertExtracted("weight", makeIntWeightedSet({{ 400, 10}, {401, 13}})));
    TEST_DO(f.assertExtracted("val", makeStringWeightedSet({{"600", 17}, {"604", 19}})));
}

struct StructFixtureBase : public FixtureBase
{
    StructDataType structFieldType;

    StructFixtureBase(bool byteWeight)
        : FixtureBase(byteWeight),
          structFieldType("struct")
    {
        structFieldType.addField(weightField);
        structFieldType.addField(nameField);
    }

    std::unique_ptr<StructFieldValue>
    makeStruct()
    {
        return std::make_unique<StructFieldValue>(structFieldType);
    }

    std::unique_ptr<StructFieldValue>
    makeStruct(int weight, const vespalib::string &value)
    {
        auto ret = makeStruct();
        ret->setValue(weightField, IntFieldValue(weight));
        ret->setValue(nameField, StringFieldValue(value));
        return ret;
    }

    std::unique_ptr<StructFieldValue>
    makeStruct(int weight)
    {
        auto ret = makeStruct();
        ret->setValue(weightField, IntFieldValue(weight));
        return ret;
    }

    std::unique_ptr<StructFieldValue>
    makeStruct(const vespalib::string &value)
    {
        auto ret = makeStruct();
        ret->setValue(nameField, StringFieldValue(value));
        return ret;
    }
};

struct StructArrayFixture : public StructFixtureBase
{
    const ArrayDataType structArrayFieldType;
    const Field structArrayField;

    StructArrayFixture(bool byteWeight = false)
        : StructFixtureBase(byteWeight),
          structArrayFieldType(structFieldType),
          structArrayField("s", 11, structArrayFieldType, true)
    {
        type.addField(structArrayField);
    }

    ~StructArrayFixture();
};

StructArrayFixture::~StructArrayFixture() = default;

TEST_F("require that struct array field gives array values", StructArrayFixture)
{
    auto doc = f.makeDoc();
    ArrayFieldValue structArrayFieldValue(f.structArrayFieldType);
    structArrayFieldValue.add(*f.makeStruct(1, "name1"));
    structArrayFieldValue.add(*f.makeStruct(2));
    structArrayFieldValue.add(*f.makeStruct("name3"));
    doc->setValue(f.structArrayField, structArrayFieldValue);
    TEST_DO(f.assertExtracted("s.weight", makeIntArray({ 1, 2, noInt })));
    TEST_DO(f.assertExtracted("s.name", makeStringArray({ "name1", noString, "name3" })));
}

struct StructMapFixture : public StructFixtureBase
{
    const MapDataType structMapFieldType;
    const Field structMapField;

    StructMapFixture(bool byteWeight = false, bool byteKey = false)
        : StructFixtureBase(byteWeight),
          structMapFieldType(byteKey ? *DataType::BYTE : *DataType::STRING, structFieldType),
          structMapField("s", 12, structMapFieldType, true)
    {
        type.addField(structMapField);
    }

    ~StructMapFixture();
};

StructMapFixture::~StructMapFixture() = default;

TEST_F("require that struct map field gives array values", StructMapFixture)
{
    auto doc = f.makeDoc();
    MapFieldValue structMapFieldValue(f.structMapFieldType);
    structMapFieldValue.put(StringFieldValue("m0"), *f.makeStruct(10, "name10"));
    structMapFieldValue.put(StringFieldValue("m1"), *f.makeStruct(11));
    structMapFieldValue.put(StringFieldValue("m2"), *f.makeStruct("name12"));
    structMapFieldValue.put(StringFieldValue("m3"), *f.makeStruct());
    doc->setValue(f.structMapField, structMapFieldValue);
    TEST_DO(f.assertExtracted("s.key", makeStringArray({ "m0", "m1", "m2", "m3" })));
    TEST_DO(f.assertExtracted("s.value.weight", makeIntArray({ 10, 11, noInt, noInt })));
    TEST_DO(f.assertExtracted("s.value.name", makeStringArray({ "name10", noString, "name12", noString })));
}

TEST_F("require that unknown field gives null value", FixtureBase(false))
{
    f.makeDoc();
    TEST_DO(f.assertExtracted("unknown", std::unique_ptr<FieldValue>()));
}

TEST("require that type changes are detected")
{
    TEST_DO(checkFieldPathChange(SimpleFixture(false), SimpleFixture(false), "weight", true));
    TEST_DO(checkFieldPathChange(SimpleFixture(false), SimpleFixture(true), "weight", false));
    TEST_DO(checkFieldPathChange(ArrayFixture(false), ArrayFixture(false), "weight", true));
    TEST_DO(checkFieldPathChange(ArrayFixture(false), ArrayFixture(true), "weight", false));
    TEST_DO(checkFieldPathChange(SimpleFixture(false), ArrayFixture(false), "weight", false));
    TEST_DO(checkFieldPathChange(WeightedSetFixture(false), WeightedSetFixture(false), "weight", true));
    TEST_DO(checkFieldPathChange(WeightedSetFixture(false), WeightedSetFixture(true), "weight", false));
    TEST_DO(checkFieldPathChange(SimpleFixture(false), WeightedSetFixture(false), "weight", false));
    TEST_DO(checkFieldPathChange(ArrayFixture(false), WeightedSetFixture(false), "weight", false));
    TEST_DO(checkFieldPathChange(StructArrayFixture(false), StructArrayFixture(false), "s.weight", true));
    TEST_DO(checkFieldPathChange(StructArrayFixture(false), StructArrayFixture(true), "s.weight", false));
    TEST_DO(checkFieldPathChange(StructMapFixture(false, false), StructMapFixture(false, false), "s.value.weight", true));
    TEST_DO(checkFieldPathChange(StructMapFixture(false, false), StructMapFixture(true, false), "s.value.weight", false));
    TEST_DO(checkFieldPathChange(StructMapFixture(false, false), StructMapFixture(false, true), "s.value.weight", false));
    TEST_DO(checkFieldPathChange(StructMapFixture(false, false), StructMapFixture(false, false), "s.key", true));
    TEST_DO(checkFieldPathChange(StructMapFixture(false, false), StructMapFixture(false, true), "s.key", false));
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
