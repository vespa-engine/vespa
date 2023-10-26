// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdio>
#include <unistd.h>

namespace vespalib {

/**
 * @brief A FilePointer wraps a bald FILE pointer inside a guarding
 * object.
 *
 * The underlying file is closed when the FilePointer object is
 * destructed.
 **/
class FilePointer
{
private:
    FILE *_fp;
public:
    /**
     * @brief Create a FilePointer from a FILE pointer.
     *
     * @param file the underlying FILE pointer
     **/
    explicit FilePointer(FILE *file = nullptr) noexcept : _fp(file) {}
    FilePointer(const FilePointer &) = delete;
    FilePointer &operator=(const FilePointer &) = delete;
    /**
     * @brief Close the file if it is still open.
     **/
    ~FilePointer() { reset(); }
    /**
     * @brief Check whether we have a FILE pointer (not nullptr)
     *
     * @return true if we have an underlying FILE pointer
     **/
    bool valid() const noexcept { return (_fp != nullptr); }
    /**
     * @brief Obtain the internal FILE pointer
     *
     * @return internal FILE pointer
     **/
    FILE *fp() const noexcept { return _fp; }
    /**
     * @brief Implicit cast to obtain internal FILE pointer
     *
     * @return internal FILE pointer
     **/
    operator FILE*() noexcept { return _fp; }
    /**
     * @brief Take ownership of a new FILE pointer.
     *
     * The previously owned FILE pointer is closed, if present.
     **/
    void reset(FILE *file = nullptr) {
        if (valid()) {
            fclose(_fp);
        }
        _fp = file;
    }
    /**
     * @brief Release ownership of the current FILE pointer.
     *
     * The file will no longer be closed by the destructor.
     *
     * @return the released FILE pointer
     **/
    FILE *release() noexcept {
        FILE *tmp = _fp;
        _fp = nullptr;
        return tmp;
    }
};

/**
 * @brief A FileDescriptor wraps a file descriptor inside a guarding object.
 *
 * The underlying file is closed when the FileDescriptor object is
 * destructed.
 **/
class FileDescriptor
{
private:
    int _fd;
public:
    /**
     * @brief Create a FileDescriptor from a file descriptor.
     *
     * @param file the underlying file descriptor
     **/
    explicit FileDescriptor(int file = -1) noexcept : _fd(file) {}
    FileDescriptor(const FileDescriptor &) = delete;
    FileDescriptor &operator=(const FileDescriptor &) = delete;
    /**
     * @brief Close the file if it is still open.
     **/
    ~FileDescriptor() { reset(); }
    /**
     * @brief Check whether we have a file descriptor (not -1)
     *
     * @return true if we have an underlying file descriptor
     **/
    bool valid() const noexcept { return (_fd >= 0); }
    /**
     * @brief Obtain the internal file descriptor
     *
     * @return internal file descriptor
     **/
    int fd() const noexcept { return _fd; }
    /**
     * @brief Take ownership of a new file descriptor.
     *
     * The previously owned file descriptor is closed, if present.
     **/
    void reset(int file = -1) {
        if (valid()) {
            close(_fd);
        }
        _fd = file;
    }
    /**
     * @brief Release ownership of the current file descriptor.
     *
     * The file will no longer be closed by the destructor.
     *
     * @return the released file descriptor
     **/
    int release() noexcept {
        int tmp = _fd;
        _fd = -1;
        return tmp;
    }
};


/**
 * @brief A CounterGuard increases an int in the constructor and decreases
 * it in the destructor.
 *
 * This is typically used to keep track of how many threads are inside
 * a given scope at a given time (for example, how many threads that
 * are currently waiting for a specific signal on a monitor).
 **/
class CounterGuard
{
private:
    int &_cnt;
public:
    /**
     * @brief Increase the value
     *
     * @param cnt a reference to the value that will be modified
     **/
    explicit CounterGuard(int &cnt) noexcept : _cnt(cnt) { ++cnt; }
    CounterGuard(const CounterGuard &) = delete;
    CounterGuard &operator=(const CounterGuard &) = delete;
    /**
     * @brief Decrease the value
     **/
    ~CounterGuard() { --_cnt; }
};

} // namespace vespalib

