// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/document/base/documentid.h>
#include <vespa/document/config/documenttypes_config_fwd.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/datatype/referencedatatype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/referencefieldvalue.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/repo/document_type_repo_factory.h>
#include <vespa/document/select/parser.h>

#include <vespa/log/log.h>
LOG_SETUP("document_select_test");

using document::Document;
using document::DocumentId;
using document::DocumentType;
using document::DocumentTypeRepo;
using document::DocumentTypeRepoFactory;
using document::Field;
using document::ReferenceDataType;
using document::ReferenceFieldValue;
using document::BucketIdFactory;
using document::select::Parser;
using document::select::Result;
using document::select::ResultList;

std::shared_ptr<DocumenttypesConfig> make_document_types() {
    using Struct = document::config_builder::Struct;
    document::config_builder::DocumenttypesConfigBuilderHelper builder;
    constexpr int parent_doctype_id = 42;
    constexpr int child_doctype_id = 43;
    constexpr int ref_type_id = 44;
    builder.document(parent_doctype_id, "parent",
                     Struct("parent.header"),
                     Struct("parent.body"));
    builder.document(child_doctype_id, "child",
                     Struct("child.header").
                     addField("ref", ref_type_id),
                     Struct("child.body")).
        referenceType(ref_type_id, parent_doctype_id);
    return std::make_shared<DocumenttypesConfig>(builder.config());
}

class DocumentSelectTest : public ::testing::Test
{
protected:
    std::shared_ptr<DocumenttypesConfig>    _document_types;
    std::shared_ptr<const DocumentTypeRepo> _repo;
    const DocumentType*                     _child_document_type;
    const Field&                            _child_ref_field;
    const ReferenceDataType&                _child_ref_field_type;
    BucketIdFactory                         _bucket_id_factory;
    std::unique_ptr<Parser>                 _parser;
public:
    DocumentSelectTest();
    ~DocumentSelectTest() override;
    void check_select(const Document &doc, const vespalib::string &expression, const Result &exp_result);
};

DocumentSelectTest::DocumentSelectTest()
    : ::testing::Test(),
      _document_types(make_document_types()),
      _repo(DocumentTypeRepoFactory::make(*_document_types)),
      _child_document_type(_repo->getDocumentType("child")),
      _child_ref_field(_child_document_type->getField("ref")),
      _child_ref_field_type(dynamic_cast<const ReferenceDataType&>(_child_ref_field.getDataType())),
      _bucket_id_factory(),
      _parser(std::make_unique<Parser>(*_repo, _bucket_id_factory))
{
}

DocumentSelectTest::~DocumentSelectTest() = default;

void
DocumentSelectTest::check_select(const Document& doc, const vespalib::string& expression, const Result &exp_result)
{
    auto node = _parser->parse(expression);
    EXPECT_EQ(node->contains(doc), exp_result);
}


TEST_F(DocumentSelectTest, check_existing_reference_field)
{
    auto document = std::make_unique<Document>(*_repo, *_child_document_type, DocumentId("id::child::0"));
    document->setFieldValue(_child_ref_field, std::make_unique<ReferenceFieldValue>(_child_ref_field_type, DocumentId("id::parent::1")));
    EXPECT_TRUE(document->hasValue(_child_ref_field));
    check_select(*document, "child.ref == null", Result::False);
    check_select(*document, "child.ref != null", Result::True);
    check_select(*document, "child.ref == \"id::parent::1\"", Result::True);
    check_select(*document, "child.ref != \"id::parent::1\"", Result::False);
    check_select(*document, "child.ref == \"id::parent::2\"", Result::False);
    check_select(*document, "child.ref != \"id::parent::2\"", Result::True);
    check_select(*document, "child.ref < \"id::parent::0\"", Result::False);
    check_select(*document, "child.ref < \"id::parent::2\"", Result::True);
    check_select(*document, "child.ref > \"id::parent::0\"", Result::True);
    check_select(*document, "child.ref > \"id::parent::2\"", Result::False);
}

TEST_F(DocumentSelectTest, check_missing_reference_field)
{
    auto document = std::make_unique<Document>(*_repo, *_child_document_type, DocumentId("id::child::0"));
    EXPECT_FALSE(document->hasValue(_child_ref_field));
    check_select(*document, "child.ref == null", Result::True);
    check_select(*document, "child.ref != null", Result::False);
    check_select(*document, "child.ref == \"id::parent::1\"", Result::False);
    check_select(*document, "child.ref != \"id::parent::1\"", Result::True);
    check_select(*document, "child.ref == \"id::parent::2\"", Result::False);
    check_select(*document, "child.ref != \"id::parent::2\"", Result::True);
    check_select(*document, "child.ref < \"id::parent::0\"", Result::Invalid);
    check_select(*document, "child.ref < \"id::parent::2\"", Result::Invalid);
    check_select(*document, "child.ref > \"id::parent::0\"", Result::Invalid);
    check_select(*document, "child.ref > \"id::parent::2\"", Result::Invalid);
}

GTEST_MAIN_RUN_ALL_TESTS()
