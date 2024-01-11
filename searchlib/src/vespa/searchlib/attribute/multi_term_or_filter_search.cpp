// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multi_term_or_filter_search.h"
#include "posting_iterator_pack.h"
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/queryeval/iterator_pack.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/queryeval/emptysearch.h>

using search::queryeval::SearchIteratorPack;

namespace search::attribute {

template<typename IteratorPack>
class MultiTermOrFilterSearchImpl : public MultiTermOrFilterSearch
{
    // Vector that caches the docids of the current positions of the iterators in the pack.
    // This reduces cache misses in doSeek() and seek_all().
    std::vector<uint32_t> _docids;
    IteratorPack _children;
    fef::TermFieldMatchData* _tfmd;
    void seek_all(uint32_t docId);
public:
    explicit MultiTermOrFilterSearchImpl(IteratorPack&& children, fef::TermFieldMatchData* tfmd);
    ~MultiTermOrFilterSearchImpl() override;

    void doSeek(uint32_t docId) override;
    
    void doUnpack(uint32_t docid) override {
        if (_tfmd != nullptr) {
            _tfmd->resetOnlyDocId(docid);
        }
    }

    void initRange(uint32_t begin, uint32_t end) override {
        SearchIterator::initRange(begin, end);
        _children.initRange(begin, end);
        for (uint16_t i = 0; i < _children.size(); ++i) {
            _docids[i] = _children.get_docid(i);
        }
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
MultiTermOrFilterSearchImpl<IteratorPack>::MultiTermOrFilterSearchImpl(IteratorPack&& children, fef::TermFieldMatchData* tfmd)
    : MultiTermOrFilterSearch(),
      _docids(children.size(), 0),
      _children(std::move(children)),
      _tfmd(tfmd)
{
}

template<typename IteratorPack>
MultiTermOrFilterSearchImpl<IteratorPack>::~MultiTermOrFilterSearchImpl() = default;

template<typename IteratorPack>
void
MultiTermOrFilterSearchImpl<IteratorPack>::seek_all(uint32_t docId) {
    for (uint16_t i = 0; i < _children.size(); ++i) {
        uint32_t next = _docids[i];
        if (next < docId) {
            next = _children.seek(i, docId);
            _docids[i] = next;
        }
    }
}

template<typename IteratorPack>
void
MultiTermOrFilterSearchImpl<IteratorPack>::doSeek(uint32_t docId)
{
    uint32_t min_doc_id = endDocId;
    for (uint16_t i = 0; i < _children.size(); ++i) {
        uint32_t next = _docids[i];
        if (next < docId) {
            next = _children.seek(i, docId);
            _docids[i] = next;
        }
        if (next == docId) {
            setDocId(next);
            return;
        }
        min_doc_id = std::min(min_doc_id, next);
    }                
    setDocId(min_doc_id);
}

namespace {

template <typename IteratorType, typename IteratorPackType>
std::unique_ptr<queryeval::SearchIterator>
create_helper(std::vector<IteratorType>&& children, fef::TermFieldMatchData* tfmd)
{
    if (children.empty()) {
        return std::make_unique<queryeval::EmptySearch>();
    } else {
        std::sort(children.begin(), children.end(),
                  [](const auto & a, const auto & b) { return a.size() > b.size(); });
        using OrFilter = MultiTermOrFilterSearchImpl<IteratorPackType>;
        return std::make_unique<OrFilter>(IteratorPackType(std::move(children)), tfmd);
    }
}

}

std::unique_ptr<queryeval::SearchIterator>
MultiTermOrFilterSearch::create(std::vector<DocidIterator>&& children)
{
    return create_helper<DocidIterator, DocidIteratorPack>(std::move(children), nullptr);
}

std::unique_ptr<queryeval::SearchIterator>
MultiTermOrFilterSearch::create(std::vector<DocidIterator>&& children, fef::TermFieldMatchData& tfmd)
{
    return create_helper<DocidIterator, DocidIteratorPack>(std::move(children), &tfmd);
}

std::unique_ptr<queryeval::SearchIterator>
MultiTermOrFilterSearch::create(std::vector<DocidWithWeightIterator>&& children)
{
    return create_helper<DocidWithWeightIterator, DocidWithWeightIteratorPack>(std::move(children), nullptr);
}

std::unique_ptr<queryeval::SearchIterator>
MultiTermOrFilterSearch::create(std::vector<DocidWithWeightIterator>&& children, fef::TermFieldMatchData& tfmd)
{
    return create_helper<DocidWithWeightIterator, DocidWithWeightIteratorPack>(std::move(children), &tfmd);
}

std::unique_ptr<queryeval::SearchIterator>
MultiTermOrFilterSearch::create(const std::vector<SearchIterator *>& children,
                                     std::unique_ptr<fef::MatchData> md)
{
    if (children.empty()) {
        return std::make_unique<queryeval::EmptySearch>();
    } else {
        using OrFilter = MultiTermOrFilterSearchImpl<SearchIteratorPack>;
        return std::make_unique<OrFilter>(SearchIteratorPack(children, std::move(md)), nullptr);
    }
}

}
