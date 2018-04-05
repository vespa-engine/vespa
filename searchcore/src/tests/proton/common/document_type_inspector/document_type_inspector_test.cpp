// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchcore/proton/common/document_type_inspector.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>

using namespace document;
using namespace proton;
using document::config_builder::DocumenttypesConfigBuilderHelper;
using document::config_builder::Struct;

const int32_t doc_type_id = 787121340;
const vespalib::string type_name = "test";
const vespalib::string header_name = type_name + ".header";
const vespalib::string body_name = type_name + ".body";

std::unique_ptr<const DocumentTypeRepo>
makeOldDocTypeRepo()
{
    DocumenttypesConfigBuilderHelper builder;
    builder.document(doc_type_id, type_name,
                     Struct(header_name), Struct(body_name).
                     addField("f1", DataType::T_STRING).
                     addField("f2", DataType::T_STRING).
                     addField("f3", DataType::T_STRING).
                     addField("f4", DataType::T_STRING));
    return std::unique_ptr<const DocumentTypeRepo>(new DocumentTypeRepo(builder.config()));
}

std::unique_ptr<const DocumentTypeRepo>
makeNewDocTypeRepo()
{
    DocumenttypesConfigBuilderHelper builder;
    builder.document(doc_type_id, type_name,
                     Struct(header_name), Struct(body_name).
                     addField("f1", DataType::T_STRING).
                     addField("f2", DataType::T_STRING).
                     addField("f3", DataType::T_INT).
                     addField("f5", DataType::T_STRING));
    return std::unique_ptr<const DocumentTypeRepo>(new DocumentTypeRepo(builder.config()));
}

struct Fixture
{
    std::unique_ptr<const DocumentTypeRepo> _oldRepo;
    std::unique_ptr<const DocumentTypeRepo> _newRepo;
    DocumentTypeInspector _inspector;
    Fixture()
        : _oldRepo(makeOldDocTypeRepo()),
          _newRepo(makeNewDocTypeRepo()),
          _inspector(*_oldRepo->getDocumentType("test"), *_newRepo->getDocumentType("test"))
    {
    }
};

TEST_F("require that unchanged fields are known", Fixture)
{
    EXPECT_TRUE(f._inspector.hasUnchangedField("f1"));
    EXPECT_TRUE(f._inspector.hasUnchangedField("f2"));
}

TEST_F("require that changed fields are detected", Fixture)
{
    EXPECT_FALSE(f._inspector.hasUnchangedField("f3"));
}

TEST_F("require that partially missing fields are detected", Fixture)
{
    EXPECT_FALSE(f._inspector.hasUnchangedField("f4"));
    EXPECT_FALSE(f._inspector.hasUnchangedField("f5"));
}

TEST_F("require that non-existing fields are NOT known", Fixture)
{
    EXPECT_FALSE(f._inspector.hasUnchangedField("not"));
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
