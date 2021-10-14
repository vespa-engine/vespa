// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fileutil.h"
#include <vespa/fastlib/io/bufferedfile.h>

namespace search {

template <typename T>
SequentialFileArrayRead<T>::SequentialFileArrayRead(const vespalib::string & fname) :
    SequentialFileArray(fname),
    _fileReader(std::make_unique<Fast_BufferedFile>(_backingFile))
{
    openReadOnly();
}

template <typename T>
bool
SequentialFileArrayRead<T>::hasNext() const {
    return _backingFile->BytesLeft() >= sizeof(T);

}

template <typename T>
size_t SequentialFileArrayRead<T>::size() const
{
    return _backingFile->GetSize()/sizeof(T);
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
SequentialReadModifyWriteVector<T>::SequentialReadModifyWriteVector()
    : Vector(),
      _rp(0),
      _wp(0)
{ }

template <typename T>
SequentialReadModifyWriteVector<T>::SequentialReadModifyWriteVector(size_t sz)
    : Vector(sz),
      _rp(0),
      _wp(0)
{ }

template <typename T>
SequentialReadModifyWriteVector<T>::~SequentialReadModifyWriteVector() { }

template <typename T, typename R, typename W>
SequentialReaderWriter<T, R, W>::SequentialReaderWriter(R & reader, W & writer)
    : _reader(reader),
      _writer(writer)
{
    next();
}

template <typename T, typename R, typename W>
SequentialReaderWriter<T, R, W>::~SequentialReaderWriter() { }

}
