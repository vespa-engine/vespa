// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributevector.h"
#include "bitvector_search_cache.h"
#include <vespa/searchcommon/attribute/i_search_context.h>
#include <vespa/searchlib/attribute/posting_list_merger.h>
#include <vespa/searchlib/common/i_document_meta_store_context.h>
#include <vespa/vespalib/util/arrayref.h>

namespace search::fef { class TermFieldMatchData; }

namespace search::attribute {

class ReferenceAttribute;
class ImportedAttributeVector;
class SearchContextParams;

/**
 * Search context exposing iteraton over an imported attribute vector.
 *
 * Iterator doc id matching is performed via the GID->LID indirection of the
 * associated reference attribute. This means that if the _referenced_ document
 * matches the search term, the doc id of the _referring_ document will be
 * considered a match.
 */
class ImportedSearchContext : public ISearchContext {
    using TargetLids = vespalib::ConstArrayRef<uint32_t>;
    const ImportedAttributeVector&                  _imported_attribute;
    vespalib::string                                _queryTerm;
    bool                                            _useSearchCache;
    BitVectorSearchCache::Entry::SP                 _searchCacheLookup;
    IDocumentMetaStoreContext::IReadGuard::UP       _dmsReadGuard;
    const ReferenceAttribute&                       _reference_attribute;
    const IAttributeVector                         &_target_attribute;
    std::unique_ptr<ISearchContext>                 _target_search_context;
    TargetLids                                      _targetLids;
    PostingListMerger<int32_t>                      _merger;
    bool                                            _fetchPostingsDone;

    uint32_t getTargetLid(uint32_t lid) const {
        return _targetLids[lid];
    }

    void makeMergedPostings(bool isFilter);
    void considerAddSearchCacheEntry();
public:
    ImportedSearchContext(std::unique_ptr<QueryTermSimple> term,
                          const SearchContextParams& params,
                          const ImportedAttributeVector& imported_attribute,
                          const attribute::IAttributeVector &target_attribute);
    ~ImportedSearchContext() override;


    std::unique_ptr<queryeval::SearchIterator>
    createIterator(fef::TermFieldMatchData* matchData, bool strict) override;
    unsigned int approximateHits() const override;
    void fetchPostings(bool strict) override;
    bool valid() const override;
    Int64Range getAsIntegerTerm() const override;
    const QueryTermBase& queryTerm() const override;
    const vespalib::string& attributeName() const override;

    using DocId = IAttributeVector::DocId;

    int32_t find(DocId docId, int32_t elemId, int32_t& weight) const {
        return _target_search_context->find(getTargetLid(docId), elemId, weight);
    }

    int32_t find(DocId docId, int32_t elemId) const {
        return _target_search_context->find(getTargetLid(docId), elemId);
    }

    int32_t onFind(uint32_t docId, int32_t elemId, int32_t &weight) const override { return find(docId, elemId, weight); }
    int32_t onFind(uint32_t docId, int32_t elemId) const override { return find(docId, elemId); }

    const ReferenceAttribute& attribute() const noexcept { return _reference_attribute; }

    const ISearchContext &target_search_context() const noexcept {
        return *_target_search_context;
    }
};

}
