// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/config/documenttypes_config_fwd.h>
#include <vespa/vespalib/stllike/string.h>
#include <functional>
#include <memory>

namespace document {
class DataType;
class Document;
class DocumentType;
class DocumentTypeRepo;
}
namespace document::config::internal { class InternalDocumenttypesType; }
namespace document::config_builder { struct Struct; }

namespace search::index {

/*
 * Class used to make empty search documents.
 */
class EmptyDocBuilder {
    using DocumenttypesConfig = const document::config::internal::InternalDocumenttypesType;
    std::shared_ptr<const DocumenttypesConfig>        _document_types_config;
    std::shared_ptr<const document::DocumentTypeRepo> _repo;
    const document::DocumentType*                     _document_type;
public:
    using AddFieldsType = std::function<void(document::config_builder::Struct&)>;
    EmptyDocBuilder();
    explicit EmptyDocBuilder(AddFieldsType add_fields);
    ~EmptyDocBuilder();
    const document::DocumentTypeRepo& get_repo() const noexcept { return *_repo; }
    std::shared_ptr<const document::DocumentTypeRepo> get_repo_sp() const noexcept { return _repo; }
    const document::DocumentType& get_document_type() const noexcept { return *_document_type; }
    std::unique_ptr<document::Document> make_document(vespalib::string document_id);
    const document::DataType &get_data_type(const vespalib::string &name) const;
    const DocumenttypesConfig& get_documenttypes_config() const noexcept { return *_document_types_config; }
};

}
