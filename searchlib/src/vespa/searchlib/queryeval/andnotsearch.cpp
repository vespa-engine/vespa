// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "andnotsearch.h"
#include "termwise_helper.h"
#include <vespa/searchlib/common/bitvector.h>

namespace search::queryeval {

AndNotSearch::AndNotSearch(MultiSearch::Children children, bool elementwise)
    : MultiSearch(std::move(children)),
      _elementwise(elementwise)
{
}

AndNotSearch::~AndNotSearch() = default;

void
AndNotSearch::doSeek(uint32_t docid)
{
    const Children & children(getChildren());
    if (!children[0]->seek(docid)) {
        return; // not match in positive subtree
    }
    if (!_elementwise) {
        for (uint32_t i = 1; i < children.size(); ++i) {
            if (children[i]->seek(docid)) {
                return; // match in negative subtree
            }
        }
    }
    setDocId(docid); // we have a match
}

void
AndNotSearch::doUnpack(uint32_t docid)
{
   getChildren()[0]->doUnpack(docid);
}

SearchIterator::UP
AndNotSearchStrictBase::andWith(UP filter, uint32_t estimate)
{
    return getChildren()[0]->andWith(std::move(filter), estimate);
}

namespace {
class AndNotSearchStrict : public AndNotSearchStrictBase
{
private:
    template<bool doSeekOnlyOnPositiveChild>
    void internalSeek(uint32_t docid);
protected:
    void doSeek(uint32_t docid) override {
        internalSeek<true>(docid);
    }
public:
    /**
     * Create a new strict AndNot Search with the given children.
     * A strict AndNot can assume that the first child below is also strict.
     * No such assumptions can be made about the * other children.
     *
     * @param children the search objects we are andnot'ing
     **/
    explicit AndNotSearchStrict(Children children, bool elementwise)
        : AndNotSearchStrictBase(std::move(children), elementwise)
    { }

    void initRange(uint32_t beginid, uint32_t endid) override {
        AndNotSearch::initRange(beginid, endid);
        internalSeek<false>(beginid);
    }
   
};

template <bool doSeekOnlyOnPositiveChild>
void
AndNotSearchStrict::internalSeek(uint32_t docid)
{
    const Children & children(getChildren());
    bool hit;
    if (doSeekOnlyOnPositiveChild) {
        children[0]->doSeek(docid);
        hit = (children[0]->getDocId() == docid);
    } else {
        hit = children[0]->seek(docid);
    }
    if (!_elementwise) {
        for (uint32_t i = 1; hit && i < children.size(); ++i) {
            if (children[i]->seek(docid)) {
                hit = false;
            }
        }
    }
    if (hit) {
        setDocId(docid);
        return;
    }
    uint32_t nextId = children[0]->getDocId();
    while (!isAtEnd(nextId)) {
        bool foundHit = true;
        if (!_elementwise) {
            for (uint32_t i = 1; i < children.size(); ++i) {
                if (children[i]->seek(nextId)) {
                    foundHit = false;
                    ++nextId;
                    break;
                }
            }
        }
        if (foundHit) {
            break;
        } else {
            children[0]->doSeek(nextId);
            nextId = children[0]->getDocId();
        }
    }
    setDocId(nextId);
}

}  // namespace

std::unique_ptr<SearchIterator>
AndNotSearch::create(ChildrenIterators children_in, bool strict) {
    return create(std::move(children_in), false, strict);
}

std::unique_ptr<SearchIterator>
AndNotSearch::create(ChildrenIterators children_in, bool elementwise, bool strict) {
    MultiSearch::Children children = std::move(children_in);
    if (strict) {
        return std::make_unique<AndNotSearchStrict>(std::move(children), elementwise);
    } else {
        return SearchIterator::UP(new AndNotSearch(std::move(children), elementwise));
    }
}

BitVector::UP
AndNotSearch::get_hits(uint32_t begin_id) {
    const Children &children = getChildren();
    BitVector::UP result = children[0]->get_hits(begin_id);
    result->notSelf();
    result = TermwiseHelper::orChildren(std::move(result), children.begin()+1, children.end(), begin_id);
    result->notSelf();
    return result;
}

void
AndNotSearch::or_hits_into(BitVector &result, uint32_t begin_id) {
    result.orWith(*get_hits(begin_id));
}

void
AndNotSearch::get_element_ids(uint32_t docid, std::vector<uint32_t>& element_ids)
{
    auto& children = getChildren();
    if (children.empty()) {
        return;
    }
    children.front()->get_element_ids(docid, element_ids);
    std::span others(children.begin() + 1, children.end());
    if (element_ids.empty() || others.empty()) {
        return;
    }
    std::vector<uint32_t> temp_element_ids;
    std::vector<uint32_t> result;
    for (auto& child : others) {
        child->get_element_ids(docid, temp_element_ids);
        if (!temp_element_ids.empty()) {
            result.clear();
            std::set_difference(element_ids.begin(), element_ids.end(),
                                temp_element_ids.begin(), temp_element_ids.end(),
                                std::back_inserter(result));
            std::swap(element_ids, result);
            if (element_ids.empty()) {
                return;
            }
        }
    }
}

}
