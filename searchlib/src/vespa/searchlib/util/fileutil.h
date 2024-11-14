// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <memory>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/util/array.h>
#include <string>

using vespalib::GenericHeader;

namespace search::fileutil {

class LoadedBuffer
{
protected:
    void * _buffer;
    size_t _size;
    uint64_t _size_on_disk;
    std::unique_ptr<GenericHeader> _header;
public:
    LoadedBuffer(const LoadedBuffer & rhs) = delete;
    LoadedBuffer & operator =(const LoadedBuffer & rhs) = delete;
    using UP = std::unique_ptr<LoadedBuffer>;

    LoadedBuffer(void * buf, size_t sz) noexcept
        : _buffer(buf),
          _size(sz),
          _size_on_disk(0),
          _header()
    { }

    virtual ~LoadedBuffer() = default;
    const void * buffer() const { return _buffer; }
    const char *  c_str() const { return static_cast<const char *>(_buffer); }
    size_t size() const { return _size; }
    bool  empty() const { return _size == 0; }
    size_t size(size_t elemSize) const { return  _size/elemSize; }
    uint64_t size_on_disk() const noexcept { return _size_on_disk; }
    const GenericHeader &getHeader() const { return *_header; }
};

class LoadedMmap : public LoadedBuffer
{
    void * _mapBuffer;
    size_t _mapSize;
public:
    explicit LoadedMmap(const std::string &fileName);
    ~LoadedMmap() override;
};

}

namespace search {
/**
 * Util class with static functions for handling attribute data files.
 **/
class FileUtil
{
public:

    /**
     * Opens and returns the file with the given name for reading.
     * Enables direct IO on the file.
     **/
    static std::unique_ptr<FastOS_FileInterface> openFile(const std::string &fileName);

    /**
     * Loads and returns the file with the given name.
     * Mmaps the file into the returned buffer.
     **/
    static fileutil::LoadedBuffer::UP loadFile(const std::string &fileName);
};

class FileReaderBase
{
public:
    explicit FileReaderBase(FastOS_FileInterface * file) : _file(file) { }
    ssize_t read(void *buf, size_t sz);
private:
    void handleError(ssize_t numRead, size_t wanted);
    FastOS_FileInterface * _file;
};

template <typename T>
class FileReader : public FileReaderBase
{
public:
    explicit FileReader(FastOS_FileInterface * file) : FileReaderBase(file) { }
    T readHostOrder() {
        T result;
        read(&result, sizeof(result));
        return result;
    }
};

template <typename T>
class SequentialReadModifyWriteInterface
{
public:
    using Type = T;
    virtual ~SequentialReadModifyWriteInterface() = default;
    virtual const T & read() = 0;
    virtual void write(const T & v) = 0;
    virtual bool next() = 0;
    virtual bool empty() const { return size() == 0; }
    virtual size_t size() const = 0;
    virtual void rewind() = 0;
};

template <typename T>
class SequentialReadModifyWriteVector : public SequentialReadModifyWriteInterface<T>, public vespalib::Array<T>
{
private:
    using Vector = vespalib::Array<T>;
public:
    SequentialReadModifyWriteVector();
    explicit SequentialReadModifyWriteVector(size_t sz);
    ~SequentialReadModifyWriteVector() override;
    const T & read()        override { return (*this)[_rp]; }
    void write(const T & v) override { (*this)[_wp++] = v; }
    bool next()             override { _rp++; return _rp < Vector::size(); }
    bool empty()      const override { return Vector::empty(); }
    size_t size()     const override { return Vector::size(); }
    void rewind()           override { _rp = 0; _wp = 0; }
private:
    size_t _rp;
    size_t _wp;
};

}

