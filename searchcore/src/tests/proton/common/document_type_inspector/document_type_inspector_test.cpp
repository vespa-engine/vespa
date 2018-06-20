// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchcore/proton/common/document_type_inspector.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/document/base/field.h>
#include <vespa/document/datatype/datatypes.h>

using namespace document;
using namespace proton;

template <class Type>
void
addFields(Type &type, bool fieldF3IsString, bool hasFieldF4, bool hasFieldF5)
{
    type.addField(Field("f1", 1, *DataType::STRING, true));
    type.addField(Field("f2", 2, *DataType::STRING, true));
    type.addField(Field("f3", 3, fieldF3IsString ? *DataType::STRING : *DataType::INT, true));
    if (hasFieldF4) {
        type.addField(Field("f4", 4, *DataType::STRING, true));
    }
    if (hasFieldF5) {
        type.addField(Field("f5", 5, *DataType::STRING, true));
    }
}

struct DocumentTypeFixture
{
    DocumentType _documentType;
    StructDataType _structFieldType;
    ArrayDataType _structArrayFieldType;
    MapDataType _structMapFieldType;
    MapDataType _mapFieldType;

    DocumentTypeFixture(bool fieldF3IsString, bool hasFieldF4, bool hasFieldF5, bool hasStruct, bool mapKeyIsByte);
    ~DocumentTypeFixture();
};

DocumentTypeFixture::DocumentTypeFixture(bool fieldF3IsString, bool hasFieldF4, bool hasFieldF5, bool hasStruct, bool mapKeyIsByte)
    : _documentType("test"),
      _structFieldType("struct"),
      _structArrayFieldType(_structFieldType),
      _structMapFieldType(mapKeyIsByte ? *DataType::BYTE : *DataType::STRING, _structFieldType),
      _mapFieldType(mapKeyIsByte ? *DataType::BYTE : *DataType::STRING, *DataType::STRING)
{
    addFields(_documentType, fieldF3IsString, hasFieldF4, hasFieldF5);
    if (hasStruct) {
        addFields(_structFieldType, fieldF3IsString, hasFieldF4, hasFieldF5);
        _documentType.addField(Field("sarray", 11, _structArrayFieldType, true));
        _documentType.addField(Field("smap", 12, _structMapFieldType, true));
        _documentType.addField(Field("map", 13, _mapFieldType, true));
    }
}

DocumentTypeFixture::~DocumentTypeFixture() = default;

struct Fixture
{
    DocumentTypeFixture _oldDocType;
    DocumentTypeFixture _newDocType;
    DocumentTypeInspector _inspector;
    Fixture(bool hasStruct = true, bool mapKeyIsByte = false)
        : _oldDocType(true, true, false, hasStruct, mapKeyIsByte),
          _newDocType(false, false, true, true, false),
          _inspector(_oldDocType._documentType, _newDocType._documentType)
    {
    }
};

TEST_F("require that unchanged fields are known", Fixture)
{
    const IDocumentTypeInspector &inspector = f._inspector;
    EXPECT_TRUE(inspector.hasUnchangedField("f1"));
    EXPECT_TRUE(inspector.hasUnchangedField("f2"));
    EXPECT_TRUE(inspector.hasUnchangedField("sarray.f1"));
    EXPECT_TRUE(inspector.hasUnchangedField("sarray.f2"));
    EXPECT_TRUE(inspector.hasUnchangedField("smap.key"));
    EXPECT_TRUE(inspector.hasUnchangedField("smap.value.f1"));
    EXPECT_TRUE(inspector.hasUnchangedField("smap.value.f2"));
    EXPECT_TRUE(inspector.hasUnchangedField("map.key"));
    EXPECT_TRUE(inspector.hasUnchangedField("map.value"));
}

TEST_F("require that changed fields are detected", Fixture)
{
    const IDocumentTypeInspector &inspector = f._inspector;
    EXPECT_FALSE(inspector.hasUnchangedField("f3"));
    EXPECT_FALSE(inspector.hasUnchangedField("sarray.f3"));
    EXPECT_FALSE(inspector.hasUnchangedField("smap.value.f3"));
}

TEST_F("require that partially missing fields are detected", Fixture)
{
    const IDocumentTypeInspector &inspector = f._inspector;
    EXPECT_FALSE(inspector.hasUnchangedField("f4"));
    EXPECT_FALSE(inspector.hasUnchangedField("f5"));
    EXPECT_FALSE(inspector.hasUnchangedField("sarray.f4"));
    EXPECT_FALSE(inspector.hasUnchangedField("sarray.f5"));
    EXPECT_FALSE(inspector.hasUnchangedField("smap.value.f4"));
    EXPECT_FALSE(inspector.hasUnchangedField("smap.value.f5"));
}

TEST_F("require that non-existing fields are NOT known", Fixture)
{
    const IDocumentTypeInspector &inspector = f._inspector;
    EXPECT_FALSE(inspector.hasUnchangedField("not"));
    EXPECT_FALSE(inspector.hasUnchangedField("sarray.not"));
    EXPECT_FALSE(inspector.hasUnchangedField("smap.not"));
}

TEST_F("require that map key type change is detected", Fixture(true, true))
{
    const IDocumentTypeInspector &inspector = f._inspector;
    EXPECT_FALSE(inspector.hasUnchangedField("smap.key"));
    EXPECT_FALSE(inspector.hasUnchangedField("smap.value.f1"));
    EXPECT_FALSE(inspector.hasUnchangedField("smap.value.f2"));
    EXPECT_FALSE(inspector.hasUnchangedField("map.key"));
    EXPECT_FALSE(inspector.hasUnchangedField("map.value"));
}

TEST_F("require that struct addition is detected", Fixture(false, false))
{
    const IDocumentTypeInspector &inspector = f._inspector;
    EXPECT_FALSE(inspector.hasUnchangedField("sarray.f1"));
    EXPECT_FALSE(inspector.hasUnchangedField("sarray.f2"));
    EXPECT_FALSE(inspector.hasUnchangedField("smap.key"));
    EXPECT_FALSE(inspector.hasUnchangedField("smap.value.f1"));
    EXPECT_FALSE(inspector.hasUnchangedField("smap.value.f2"));
    EXPECT_FALSE(inspector.hasUnchangedField("map.key"));
    EXPECT_FALSE(inspector.hasUnchangedField("map.value"));
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
