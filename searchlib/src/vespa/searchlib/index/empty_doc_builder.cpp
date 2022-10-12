// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "empty_doc_builder.h"
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/repo/document_type_repo_factory.h>
#include <vespa/document/repo/configbuilder.h>
#include <cassert>

using document::DataType;
using document::Document;
using document::DocumentId;
using document::DocumentTypeRepo;
using document::DocumentTypeRepoFactory;

namespace search::index {

namespace {

DocumenttypesConfig
get_document_types_config(EmptyDocBuilder::AddFieldsType add_fields)
{
    using namespace document::config_builder;
    DocumenttypesConfigBuilderHelper builder;
    Struct header("searchdocument.header");
    add_fields(header);
    builder.document(42, "searchdocument",
                     header,
                     Struct("searchdocument.body"));
    return builder.config();
}

}

EmptyDocBuilder::EmptyDocBuilder()
    : EmptyDocBuilder([](auto&) noexcept {})
{
}

EmptyDocBuilder::EmptyDocBuilder(AddFieldsType add_fields)
    : _document_types_config(std::make_shared<const DocumenttypesConfig>(get_document_types_config(add_fields))),
      _repo(DocumentTypeRepoFactory::make(*_document_types_config)),
      _document_type(_repo->getDocumentType("searchdocument"))
{
}

EmptyDocBuilder::~EmptyDocBuilder() = default;


std::unique_ptr<Document>
EmptyDocBuilder::make_document(vespalib::string document_id)
{
    auto doc = std::make_unique<Document>(get_document_type(), DocumentId(document_id));
    doc->setRepo(get_repo());
    return doc;
}

const DataType&
EmptyDocBuilder::get_data_type(const vespalib::string &name) const
{
    const DataType *type = _repo->getDataType(*_document_type, name);
    assert(type);
    return *type;
}

}
