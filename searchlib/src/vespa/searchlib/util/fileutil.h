// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <memory>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/stllike/string.h>

using vespalib::GenericHeader;
class Fast_BufferedFile;

namespace search {

    namespace fileutil {

        class LoadedBuffer
        {
        protected:
            void * _buffer;
            size_t _size;
            std::unique_ptr<GenericHeader> _header;
        public:
            LoadedBuffer(const LoadedBuffer & rhs) = delete;
            LoadedBuffer & operator =(const LoadedBuffer & rhs) = delete;
            typedef std::unique_ptr<LoadedBuffer> UP;

            LoadedBuffer(void * buf, size_t sz)
                    : _buffer(buf),
                      _size(sz),
                      _header(nullptr)
            { }

            virtual ~LoadedBuffer() { }
            const void * buffer() const { return _buffer; }
            const char *  c_str() const { return static_cast<const char *>(_buffer); }
            size_t size() const { return _size; }
            bool  empty() const { return _size == 0; }
            size_t size(size_t elemSize) const { return  _size/elemSize; }
            const GenericHeader &getHeader() const { return *_header; }
        };

        class LoadedMmap : public LoadedBuffer
        {
            void * _mapBuffer;
            size_t _mapSize;
        public:
            LoadedMmap(const vespalib::string &fileName);

            virtual ~LoadedMmap();
        };

    }
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
    static std::unique_ptr<FastOS_FileInterface> openFile(const vespalib::string &fileName);

    /**
     * Loads and returns the file with the given name.
     * Mmaps the file into the returned buffer.
     **/
    static fileutil::LoadedBuffer::UP loadFile(const vespalib::string &fileName);
};

class FileReaderBase
{
public:
    FileReaderBase(FastOS_FileInterface & file) : _file(file) { }
    ssize_t read(void *buf, size_t sz);
private:
    void handleError(ssize_t numRead, size_t wanted);
    FastOS_FileInterface & _file;
};

class FileWriterBase
{
public:
    FileWriterBase(FastOS_FileInterface & file) : _file(file) { }
    ssize_t write(const void *buf, size_t sz);
protected:
    void handleError(ssize_t numWritten, size_t wanted);
private:
    FastOS_FileInterface & _file;
};

template <typename T>
class FileReader : public FileReaderBase
{
public:
    FileReader(FastOS_FileInterface & file) : FileReaderBase(file) { }
    T readHostOrder() {
        T result;
        read(&result, sizeof(result));
        return result;
    }
};

class SequentialFileArray
{
public:
    SequentialFileArray(const vespalib::string & fname);
    virtual ~SequentialFileArray();
    const vespalib::string & getName() const { return _name; }
    void rewind();
    void close();
    void erase();
protected:
    void openReadOnly();
    void openWriteOnly();
    std::unique_ptr<Fast_BufferedFile> _backingFile;
    vespalib::string _name;
};

template <typename T>
class SequentialFileArrayRead : public SequentialFileArray
{
public:
    SequentialFileArrayRead(const vespalib::string & fname);
    ~SequentialFileArrayRead();
    T getNext() const { return _fileReader.readHostOrder(); }
    bool hasNext() const;
    size_t size() const;
private:
    mutable FileReader<T> _fileReader;
};

template <typename T>
class SequentialFileArrayWrite : public SequentialFileArray
{
public:
    SequentialFileArrayWrite(const vespalib::string & fname);
    void push_back(const T & v) { _count++; _fileWriter.write(&v, sizeof(v)); }
    size_t size() const { return _count; }
    bool empty() const { return _count == 0; }
private:
    size_t _count;
    FileWriterBase    _fileWriter;
};

template <typename T, typename S>
class MergeSorter
{
public:
    MergeSorter(const vespalib::string & name, size_t chunkSize);
    void push_back(const T & v);
    void commit() { sortChunk(); merge(); }
    const vespalib::string & getName() const { return _name; }
    void rewind() { }
private:
    vespalib::string genName(size_t n);
    void merge();
    void sortChunk();

    std::vector<T> _chunk;
    size_t _chunkCount;
    vespalib::string _name;
};

template <typename T>
class SequentialReadModifyWriteInterface
{
public:
    typedef T Type;
    virtual ~SequentialReadModifyWriteInterface() { }
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
    typedef vespalib::Array<T> Vector;
public:
    SequentialReadModifyWriteVector();
    SequentialReadModifyWriteVector(size_t sz);
    ~SequentialReadModifyWriteVector();
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

template <typename T, typename R, typename W>
class SequentialReaderWriter : public SequentialReadModifyWriteInterface<T>
{
public:
    SequentialReaderWriter(R & reader, W & writer);
    ~SequentialReaderWriter();
    virtual const T & read()        { return _lastRead; }
    virtual void write(const T & v) { _writer.push_back(v); }
    virtual bool next() {
        bool hasMore(_reader.hasNext());
        if (hasMore) {
            _lastRead = _reader.getNext();
        }
        return hasMore;
    }
    virtual size_t size()     const { return _reader.size(); }
    virtual void rewind() {
        _reader.rewind();
        next();
        _writer.rewind();
    }
private:
    T   _lastRead;
    R & _reader;
    W & _writer;
};

}

