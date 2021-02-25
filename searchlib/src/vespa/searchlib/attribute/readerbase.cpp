// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributevector.h"
#include "load_utils.h"
#include "readerbase.h"
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/searchlib/util/filesizecalculator.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/size_literals.h>

#include <vespa/log/log.h>
LOG_SETUP(".search.attribute.readerbase");

namespace search {

namespace {

const vespalib::string versionTag = "version";
const vespalib::string docIdLimitTag = "docIdLimit";
const vespalib::string createSerialNumTag = "createSerialNum";

constexpr size_t DIRECTIO_ALIGNMENT(4_Ki);

uint64_t
extractCreateSerialNum(const vespalib::GenericHeader &header)
{
    return (header.hasTag(createSerialNumTag)) ? header.getTag(createSerialNumTag).asInteger() : 0u;
}

}

ReaderBase::ReaderBase(AttributeVector &attr)
    : _datFile(attribute::LoadUtils::openDAT(attr)),
      _weightFile(attr.hasWeightedSetType() ?
                  attribute::LoadUtils::openWeight(attr) : std::unique_ptr<Fast_BufferedFile>()),
      _idxFile(attr.hasMultiValue() ?
               attribute::LoadUtils::openIDX(attr) : std::unique_ptr<Fast_BufferedFile>()),
      _weightReader(*_weightFile),
      _idxReader(*_idxFile),
      _enumReader(*_datFile),
      _currIdx(0),
      _datHeaderLen(0u),
      _idxHeaderLen(0u),
      _weightHeaderLen(0u),
      _createSerialNum(0u),
      _fixedWidth(attr.getFixedWidth()),
      _enumerated(false),
      _hasLoadData(false),
      _version(0),
      _docIdLimit(0),
      _datHeader(DIRECTIO_ALIGNMENT),
      _datFileSize(0),
      _idxFileSize(0)
{
    _datHeaderLen = _datHeader.readFile(*_datFile);
    _datFile->SetPosition(_datHeaderLen);
    if (!attr.headerTypeOK(_datHeader) ||
        !extractFileSize(_datHeader, *_datFile, _datFileSize)) {
        _datFile->Close();
    }
    _createSerialNum = extractCreateSerialNum(_datHeader);
    if (_datHeader.hasTag(versionTag)) {
        _version = _datHeader.getTag(versionTag).asInteger();
    }
    _docIdLimit = _datHeader.getTag(docIdLimitTag).asInteger();
    if (hasIdx()) {
        vespalib::FileHeader idxHeader(DIRECTIO_ALIGNMENT);
        _idxHeaderLen = idxHeader.readFile(*_idxFile);
        _idxFile->SetPosition(_idxHeaderLen);
        if (!attr.headerTypeOK(idxHeader) ||
            !extractFileSize(idxHeader, *_idxFile, _idxFileSize)) {
            _idxFile->Close();
        } else  {
            _currIdx = _idxReader.readHostOrder();
        }
    }
    if (hasWeight()) {
        vespalib::FileHeader weightHeader(DIRECTIO_ALIGNMENT);
        _weightHeaderLen = weightHeader.readFile(*_weightFile);
        _weightFile->SetPosition(_weightHeaderLen);
        if (!attr.headerTypeOK(weightHeader))
            _weightFile->Close();
    }
    if (hasData() && AttributeVector::isEnumerated(_datHeader)) {
        _enumerated = true;
    }
    _hasLoadData = hasData() &&
                   (!attr.hasMultiValue() || hasIdx()) &&
                   (!attr.hasWeightedSetType() || hasWeight());
}

ReaderBase::~ReaderBase() = default;

bool
ReaderBase::hasWeight() const {
    return _weightFile.get() && _weightFile->IsOpened();
}

bool
ReaderBase::hasIdx() const {
    return _idxFile.get() && _idxFile->IsOpened();
}

bool
ReaderBase::hasData() const {
    return _datFile.get() && _datFile->IsOpened();
}

bool
ReaderBase::
extractFileSize(const vespalib::GenericHeader &header,
                FastOS_FileInterface &file, uint64_t &fileSize)
{
    fileSize = file.GetSize();
    return FileSizeCalculator::extractFileSize(header, header.getSize(),
                                               file.GetFileName(), fileSize);
}

void
ReaderBase::rewind()
{
    _datFile->SetPosition(_datHeaderLen);
    _currIdx = 0;
    if (hasIdx()) {
        _idxFile->SetPosition(_idxHeaderLen);
        _currIdx = _idxReader.readHostOrder();
    }
    if (hasWeight()) {
        _weightFile->SetPosition(_weightHeaderLen);
    }
}

size_t
ReaderBase::getNumValues()
{
    if (getEnumerated()) {
       return getEnumCount();
    } else {
       if (_fixedWidth > 0) {
           size_t dataSize(_datFileSize - _datHeaderLen);
           assert((dataSize % _fixedWidth) == 0);
           return dataSize / _fixedWidth;
        } else {
            // TODO. This limits the number of multivalues to 2^32-1
            // This is assert during write, so this should never be a problem here.
            _idxFile->SetPosition(_idxFileSize - 4);
            size_t numValues = _idxReader.readHostOrder();
            rewind();
            return numValues;
        }
    }
}

uint32_t
ReaderBase::getNextValueCount()
{
    uint32_t nextIdx = _idxReader.readHostOrder();
    uint32_t numValues = nextIdx - _currIdx;
    _currIdx = nextIdx;
    return numValues;
}

}
