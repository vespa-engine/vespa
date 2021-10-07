// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <sys/types.h>


namespace fastos {

/*
 * Class handling pointers to functions used by FastOS_File for read
 * and writa access. Unit tests might modify pointers to inject errors.
 */
class File_RW_Ops
{
    using ReadFunc = ssize_t (*)(int fd, void* buf, size_t count);
    using WriteFunc = ssize_t (*)(int fd, const void* buf, size_t count);
    using PreadFunc = ssize_t (*)(int fd, void* buf, size_t count, off_t offset);
    using PwriteFunc = ssize_t (*)(int fd, const void* buf, size_t count, off_t offset);

public:
    static ReadFunc _read;
    static WriteFunc _write;
    static PreadFunc _pread;
    static PwriteFunc _pwrite;

    static ssize_t read(int fd, void* buf, size_t count) { return _read(fd, buf, count); }
    static ssize_t write(int fd, const void* buf, size_t count) { return _write(fd, buf, count); }
    static ssize_t pread(int fd, void* buf, size_t count, off_t offset) { return _pread(fd, buf, count, offset); }
    static ssize_t pwrite(int fd, const void* buf, size_t count, off_t offset) { return _pwrite(fd, buf, count, offset); }
};

}
