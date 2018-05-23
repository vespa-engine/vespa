// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include "documentmetastore.h"

namespace proton::documentmetastore {

/**
 * Search context used to search the document meta store for all valid documents.
 */
class SearchContext : public search::AttributeVector::SearchContext
{
private:
    using DocId = search::AttributeVector::DocId;

    bool _isWord;
    document::GlobalId _gid;

    unsigned int approximateHits() const override;
    int32_t onFind(DocId docId, int32_t elemId, int32_t &weight) const override;
    int32_t onFind(DocId docId, int32_t elemId) const override;

    search::queryeval::SearchIterator::UP
    createIterator(search::fef::TermFieldMatchData *matchData, bool strict) override;

    const DocumentMetaStore &getStore() const;

public:
    SearchContext(std::unique_ptr<search::QueryTermSimple> qTerm, const DocumentMetaStore &toBeSearched);
};

}
