// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include "documentmetastore.h"

namespace proton {
namespace documentmetastore {

/**
 * Search context used to search the document meta store for all valid documents.
 */
class SearchContext : public search::AttributeVector::SearchContext
{
private:
    typedef search::AttributeVector::DocId DocId;

    bool _isWord;
    document::GlobalId _gid;

    virtual unsigned int approximateHits() const;
    virtual bool onCmp(DocId docId, int32_t &weight) const;
    virtual bool onCmp(DocId docId) const;

    virtual search::queryeval::SearchIterator::UP
    createIterator(search::fef::TermFieldMatchData *matchData,
                   bool strict);

    const DocumentMetaStore &getStore() const;

public:
    SearchContext(search::QueryTermSimple::UP qTerm,
                  const DocumentMetaStore &toBeSearched);
};

} // namespace documentmetastore
} // namespace proton

