// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search::queryeval {

class MultiSearch;

class UnpackInfo
{
private:
    static constexpr size_t max_size = 31;
    static constexpr size_t max_index = 255;

    uint8_t _size;
    uint8_t _unpack[max_size];

public:
    UnpackInfo();

    // add an index to unpack, will not renumber existing indexes
    UnpackInfo &add(size_t index);

    // insert an index that may need unpacking, will renumber existing indexes
    UnpackInfo &insert(size_t index, bool unpack = true);

    // remove an index and its unpack data, will renumber existing indexes
    UnpackInfo &remove(size_t index);

    UnpackInfo &forceAll() {
        _size = (max_size + 1);
        return *this;
    }

    bool unpackAll() const { return (_size > max_size); }
    bool empty() const { return (_size == 0); }
    bool needUnpack(size_t index) const;

    template <typename F>
    void each(F &&f, size_t n) const {
        if (__builtin_expect(unpackAll(), false)) {
            for (size_t i = 0; i < n; ++i) {
                f(i);
            }
        } else {
            for (size_t i = 0; i < _size; ++i) {
                f(_unpack[i]);
            }
        }
    }

    vespalib::string toString() const;
};

struct NoUnpack {
    void unpack(uint32_t docid, const MultiSearch & search) {
        (void) docid;
        (void) search;
    }
    void onRemove(size_t index) { (void) index; }
    void onInsert(size_t index) { (void) index; }
    bool needUnpack(size_t index) const { (void) index; return false; }
};

}
