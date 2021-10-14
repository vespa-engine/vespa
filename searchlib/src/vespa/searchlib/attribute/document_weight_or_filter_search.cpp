// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_weight_or_filter_search.h"
#include "iterator_pack.h"
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/queryeval/emptysearch.h>

namespace search::attribute {

class DocumentWeightOrFilterSearchImpl : public DocumentWeightOrFilterSearch
{
    AttributeIteratorPack _children;
public:
    DocumentWeightOrFilterSearchImpl(AttributeIteratorPack&& children);
    ~DocumentWeightOrFilterSearchImpl();

    void doSeek(uint32_t docId) override;
    
    void doUnpack(uint32_t) override;

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
        return _children.get_hits(begin_id, getEndId());
    }

    Trinary is_strict() const override { return Trinary::True; }
};

DocumentWeightOrFilterSearchImpl::DocumentWeightOrFilterSearchImpl(AttributeIteratorPack&& children)
    : DocumentWeightOrFilterSearch(),
      _children(std::move(children))
{
}

DocumentWeightOrFilterSearchImpl::~DocumentWeightOrFilterSearchImpl() = default;

void
DocumentWeightOrFilterSearchImpl::doSeek(uint32_t docId)
{
    if (_children.get_docid(0) < docId) {
        _children.seek(0, docId);
    }
    uint32_t min_doc_id = _children.get_docid(0);
    for (uint16_t i = 1; i < _children.size(); ++i) {
        if (_children.get_docid(i) < docId) {
            _children.seek(i, docId);
        }
        min_doc_id = std::min(min_doc_id, _children.get_docid(i));
    }                
    setDocId(min_doc_id);
}

void
DocumentWeightOrFilterSearchImpl::doUnpack(uint32_t)
{
}

std::unique_ptr<search::queryeval::SearchIterator>
DocumentWeightOrFilterSearch::create(std::vector<DocumentWeightIterator>&& children)
{
    if (children.empty()) {
        return std::make_unique<queryeval::EmptySearch>();
    } else {
        return std::make_unique<DocumentWeightOrFilterSearchImpl>(AttributeIteratorPack(std::move(children)));
    }
}

}
