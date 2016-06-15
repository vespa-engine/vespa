// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/fastlib/io/bufferedfile.h>
#include <vector>
#include <memory>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/stllike/string.h>

using vespalib::GenericHeader;

namespace search {

/**
 * Util class with static functions for handling attribute data files.
 **/
class FileUtil
{
public:
    /**
     * Buffer class with content loaded from file.
     **/
    class LoadedBuffer
    {
    private:
        LoadedBuffer(const LoadedBuffer & rhs);

        LoadedBuffer &
        operator =(const LoadedBuffer & rhs);
    protected:
        void * _buffer;
        size_t _size;
        std::unique_ptr<GenericHeader> _header;
    public:
        typedef std::unique_ptr<LoadedBuffer> UP;

        LoadedBuffer(void * buf, size_t sz)
            : _buffer(buf),
              _size(sz),
              _header(nullptr)
        {
        }

        virtual
        ~LoadedBuffer()
        {
        }

        const void *
        buffer() const
        {
            return _buffer;
        }

        const char *
        c_str() const
        {
            return static_cast<const char *>(_buffer);
        }

        size_t
        size() const
        {
            return _size;
        }

        bool
        empty() const
        {
            return _size == 0;
        }

        size_t
        size(size_t elemSize) const
        {
            return  _size/elemSize;
        }

        const GenericHeader &
        getHeader() const
        {
            return *_header;
        }
    };

    /**
     * Buffer class with content mmapped from file.
     **/
    class LoadedMmap : public LoadedBuffer
    {
        void * _mapBuffer;
        size_t _mapSize;
    public:
        LoadedMmap(const vespalib::string &fileName);

        virtual
        ~LoadedMmap();
    };

    /**
     * Opens and returns the file with the given name for reading.
     * Enables direct IO on the file.
     **/
    static std::unique_ptr<Fast_BufferedFile>
    openFile(const vespalib::string &fileName);

    /**
     * Loads and returns the file with the given name.
     * Mmaps the file into the returned buffer.
     **/
    static LoadedBuffer::UP
    loadFile(const vespalib::string &fileName);
};

class FileReaderBase
{
public:
    FileReaderBase(FastOS_FileInterface & file) : _file(file) { }
    ssize_t read(void *buf, size_t sz) {
        ssize_t numRead = _file.Read(buf, sz);
        if (numRead != ssize_t(sz)) {
            handleError(numRead, sz);
        }
        return numRead;
    }
private:
    void handleError(ssize_t numRead, size_t wanted);
    FastOS_FileInterface & _file;
};

class FileWriterBase
{
public:
    FileWriterBase(FastOS_FileInterface & file) : _file(file) { }
    ssize_t write(const void *buf, size_t sz) {
        ssize_t numWritten = _file.Write2(buf, sz);
        if (numWritten != ssize_t(sz)) {
            handleError(numWritten, sz);
        }
        return numWritten;
    }
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
    virtual ~SequentialFileArray() { close(); }
    const vespalib::string & getName() const { return _name; }
    void rewind();
    void close();
    void erase();
protected:
    void openReadOnly();
    void openWriteOnly();
    mutable Fast_BufferedFile _backingFile;
    vespalib::string _name;
};

template <typename T>
class SequentialFileArrayRead : public SequentialFileArray
{
public:
    SequentialFileArrayRead(const vespalib::string & fname);
    T getNext() const { return _fileReader.readHostOrder(); }
    bool hasNext() const { return _backingFile.BytesLeft() >= sizeof(T); }
    size_t size() const { return _backingFile.GetSize()/sizeof(T); }
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

template <typename T>
SequentialFileArrayRead<T>::SequentialFileArrayRead(const vespalib::string & fname) :
    SequentialFileArray(fname),
    _fileReader(_backingFile)
{
    openReadOnly();
}

template <typename T>
SequentialFileArrayWrite<T>::SequentialFileArrayWrite(const vespalib::string & fname) :
    SequentialFileArray(fname),
    _count(0),
    _fileWriter(_backingFile)
{
    openWriteOnly();
}

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

template <typename T, typename S>
MergeSorter<T, S>::MergeSorter(const vespalib::string & name, size_t chunkSize) :
    _chunk(),
    _chunkCount(0),
    _name(name + ".sorted")
{
    _chunk.reserve(chunkSize);
}

template <typename T, typename S>
void MergeSorter<T, S>::push_back(const T & v)
{
    if (_chunk.size() < _chunk.capacity()) {
        _chunk.push_back(v);
        if (_chunk.size() == _chunk.capacity()) {
            sortChunk();
        }
    }
}

template <typename T, typename S>
vespalib::string MergeSorter<T, S>::genName(size_t n)
{
    char tmp[32];
    sprintf(tmp, ".%zd", n);
    vespalib::string fname(_name);
    fname += tmp;
    return fname;
}

template <typename T, typename S>
void MergeSorter<T, S>::merge()
{
    S sorter;
    std::vector< SequentialFileArrayRead<T> *> fileParts;
    size_t count(0);
    for(size_t i(0); i < _chunkCount; i++) {
        std::unique_ptr< SequentialFileArrayRead<T> > part(new SequentialFileArrayRead<T>(genName(i)));
        size_t sz = part->size();
        if (sz > 0) {
            fileParts.push_back(part.release());
        } else {
            part->erase();
        }
        count += sz;
    }

    std::vector<T> cachedValue;
    for(size_t i(0), m(fileParts.size()); i < m; i++) {
        cachedValue.push_back(fileParts[i]->getNext());
    }
    SequentialFileArrayWrite<T> merged(_name);
    for(size_t j(0); j < count; j++) {
        size_t firstIndex(0);
        for(size_t i(1), m(cachedValue.size()); i < m; i++) {
            if (sorter.cmp(cachedValue[i], cachedValue[firstIndex])) {
                firstIndex = i;
            }
        }
        merged.push_back(cachedValue[firstIndex]);
        if ( ! fileParts[firstIndex]->hasNext() ) {
            fileParts[firstIndex]->erase();
            delete fileParts[firstIndex];
            fileParts.erase(fileParts.begin()+firstIndex);
            cachedValue.erase(cachedValue.begin()+firstIndex);
        } else {
            cachedValue[firstIndex] = fileParts[firstIndex]->getNext();
        }
    }
}

template <typename T, typename S>
void MergeSorter<T, S>::sortChunk()
{
    S sorter;
    sorter.sort(&_chunk[0], _chunk.size());
    FastOS_File chunkFile(genName(_chunkCount).c_str());
    chunkFile.EnableDirectIO();
    if (chunkFile.OpenWriteOnlyTruncate()) {
        chunkFile.CheckedWrite(&_chunk[0], _chunk.size()*sizeof(_chunk[0]));
    }
    chunkFile.Close();
    _chunkCount++;
    _chunk.clear();
}

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

template <typename T, typename A=vespalib::HeapAlloc>
class SequentialReadModifyWriteVector : public SequentialReadModifyWriteInterface<T>, public vespalib::Array<T, A>
{
private:
    typedef vespalib::Array<T, A> Vector;
public:
    SequentialReadModifyWriteVector() : Vector(), _rp(0), _wp(0) { }
    SequentialReadModifyWriteVector(size_t sz) : Vector(sz), _rp(0), _wp(0) { }
    virtual const T & read()        { return (*this)[_rp]; }
    virtual void write(const T & v) { (*this)[_wp++] = v; }
    virtual bool next()             { _rp++; return _rp < Vector::size(); }
    virtual bool empty()      const { return Vector::empty(); }
    virtual size_t size()     const { return Vector::size(); }
    virtual void rewind()           { _rp = 0; _wp = 0; }
private:
    size_t _rp;
    size_t _wp;
};

template <typename T, typename R, typename W>
class SequentialReaderWriter : public SequentialReadModifyWriteInterface<T>
{
public:
    SequentialReaderWriter(R & reader, W & writer) :
        _reader(reader),
        _writer(writer)
    {
        next();
    }
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

