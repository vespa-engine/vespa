// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "orsearch.h"
#include "orlikesearch.h"
#include "termwise_helper.h"
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/vespalib/util/left_right_heap.h>

namespace search::queryeval {

namespace {

using vespalib::LeftArrayHeap;
using vespalib::LeftHeap;

class FullUnpack
{
public:
    void unpack(uint32_t docid, MultiSearch & search) {
        const MultiSearch::Children & children(search.getChildren());
        size_t sz(children.size());
        for (size_t i(0); i < sz; ++i) {
            if (__builtin_expect(children[i]->getDocId() < docid, false)) {
                children[i]->doSeek(docid);
            }
            if (__builtin_expect(children[i]->getDocId() == docid, false)) {
                children[i]->doUnpack(docid);
            }
        }
    }
    void each(auto &&f, size_t n) {
        for (size_t i = 0; i < n; ++i) {
            f(i);
        }
    }
    void onRemove(size_t index) { (void) index; }
    void onInsert(size_t index) { (void) index; }
    bool needUnpack(size_t index) const { (void) index; return true; }
};

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
    void each(auto &&f, size_t n) {
        _unpackInfo.each(std::forward<decltype(f)>(f), n);
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

template <typename Unpack>
SearchIterator::UP create_strict_or(std::vector<SearchIterator::UP> children, const Unpack &unpack, OrSearch::StrictImpl strict_impl) {
    if (strict_impl == OrSearch::StrictImpl::HEAP) {
        if (children.size() <= 0x70) {
            return std::make_unique<StrictHeapOrSearch<Unpack,LeftArrayHeap,uint8_t>>(std::move(children), unpack);
        } else if (children.size() <= 0xff) {
            return std::make_unique<StrictHeapOrSearch<Unpack,LeftHeap,uint8_t>>(std::move(children), unpack);
        } else if (children.size() <= 0xffff) {
            return std::make_unique<StrictHeapOrSearch<Unpack,LeftHeap,uint16_t>>(std::move(children), unpack);
        } else {
            return std::make_unique<StrictHeapOrSearch<Unpack,LeftHeap,uint32_t>>(std::move(children), unpack);
        }
    }
    return std::make_unique<OrLikeSearch<true,Unpack>>(std::move(children), unpack);
}

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

SearchIterator::UP
OrSearch::create(ChildrenIterators children, bool strict) {
    UnpackInfo unpackInfo;
    unpackInfo.forceAll();
    return create(std::move(children), strict, unpackInfo);
}

SearchIterator::UP
OrSearch::create(ChildrenIterators children, bool strict, const UnpackInfo & unpackInfo) {
    return create(std::move(children), strict, unpackInfo, StrictImpl::HEAP);
}

SearchIterator::UP
OrSearch::create(ChildrenIterators children, bool strict, const UnpackInfo & unpackInfo, StrictImpl strict_impl) {
    if (strict) {
        if (unpackInfo.unpackAll()) {
            return create_strict_or(std::move(children), FullUnpack(), strict_impl);
        } else if(unpackInfo.empty()) {
            return create_strict_or(std::move(children), NoUnpack(), strict_impl);
        } else {
            return create_strict_or(std::move(children), SelectiveUnpack(unpackInfo), strict_impl);
        }
    } else {
        if (unpackInfo.unpackAll()) {
            using MyOr = OrLikeSearch<false, FullUnpack>;
            return std::make_unique<MyOr>(std::move(children), FullUnpack());
        } else if(unpackInfo.empty()) {
            using MyOr = OrLikeSearch<false, NoUnpack>;
            return std::make_unique<MyOr>(std::move(children), NoUnpack());
        } else {
            using MyOr = OrLikeSearch<false, SelectiveUnpack>;
            return std::make_unique<MyOr>(std::move(children), SelectiveUnpack(unpackInfo));
        }
    }
}

}
