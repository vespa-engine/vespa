// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mapped_file_input.h"
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/mman.h>

namespace vespalib {

MappedFileInput::MappedFileInput(const vespalib::string &file_name)
    : _fd(open(file_name.c_str(), O_RDONLY)),
      _data((char *)MAP_FAILED),
      _size(0),
      _used(0)
{
    struct stat info;
    if ((_fd != -1) && fstat(_fd, &info) == 0) {
        _data = static_cast<char*>(mmap(0, info.st_size, PROT_READ, MAP_SHARED, _fd, 0));
        if (_data != MAP_FAILED) {
            _size = info.st_size;
            madvise(_data, _size, MADV_SEQUENTIAL);
#ifdef __linux__
            madvise(_data, _size, MADV_DONTDUMP);
#endif
        }
    }
}

MappedFileInput::~MappedFileInput()
{
    if (valid()) {
        munmap(_data, _size);
    }
    if (_fd != -1) {
        close(_fd);
    }
}

bool MappedFileInput::valid() const
{
    return (_data != MAP_FAILED);
}

Memory
MappedFileInput::obtain()
{
    return Memory((_data + _used), (_size - _used));
}

Input &
MappedFileInput::evict(size_t bytes)
{
    _used += bytes;
    return *this;
}

} // namespace vespalib
