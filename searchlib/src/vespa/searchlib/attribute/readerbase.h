// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/util/file_with_header.h>
#include <vespa/searchlib/util/fileutil.h>

namespace search {

class AttributeVector;

class ReaderBase
{
public:
    ReaderBase(AttributeVector & attr);
    virtual ~ReaderBase();

    void rewind();
    bool hasWeight() const;
    bool hasIdx() const;
    bool hasData() const;

    uint32_t getNumIdx() const {
        return (_idxFile.data_size()) /sizeof(uint32_t);
    }

    size_t getEnumCount() const;

    size_t getNumValues();
    int32_t getNextWeight() { return _weightReader.readHostOrder(); }
    uint32_t getNextEnum() { return _enumReader.readHostOrder(); }
    bool getEnumerated() const { return _enumerated; }
    uint32_t getNextValueCount();
    int64_t getCreateSerialNum() const { return _createSerialNum; }
    bool getHasLoadData() const { return _hasLoadData; }
    uint32_t getVersion() const { return _version; }
    uint32_t getDocIdLimit() const { return _docIdLimit; }
    const vespalib::GenericHeader &getDatHeader() const {
        return _datFile.header();
    }
protected:
    FileWithHeader _datFile;
private:
    FileWithHeader        _weightFile;
    FileWithHeader        _idxFile;
    FileReader<int32_t>   _weightReader;
    FileReader<uint32_t>  _idxReader;
    FileReader<uint32_t>  _enumReader;
    uint32_t              _currIdx;
    uint64_t              _createSerialNum;
    size_t                _fixedWidth;
    bool                  _enumerated;
    bool                  _hasLoadData;
    uint32_t              _version;
    uint32_t              _docIdLimit;
protected:
    size_t getDataCountHelper(size_t elemSize) const {
        size_t dataSize = _datFile.data_size();
        return dataSize / elemSize;
    }
};

}
