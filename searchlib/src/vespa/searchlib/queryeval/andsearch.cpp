// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "andsearch.h"
#include "andsearchstrict.h"
#include <vespa/searchlib/common/bitvector.h>

namespace search {
namespace queryeval {

BitVector::UP
AndSearch::get_hits(uint32_t begin_id) {
    return andChildren(getChildren(), begin_id);
}

void
AndSearch::or_hits_into(BitVector &result, uint32_t begin_id) {
    result.orWith(*get_hits(begin_id));
}

void
AndSearch::and_hits_into(BitVector &result, uint32_t begin_id)
{
    andChildren(result, getChildren(), begin_id);
}

SearchIterator::UP AndSearch::andWith(UP filter, uint32_t estimate_)
{
    return offerFilterToChildren(std::move(filter), estimate_);
}

SearchIterator::UP AndSearch::offerFilterToChildren(UP filter, uint32_t estimate_)
{
    const Children & children(getChildren());
    for (uint32_t i(0); filter && (i < children.size()); ++i) {
        filter = children[i]->andWith(std::move(filter), estimate_);
    }
    return filter;
}

void AndSearch::doUnpack(uint32_t docid)
{
    const Children & children(getChildren());
    for (uint32_t i(0); i < children.size(); ++i) {
        children[i]->doUnpack(docid);
    }
}

AndSearch::AndSearch(const Children & children) :
    MultiSearch(children),
    _estimate(std::numeric_limits<uint32_t>::max())
{
}

namespace {

class FullUnpack
{
public:
    void unpack(uint32_t docid, const MultiSearch & search) {
        const MultiSearch::Children & children(search.getChildren());
        for (uint32_t i(0); i < children.size(); ++i) {
            children[i]->doUnpack(docid);
        }
    }
    bool needUnpack(size_t index) const {
        (void) index;
        return true;
    }
    void onRemove(size_t index) { (void) index; }
    void onInsert(size_t index) { (void) index; }
};

class SelectiveUnpack
{
public:
    SelectiveUnpack(const UnpackInfo & unpackInfo) :
        _unpackInfo(unpackInfo)
    { }
    void unpack(uint32_t docid, const MultiSearch & search) {
        auto &children = search.getChildren();
        _unpackInfo.each([&children,docid](size_t i){children[i]->doUnpack(docid);},
                         children.size());
    }
    bool needUnpack(size_t index) const {
        return _unpackInfo.needUnpack(index);
    }
    void onRemove(size_t index) {
        _unpackInfo.remove(index);
    }
    void onInsert(size_t index) {
        _unpackInfo.insert(index);
    }
private:
    UnpackInfo _unpackInfo;
};

}

AndSearch *
AndSearch::create(const MultiSearch::Children &children, bool strict)
{
    UnpackInfo unpackInfo;
    unpackInfo.forceAll();
    return create(children, strict, unpackInfo);
}

AndSearch *
AndSearch::create(const MultiSearch::Children &children, bool strict, const UnpackInfo & unpackInfo) {
    if (strict) {
        if (unpackInfo.unpackAll()) {
            return new AndSearchStrict<FullUnpack>(children, FullUnpack());
        } else if(unpackInfo.empty()) {
            return new AndSearchStrict<NoUnpack>(children, NoUnpack());
        } else {
            return new AndSearchStrict<SelectiveUnpack>(children, SelectiveUnpack(unpackInfo));
        }
    } else {
        if (unpackInfo.unpackAll()) {
            return new AndSearchNoStrict<FullUnpack>(children, FullUnpack());
        } else if (unpackInfo.empty()) {
            return new AndSearchNoStrict<NoUnpack>(children, NoUnpack());
        } else {
            return new AndSearchNoStrict<SelectiveUnpack>(children, SelectiveUnpack(unpackInfo));
        }
    }
}

}  // namespace queryeval
}  // namespace search
