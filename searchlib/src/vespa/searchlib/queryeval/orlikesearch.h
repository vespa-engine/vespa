// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/objects/visit.h>
#include "orsearch.h"

namespace search {
namespace queryeval {

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
    OrLikeSearch(const Children &children, const Unpack & unpacker) :
        OrSearch(children),
        _unpacker(unpacker)
    { }
private:
    virtual void onRemove(size_t index) override {
        _unpacker.onRemove(index);
    }
    virtual void onInsert(size_t index) override {
        _unpacker.onInsert(index);
    }
    virtual void doUnpack(uint32_t docid) override {
        _unpacker.unpack(docid, *this);
    }
    virtual bool needUnpack(size_t index) const override {
        return _unpacker.needUnpack(index);
    }
    Unpack _unpacker;
};


} // namespace queryeval
} // namespace search

