// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_weight_or_filter_search.h"
#include "posting_iterator_pack.h"
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/queryeval/iterator_pack.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/queryeval/emptysearch.h>

using search::queryeval::SearchIteratorPack;

namespace search::attribute {

template<typename IteratorPack>
class DocumentWeightOrFilterSearchImpl : public DocumentWeightOrFilterSearch
{
    IteratorPack _children;
    void seek_all(uint32_t docId);
public:
    explicit DocumentWeightOrFilterSearchImpl(IteratorPack&& children);
    ~DocumentWeightOrFilterSearchImpl() override;

    void doSeek(uint32_t docId) override;
    
    void doUnpack(uint32_t) override { }

    void initRange(uint32_t begin, uint32_t end) override {
        SearchIterator::initRange(begin, end);
        _children.initRange(begin, end);
    }

    void or_hits_into(BitVector &result, uint32_t begin_id) override {
        return _children.or_hits_into(result, begin_id);
    }

    void and_hits_into(BitVector &result, uint32_t begin_id) override {
        return result.andWith(*get_hits(begin_id));
    }

    std::unique_ptr<BitVector> get_hits(uint32_t begin_id) override {
        seek_all(getDocId());
        return _children.get_hits(begin_id, getEndId());
    }

    Trinary is_strict() const override { return Trinary::True; }
};

template<typename IteratorPack>
DocumentWeightOrFilterSearchImpl<IteratorPack>::DocumentWeightOrFilterSearchImpl(IteratorPack&& children)
    : DocumentWeightOrFilterSearch(),
      _children(std::move(children))
{
}

template<typename IteratorPack>
DocumentWeightOrFilterSearchImpl<IteratorPack>::~DocumentWeightOrFilterSearchImpl() = default;

template<typename IteratorPack>
void
DocumentWeightOrFilterSearchImpl<IteratorPack>::seek_all(uint32_t docId) {
    for (uint16_t i = 0; i < _children.size(); ++i) {
        uint32_t next = _children.get_docid(i);
        if (next < docId) {
            _children.seek(i, docId);
        }
    }
}

template<typename IteratorPack>
void
DocumentWeightOrFilterSearchImpl<IteratorPack>::doSeek(uint32_t docId)
{
    uint32_t min_doc_id = endDocId;
    for (uint16_t i = 0; i < _children.size(); ++i) {
        uint32_t next = _children.get_docid(i);
        if (next < docId) {
            next = _children.seek(i, docId);
        }
        if (next == docId) {
            setDocId(next);
            return;
        }
        min_doc_id = std::min(min_doc_id, next);
    }                
    setDocId(min_doc_id);
}

std::unique_ptr<queryeval::SearchIterator>
DocumentWeightOrFilterSearch::create(std::vector<DocidWithWeightIterator>&& children)
{
    if (children.empty()) {
        return std::make_unique<queryeval::EmptySearch>();
    } else {
        std::sort(children.begin(), children.end(),
                  [](const auto & a, const auto & b) { return a.size() > b.size(); });
        using OrFilter = DocumentWeightOrFilterSearchImpl<DocidWithWeightIteratorPack>;
        return std::make_unique<OrFilter>(DocidWithWeightIteratorPack(std::move(children)));
    }
}

std::unique_ptr<queryeval::SearchIterator>
DocumentWeightOrFilterSearch::create(const std::vector<SearchIterator *>& children,
                                     std::unique_ptr<fef::MatchData> md)
{
    if (children.empty()) {
        return std::make_unique<queryeval::EmptySearch>();
    } else {
        using OrFilter = DocumentWeightOrFilterSearchImpl<SearchIteratorPack>;
        return std::make_unique<OrFilter>(SearchIteratorPack(children, std::move(md)));
    }
}

}
