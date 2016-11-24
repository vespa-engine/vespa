// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/select/node.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/document/select/resultset.h>

namespace document {
    class DocumentTypeRepo;
}

namespace proton {

class AttributeManager;

/**
 * Cached selection expression, to avoid pruning expression for each
 * new bucket.
 */
class CachedSelect
{
public:
    typedef std::shared_ptr<CachedSelect> SP;
    // Single value attributes referenced from selection expression
    std::vector<search::AttributeVector::SP> _attributes;

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
    
    CachedSelect(void);

    void
    set(const vespalib::string &selection,
        const document::DocumentTypeRepo &repo);
                  
    void
    set(const vespalib::string &selection,
        const vespalib::string &docTypeName,
        const document::Document &emptyDoc,
        const document::DocumentTypeRepo &repo,
        const search::index::Schema &schema,
        const search::IAttributeManager *amgr,
        bool hasFields);
};

} // namespace proton

