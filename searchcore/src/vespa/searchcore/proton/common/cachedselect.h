// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/select/resultset.h>
#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace document {
    class DocumentTypeRepo;
    class Document;
    namespace select { class Node; }
}
namespace search {
    class AttributeVector;
    class IAttributeManager;
}

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
    typedef std::shared_ptr<CachedSelect> SP;

    class Session {
    private:
        std::unique_ptr<document::select::Node> _docSelect;
        std::unique_ptr<document::select::Node> _preDocOnlySelect;
        std::unique_ptr<document::select::Node> _preDocSelect;

    public:
        Session(std::unique_ptr<document::select::Node> docSelect,
                std::unique_ptr<document::select::Node> preDocOnlySelect,
                std::unique_ptr<document::select::Node> preDocSelect);
        bool contains(const SelectContext &context) const;
        bool contains(const document::Document &doc) const;
        const document::select::Node &selectNode() const;
    };

    using AttributeVectors = std::vector<std::shared_ptr<search::AttributeVector>>;

private:
    // Single value attributes referenced from selection expression
    AttributeVectors _attributes;

    // Pruned selection expression, specific for a document type
    std::unique_ptr<document::select::Node> _docSelect;
    uint32_t _fieldNodes;
    uint32_t _attrFieldNodes;
    uint32_t _svAttrFieldNodes;
    bool _allFalse;
    bool _allTrue;
    bool _allInvalid;

    /**
     * If expression doesn't reference multi value attributes or
     * non-attribute fields then this selection expression can be used
     * without retrieving document from document store (must use
     * SelectContext class and populate _docId instead).
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

    const AttributeVectors &attributes() const { return _attributes; }
    uint32_t fieldNodes() const { return _fieldNodes; }
    uint32_t attrFieldNodes() const { return _attrFieldNodes; }
    uint32_t svAttrFieldNodes() const { return _svAttrFieldNodes; }
    bool allFalse() const { return _allFalse; }
    bool allTrue() const { return _allTrue; }
    bool allInvalid() const { return _allInvalid; }

    // Should only be used for unit testing
    const std::unique_ptr<document::select::Node> &docSelect() const { return _docSelect; }
    const std::unique_ptr<document::select::Node> &preDocOnlySelect() const { return _preDocOnlySelect; }
    const std::unique_ptr<document::select::Node> &preDocSelect() const { return _preDocSelect; }

    void set(const vespalib::string &selection,
             const document::DocumentTypeRepo &repo);
                  
    void set(const vespalib::string &selection,
             const vespalib::string &docTypeName,
             const document::Document &emptyDoc,
             const document::DocumentTypeRepo &repo,
             const search::IAttributeManager *amgr,
             bool hasFields);

    std::unique_ptr<Session> createSession() const;

};

}

