// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "orsearch.h"
#include "orlikesearch.h"
#include "termwise_helper.h"
#include <vespa/searchlib/common/bitvector.h>

namespace search {
namespace queryeval {

namespace {

class FullUnpack
{
public:
    void unpack(uint32_t docid, MultiSearch & search) {
        const MultiSearch::Children & children(search.getChildren());
        size_t sz(children.size());
        for (size_t i(0); i < sz; ) {
            if (__builtin_expect(children[i]->getDocId() < docid, false)) {
                children[i]->doSeek(docid);
                if (children[i]->getDocId() == search::endDocId) {
                    sz = deactivate(search, i);
                    continue;
                }
            }
            if (__builtin_expect(children[i]->getDocId() == docid, false)) {
                children[i]->doUnpack(docid);
            }
            i++;
        }
    }
    void onRemove(size_t index) { (void) index; }
    void onInsert(size_t index) { (void) index; }
    bool needUnpack(size_t index) const { (void) index; return true; }
private:
    static size_t deactivate(MultiSearch &children, size_t idx);
};

size_t
FullUnpack::deactivate(MultiSearch & search, size_t idx)
{
    search.remove(idx);
    return search.getChildren().size();
}

class SelectiveUnpack
{
public:
    SelectiveUnpack(const UnpackInfo & unpackInfo) :
        _unpackInfo(unpackInfo)
    { }
    void unpack(uint32_t docid, const MultiSearch & search) {
        auto &children = search.getChildren();
        _unpackInfo.each([&children,docid](size_t i) {
                    SearchIterator &child = *children[i];
                    if (__builtin_expect(child.getDocId() < docid, false)) {
                        child.doSeek(docid);
                    }
                    if (__builtin_expect(child.getDocId() == docid, false)) {
                        child.doUnpack(docid);
                    }
                }, children.size());
    }
    void onRemove(size_t index) {
        _unpackInfo.remove(index);
    }
    void onInsert(size_t index) {
        _unpackInfo.insert(index);
    }
    bool needUnpack(size_t index) const {
        return _unpackInfo.needUnpack(index);
    }
private:
    UnpackInfo _unpackInfo;
};

}

BitVector::UP
OrSearch::get_hits(uint32_t begin_id) {
    return TermwiseHelper::orChildren(getChildren().begin(), getChildren().end(), begin_id);
}

void
OrSearch::and_hits_into(BitVector &result, uint32_t begin_id) {
    result.andWith(*get_hits(begin_id));
}
    
void
OrSearch::or_hits_into(BitVector &result, uint32_t begin_id)
{
    TermwiseHelper::orChildren(result, getChildren().begin(), getChildren().end(), begin_id);
}

SearchIterator *
OrSearch::create(const MultiSearch::Children &children, bool strict) {
    UnpackInfo unpackInfo;
    unpackInfo.forceAll();
    return create(children, strict, unpackInfo);
}

SearchIterator *
OrSearch::create(const MultiSearch::Children &children, bool strict, const UnpackInfo & unpackInfo) {
    (void) unpackInfo;
    if (strict) {
        if (unpackInfo.unpackAll()) {
            return new OrLikeSearch<true, FullUnpack>(children, FullUnpack());
        } else if(unpackInfo.empty()) {
            return new OrLikeSearch<true, NoUnpack>(children, NoUnpack());
        } else {
            return new OrLikeSearch<true, SelectiveUnpack>(children, SelectiveUnpack(unpackInfo));
        }
    } else {
        if (unpackInfo.unpackAll()) {
            return new OrLikeSearch<false, FullUnpack>(children, FullUnpack());
        } else if(unpackInfo.empty()) {
            return new OrLikeSearch<false, NoUnpack>(children, NoUnpack());
        } else {
            return new OrLikeSearch<false, SelectiveUnpack>(children, SelectiveUnpack(unpackInfo));
        }
    }
}

}  // namespace queryeval
}  // namespace search
