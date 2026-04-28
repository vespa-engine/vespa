// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/select/resultset.h>
#include <memory>
#include <string>
#include <vector>

namespace document {
    class IDocumentTypeRepo;
    class Document;
    namespace select { class Node; }
}
namespace search {
    class AttributeVector;
    class IAttributeManager;
}

namespace search::attribute { class ReadableAttributeVector; }

namespace proton {

class SelectContext;
class SelectPruner;

/**
 * Cached selection expression, to avoid pruning expression for each
 * new bucket.
 */
class CachedSelect
{
public:
    using SP = std::shared_ptr<CachedSelect>;

    class Session {
    private:
        // See below for semantics of the three versions of document select expressions.
        std::unique_ptr<document::select::Node> _docSelect;
        std::unique_ptr<document::select::Node> _preDocOnlySelect;
        std::unique_ptr<document::select::Node> _preDocSelect;

    public:
        Session(std::unique_ptr<document::select::Node> docSelect,
                std::unique_ptr<document::select::Node> preDocOnlySelect,
                std::unique_ptr<document::select::Node> preDocSelect);
        /*
         * Check if document select expression might contain the document without getting the document from
         * the backing store.
         *
         * Precondition: select context must have a valid _lid. If the document retriever told that it can populate
         * document metadata docid then _docId must also be valid in the select context.
         */
        [[nodiscard]] bool contains_pre_doc(const SelectContext &context) const;
        /*
         * Check if document select expression contains the document after getting the document from the
         * backing store.
         *
         * Precondition: context must have non-nullptr _doc
         */
        [[nodiscard]] bool contains_doc(const SelectContext &context) const;
        [[nodiscard]] const document::select::Node &selectNode() const;
    };

    using AttributeVectors = std::vector<std::shared_ptr<search::attribute::ReadableAttributeVector>>;

private:
    // Single value attributes referenced from selection expression
    AttributeVectors _attributes;

    // Pruned selection expression, specific for a document type
    std::unique_ptr<document::select::Node> _docSelect;
    uint32_t _fieldNodes;
    uint32_t _attrFieldNodes;
    uint32_t _document_id_nodes;
    uint32_t _svAttrFieldNodes;
    bool _always_false;
    bool _always_true;
    bool _always_invalid;
    document::select::ResultSet _doc_select_resultset;
    document::select::ResultSet _pre_doc_select_resultset;

    /**
     * If expression doesn't reference multi value attributes or
     * non-attribute fields then this selection expression can be used
     * without retrieving document from document store (must use
     * SelectContext class and populate _lid and _docId instead).
     */
    std::unique_ptr<document::select::Node> _preDocOnlySelect;

    /**
     * If expression references at least one single value attribute field
     * then this selection expression can be used to disqualify a
     * document without retrieving it from document store if it evaluates to false.
     */
    std::unique_ptr<document::select::Node> _preDocSelect;

    void setDocumentSelect(SelectPruner &docsPruner);
    void setPreDocumentSelect(const search::IAttributeManager &amgr,
                              SelectPruner &noDocsPruner);

public:
    CachedSelect();
    ~CachedSelect();

    [[nodiscard]] const AttributeVectors &attributes() const noexcept { return _attributes; }
    [[nodiscard]] uint32_t fieldNodes() const noexcept { return _fieldNodes; }
    [[nodiscard]] uint32_t attrFieldNodes() const noexcept { return _attrFieldNodes; }
    [[nodiscard]] uint32_t document_id_nodes() const noexcept { return _document_id_nodes; }
    [[nodiscard]] uint32_t svAttrFieldNodes() const noexcept { return _svAttrFieldNodes; }
    [[nodiscard]] bool is_always_false() const noexcept { return _always_false; }
    [[nodiscard]] bool is_always_true() const noexcept { return _always_true; }
    [[nodiscard]] bool is_always_invalid() const noexcept { return _always_invalid; }
    [[nodiscard]] document::select::ResultSet doc_select_resultset() const noexcept { return _doc_select_resultset; }
    [[nodiscard]] document::select::ResultSet pre_doc_select_resultset() const noexcept {
        return _pre_doc_select_resultset;
    }
    [[nodiscard]] bool needs_document() const noexcept {
        return !_always_false && !_always_true && !_always_invalid && !_preDocOnlySelect;
    }

    // Should only be used for unit testing
    const std::unique_ptr<document::select::Node> &docSelect() const { return _docSelect; }
    const std::unique_ptr<document::select::Node> &preDocOnlySelect() const { return _preDocOnlySelect; }
    const std::unique_ptr<document::select::Node> &preDocSelect() const { return _preDocSelect; }

    void set(const std::string &selection,
             const document::IDocumentTypeRepo &repo);
                  
    void set(const std::string &selection,
             const std::string &docTypeName,
             const document::Document &emptyDoc,
             const document::IDocumentTypeRepo &repo,
             const search::IAttributeManager *amgr,
             bool hasFields,
             bool has_document_ids);

    std::unique_ptr<Session> createSession() const;

};

}

