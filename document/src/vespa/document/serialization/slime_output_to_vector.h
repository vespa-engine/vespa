// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/data/output.h>
#include <vespa/vespalib/data/writable_memory.h>
#include <vector>

namespace document {

class SlimeOutputToVector : public vespalib::Output {
    std::vector<char> _buf;
    size_t _size;

public:
    SlimeOutputToVector();
    ~SlimeOutputToVector();

    vespalib::WritableMemory reserve(size_t reserve) override {
        if (_size + reserve > _buf.size()) {
            _buf.resize(_size + reserve);
        }
        return vespalib::WritableMemory(&_buf[_size], _buf.size() - _size);
    }

    Output &commit(size_t commit) override {
        _size += commit;
        return *this;
    }

    const char *data() const { return &_buf[0]; }
    size_t size() const { return _size; }
};

}  // namespace document

