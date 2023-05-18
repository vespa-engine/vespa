// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "readerbase.h"
#include "attributevector.h"
#include "load_utils.h"
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/searchlib/util/filesizecalculator.h>
#include <vespa/vespalib/util/size_literals.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".search.attribute.readerbase");

namespace search {

namespace {

const vespalib::string versionTag = "version";
const vespalib::string docIdLimitTag = "docIdLimit";
const vespalib::string createSerialNumTag = "createSerialNum";

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
      _weightReader(_weightFile.valid() ? &_weightFile.file() : nullptr),
      _idxReader(_idxFile.valid() ? &_idxFile.file() : nullptr),
      _enumReader(&_datFile.file()),
      _currIdx(0),
      _createSerialNum(0u),
      _fixedWidth(attr.getFixedWidth()),
      _enumerated(false),
      _hasLoadData(false),
      _version(0),
      _docIdLimit(0)
{
    if (!attr.headerTypeOK(_datFile.header())) {
        _datFile.close();
    }
    _createSerialNum = extractCreateSerialNum(_datFile.header());
    if (_datFile.header().hasTag(versionTag)) {
        _version = _datFile.header().getTag(versionTag).asInteger();
    }
    _docIdLimit = _datFile.header().getTag(docIdLimitTag).asInteger();
    if (hasIdx()) {
        if (!attr.headerTypeOK(_idxFile.header())) {
            _idxFile.close();
        } else  {
            _currIdx = _idxReader.readHostOrder();
        }
    }
    if (hasWeight()) {
        if (!attr.headerTypeOK(_weightFile.header())) {
            _weightFile.close();
        }
    }
    if (hasData() && AttributeVector::isEnumerated(_datFile.header())) {
        _enumerated = true;
    }
    _hasLoadData = hasData() &&
                   (!attr.hasMultiValue() || hasIdx()) &&
                   (!attr.hasWeightedSetType() || hasWeight());
}

ReaderBase::~ReaderBase() = default;

size_t
ReaderBase::getEnumCount() const {
    size_t dataSize = _datFile.data_size();
    assert((dataSize % sizeof(uint32_t)) == 0);
    return dataSize / sizeof(uint32_t);
}

bool
ReaderBase::hasWeight() const {
    return _weightFile.valid();
}

bool
ReaderBase::hasIdx() const {
    return _idxFile.valid();
}

bool
ReaderBase::hasData() const {
    return _datFile.valid();
}

void
ReaderBase::rewind()
{
    _datFile.rewind();
    _currIdx = 0;
    if (hasIdx()) {
        _idxFile.rewind();
        _currIdx = _idxReader.readHostOrder();
    }
    if (hasWeight()) {
        _weightFile.rewind();
    }
}

size_t
ReaderBase::getNumValues()
{
    if (getEnumerated()) {
       return getEnumCount();
    } else {
       if (_fixedWidth > 0) {
           size_t dataSize = _datFile.data_size();
           assert((dataSize % _fixedWidth) == 0);
           return dataSize / _fixedWidth;
        } else {
            // TODO. This limits the number of multivalues to 2^32-1
            // This is assert during write, so this should never be a problem here.
            _idxFile.file().SetPosition(_idxFile.file_size() - 4);
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
