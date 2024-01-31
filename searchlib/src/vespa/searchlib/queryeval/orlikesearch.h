// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "orsearch.h"
#include <vespa/vespalib/objects/visit.h>

namespace search::queryeval {

/**
 * A simple implementation of the Or search operation.
 **/
template <bool strict, typename Unpack>
class OrLikeSearch : public OrSearch
{
protected:
    void doSeek(uint32_t docid) override {
        const Children & children(getChildren());
        for (uint32_t i = 0; i < children.size(); ++i) {
            if (children[i]->seek(docid)) {
                setDocId(docid);
                return;
            }
        }
        if (strict) {
            uint32_t minNextId = children[0]->getDocId();
            for (uint32_t i = 1; i < children.size(); ++i) {
                if (children[i]->getDocId() < minNextId) {
                    minNextId = children[i]->getDocId();
                }
            }
            setDocId(minNextId);
        }
    }
    Trinary is_strict() const override { return strict ? Trinary::True : Trinary::False; }
    void visitMembers(vespalib::ObjectVisitor &visitor) const override {
        MultiSearch::visitMembers(visitor);
        visit(visitor, "strict", strict);
    }

public:
    /**
     * Create a new Or Search with the given children.  A strict Or
     * can assume that all children below are also strict. A
     * non-strict Or has no strictness assumptions about its children.
     *
     * @param children the search objects we are or'ing
     **/
    OrLikeSearch(Children children, const Unpack & unpacker)
      : OrSearch(std::move(children)),
        _unpacker(unpacker)
    { }
private:
    void onRemove(size_t index) override {
        _unpacker.onRemove(index);
    }
    void onInsert(size_t index) override {
        _unpacker.onInsert(index);
    }
    void doUnpack(uint32_t docid) override {
        _unpacker.unpack(docid, *this);
    }
    bool needUnpack(size_t index) const override {
        return _unpacker.needUnpack(index);
    }
    Unpack _unpacker;
};

template <typename Unpack, typename HEAP, typename ref_t>
class StrictHeapOrSearch : public OrSearch
{
private:
    struct Less {
        const uint32_t *child_docid;
        explicit Less(const std::vector<uint32_t> &cd) : child_docid(cd.data()) {}
        bool operator()(const ref_t &a, const ref_t &b) const {
            return (child_docid[a] < child_docid[b]);
        }
    };

    std::vector<uint32_t> _child_docid;
    std::vector<ref_t>    _data_space;
    ref_t                *_data_begin;
    ref_t                *_data_end;
    Unpack                _unpacker;

    void init_data() {
        _data_space.resize(getChildren().size());
        for (size_t i = 0; i < getChildren().size(); ++i) {
            _data_space[i] = i;
        }
        _data_begin = _data_space.data();
        _data_end = _data_begin + _data_space.size();
    }
    void onRemove(size_t index) final {
        _unpacker.onRemove(index);
        _child_docid.erase(_child_docid.begin() + index);
        init_data();
    }
    void onInsert(size_t index) final {
        _unpacker.onInsert(index);
        _child_docid.insert(_child_docid.begin() + index, getChildren()[index]->getDocId());
        init_data();
    }
    void seek_child(ref_t child, uint32_t docid) {
        getChildren()[child]->doSeek(docid);
        _child_docid[child] = getChildren()[child]->getDocId();
    }

public:
    StrictHeapOrSearch(Children children, const Unpack &unpacker)
      : OrSearch(std::move(children)),
        _child_docid(getChildren().size()),
        _data_space(),
        _data_begin(nullptr),
        _data_end(nullptr),
        _unpacker(unpacker)
    {
        HEAP::require_left_heap();
        init_data();
    }
    void initRange(uint32_t begin, uint32_t end) final {
        OrSearch::initRange(begin, end);
        for (size_t i = 0; i < getChildren().size(); ++i) {
            _child_docid[i] = getChildren()[i]->getDocId();
        }
        for (size_t i = 2; i <= _data_space.size(); ++i) {
            HEAP::push(_data_begin, _data_begin + i, Less(_child_docid));
        }
    }
    void doSeek(uint32_t docid) final {
        while (_child_docid[HEAP::front(_data_begin, _data_end)] < docid) {
            seek_child(HEAP::front(_data_begin, _data_end), docid);
            HEAP::adjust(_data_begin, _data_end, Less(_child_docid));
        }
        setDocId(_child_docid[HEAP::front(_data_begin, _data_end)]);
    }
    void doUnpack(uint32_t docid) final {
        _unpacker.each([&](ref_t child) {
                           if (__builtin_expect(_child_docid[child] == docid, false)) {
                               getChildren()[child]->doUnpack(docid);
                           }
                       }, getChildren().size());
    }
    bool needUnpack(size_t index) const final {
        return _unpacker.needUnpack(index);
    }
    Trinary is_strict() const final { return Trinary::True; }
};

}
