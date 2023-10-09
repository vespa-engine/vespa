// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "unpackinfo.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <cassert>
#include <algorithm>

namespace search::queryeval {

UnpackInfo::UnpackInfo()
    : _size(0)
{
    memset(_unpack, 0, sizeof(_unpack));
}

UnpackInfo &
UnpackInfo::add(size_t index)
{
    if ((index <= max_index) && (_size < max_size)) {
        _unpack[_size++] = index;
        std::sort(&_unpack[0], &_unpack[_size]);
    } else {
        forceAll();
    }
    return *this;
}

UnpackInfo &
UnpackInfo::insert(size_t index, bool unpack)
{
    if (unpackAll()) {
        return *this;
    }
    for (size_t rp = 0; rp < _size; ++rp) {
        if (_unpack[rp] >= index) {
            if (_unpack[rp] == max_index) {
                forceAll();
                return *this;
            }
            ++_unpack[rp];
        }
    }
    if (unpack) {
        add(index);
    }
    return *this;
}

UnpackInfo &
UnpackInfo::remove(size_t index)
{
    if (unpackAll()) {
        return *this;
    }
    size_t wp = 0;
    bool found_index = false;
    for (size_t rp = 0; rp < _size; ++rp) {
        if (_unpack[rp] == index) {
            found_index = true;
        } else if (_unpack[rp] > index) {
            _unpack[wp++] = (_unpack[rp] - 1);
        } else {
            _unpack[wp++] = _unpack[rp];
        }
    }
    if (found_index) {
        --_size;
    }
    assert(wp == _size);
    return *this;
}

bool
UnpackInfo::needUnpack(size_t index) const
{
    if (unpackAll()) {
        return true;
    }
    for (size_t i = 0; i < _size; ++i) {
        if (_unpack[i] == index) {
            return true;
        }
    }
    return false;
}

vespalib::string 
UnpackInfo::toString() const
{
    vespalib::asciistream os;
    if (unpackAll()) {
        os << "full-unpack";
    } else if (empty()) {
        os << "no-unpack";
    } else {
        os << size_t(_unpack[0]);
        for (size_t i = 1; i < _size; ++i) {
            os << " " << size_t(_unpack[i]);
        }
    }
    return os.str();
}

}
