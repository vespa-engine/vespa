// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/data/slime/output.h>
#include <vector>

namespace document {

class SlimeOutputToVector : public vespalib::slime::Output {
    std::vector<char> _buf;
    size_t _size;

public:
    SlimeOutputToVector() : _buf(), _size(0) {}

    virtual char *exchange(char *p, size_t commit, size_t reserve) {
        assert(!commit || p == &_buf[_size]);
        (void) p;
        _size += commit;
        if (_size + reserve > _buf.size()) {
            _buf.resize(_size + reserve);
        }
        return &_buf[_size];
    }

    const char *data() const { return &_buf[0]; }
    size_t size() const { return _size; }
};

}  // namespace document

