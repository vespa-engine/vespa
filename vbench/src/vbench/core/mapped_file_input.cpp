// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "mapped_file_input.h"

namespace vbench {

MappedFileInput::MappedFileInput(const string &name)
    : _file(open(name.c_str(), O_RDONLY)),
      _data(static_cast<char*>(MAP_FAILED)),
      _size(0),
      _taint(strfmt("could not open file: %s", name.c_str())),
      _pos(0)
{
    struct stat info;
    if (_file >= 0 && fstat(_file, &info) == 0) {
        _data = static_cast<char*>(mmap(0, info.st_size,
                                        PROT_READ, MAP_SHARED, _file, 0));
        if (_data != MAP_FAILED) {
            _size = info.st_size;
            madvise(_data, _size, MADV_SEQUENTIAL);
            _taint.reset();
        }
    }
}

Memory
MappedFileInput::obtain()
{
    return Memory(_data + _pos, (_size - _pos));
}

Input &
MappedFileInput::evict(size_t bytes)
{
    assert(bytes <= (_size - _pos));
    _pos += bytes;
    return *this;
}

} // namespace vbench
