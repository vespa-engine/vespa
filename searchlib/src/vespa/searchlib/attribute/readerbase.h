// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/util/fileutil.h>
#include <cassert>

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
        return (_idxFileSize - _idxHeaderLen) /sizeof(uint32_t);
    }

    size_t getEnumCount() const {
        size_t dataSize(_datFileSize - _datHeaderLen);
        assert((dataSize % sizeof(uint32_t)) == 0);
        return dataSize / sizeof(uint32_t);
    }

    static bool
    extractFileSize(const vespalib::GenericHeader &header, FastOS_FileInterface &file, uint64_t &fileSize);

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
        return _datHeader;
    }
protected:
    std::unique_ptr<FastOS_FileInterface>  _datFile;
private:
    std::unique_ptr<FastOS_FileInterface>  _weightFile;
    std::unique_ptr<FastOS_FileInterface>  _idxFile;
    FileReader<int32_t>   _weightReader;
    FileReader<uint32_t>  _idxReader;
    FileReader<uint32_t>  _enumReader;
    uint32_t              _currIdx;
    uint32_t              _datHeaderLen;
    uint32_t              _idxHeaderLen;
    uint32_t              _weightHeaderLen;
    uint64_t              _createSerialNum;
    size_t                _fixedWidth;
    bool                  _enumerated;
    bool                  _hasLoadData;
    uint32_t              _version;
    uint32_t              _docIdLimit;
    vespalib::FileHeader  _datHeader;
    uint64_t              _datFileSize;
    uint64_t              _idxFileSize;
protected:
    size_t getDataCountHelper(size_t elemSize) const {
        size_t dataSize(_datFileSize - _datHeaderLen);
        return dataSize / elemSize;
    }
};

}
