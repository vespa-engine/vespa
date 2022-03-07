// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "andsearch.h"

namespace search::queryeval {

/**
 * A simple implementation of the And search operation.
 **/
template <typename Unpack>
class AndSearchNoStrict : public AndSearch
{
public:
    /**
     * Create a new And Search with the given children.
     * A And Search has no strictness assumptions about
     * its children.
     *
     * @param children the search objects we are and'ing
     *        ownership of the children is taken by the MultiSearch base class.
     **/
    AndSearchNoStrict(Children children, const Unpack & unpacker) :
        AndSearch(std::move(children)),
        _unpacker(unpacker)
    { }

protected:
    void doSeek(uint32_t docid) override {
        const Children & children(getChildren());
        for (uint32_t i = 0; i < children.size(); ++i) {
            if (!children[i]->seek(docid)) {
                return;
            }
        }
        setDocId(docid);
    }
    Trinary is_strict() const override { return Trinary::False; }

    void doUnpack(uint32_t docid) override {
        _unpacker.unpack(docid, *this);
    }
    void onRemove(size_t index) override {
        _unpacker.onRemove(index);
    }
    void onInsert(size_t index) override {
        _unpacker.onInsert(index);
    }
    bool needUnpack(size_t index) const override {
        return _unpacker.needUnpack(index);
    }

private:
    Unpack _unpacker;
};

}
