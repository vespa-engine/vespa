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
        std::unique_ptr<document::select::Node> _select;
        bool _isAttrSelect;

    public:
        Session(std::unique_ptr<document::select::Node> select, bool isAttrSelect);
        bool contains(const SelectContext &context) const;
        bool contains(const document::Document &doc) const;
        const document::select::Node &selectNode() const { return *_select; }
    };

    using AttributeVectors = std::vector<std::shared_ptr<search::AttributeVector>>;

private:
    // Single value attributes referenced from selection expression
    AttributeVectors _attributes;

    // Pruned selection expression, specific for a document type
    std::unique_ptr<document::select::Node> _select;
    uint32_t _fieldNodes;
    uint32_t _attrFieldNodes;
    uint32_t _svAttrFieldNodes;
    document::select::ResultSet _resultSet;
    bool _allFalse;
    bool _allTrue;
    bool _allInvalid;

    /*
     * If expression doesn't reference multi value attributes or
     * non-attribute fields then this selection expression can be used
     * without retrieving document from document meta store (must use
     * SelectContext class and populate _docId instead).
     */
    std::unique_ptr<document::select::Node> _attrSelect;

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
    const std::unique_ptr<document::select::Node> &select() const { return _select; }
    const std::unique_ptr<document::select::Node> &attrSelect() const { return _attrSelect; }

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

