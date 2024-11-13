// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bitvectordictionary.h"
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/common/fileheadertags.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/fastos/file.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".diskindex.bitvectordictionary");

using search::index::BitVectorDictionaryLookupResult;

namespace search::diskindex {

using namespace tags;

BitVectorDictionary::BitVectorDictionary()
    : _docIdLimit(0u),
      _entries(),
      _vectorSize(0u),
      _datFile(),
      _datHeaderLen(0u)
{ }

BitVectorDictionary::~BitVectorDictionary() = default;

bool
BitVectorDictionary::open(const std::string &pathPrefix,
                          const TuneFileRandRead &tuneFileRead,
                          BitVectorKeyScope scope)
{
    {
        std::string booloccIdxName = pathPrefix + "boolocc" + getBitVectorKeyScopeSuffix(scope);
        FastOS_File idxFile;
        idxFile.OpenReadOnly(booloccIdxName.c_str());
        if (!idxFile.IsOpened()) {
            LOG(warning, "Could not open bitvector idx file '%s'", booloccIdxName.c_str());
            return false;
        }

        vespalib::FileHeader idxHeader;
        uint32_t idxHeaderLen = idxHeader.readFile(idxFile);
        idxFile.SetPosition(idxHeaderLen);
        assert(idxHeader.hasTag(FROZEN));
        assert(idxHeader.hasTag(DOCID_LIMIT));
        assert(idxHeader.hasTag(NUM_KEYS));
        assert(idxHeader.getTag(FROZEN).asInteger() != 0);
        _docIdLimit = idxHeader.getTag(DOCID_LIMIT).asInteger();
        uint32_t numEntries = idxHeader.getTag(NUM_KEYS).asInteger();
        if (idxHeader.hasTag(ENTRY_SIZE)) {
            _vectorSize = idxHeader.getTag(ENTRY_SIZE).asInteger();
        } else {
            constexpr size_t LEGACY_ALIGNMENT = 0x40;
            BitVector::Index bytes = BitVector::numBytes(_docIdLimit);
            _vectorSize = bytes + (-bytes & (LEGACY_ALIGNMENT - 1));
        }

        _entries.resize(numEntries);
        size_t bufSize = sizeof(WordSingleKey) * numEntries;
        assert(idxFile.getSize() >= static_cast<int64_t>(idxHeaderLen + bufSize));
        if (bufSize > 0) {
            ssize_t has_read = idxFile.Read(&_entries[0], bufSize);
            assert(has_read == ssize_t(bufSize));
        }
    }

    std::string booloccDatName = pathPrefix + "boolocc.bdat";
    _datFile = std::make_unique<FastOS_File>();
    _datFile->setFAdviseOptions(tuneFileRead.getAdvise());

    if (tuneFileRead.getWantMemoryMap()) {
        _datFile->enableMemoryMap(tuneFileRead.getMemoryMapFlags());
    } else if (tuneFileRead.getWantDirectIO()) {
        _datFile->EnableDirectIO();
    }
    _datFile->OpenReadOnly(booloccDatName.c_str());
    if (!_datFile->IsOpened()) {
        LOG(warning, "Could not open bitvector dat file '%s'", booloccDatName.c_str());
        return false;
    }
    vespalib::FileHeader datHeader(64);
    _datHeaderLen = datHeader.readFile(*_datFile);
    assert(_datFile->getSize() >= static_cast<int64_t>(_vectorSize * _entries.size() + _datHeaderLen));
    return true;
}

BitVectorDictionaryLookupResult
BitVectorDictionary::lookup(uint64_t wordNum) {
    WordSingleKey key;
    key._wordNum = wordNum;
    auto itr = std::lower_bound(_entries.begin(), _entries.end(), key);
    if (itr == _entries.end() || key < *itr) {
        return {};
    }
    return BitVectorDictionaryLookupResult(itr - _entries.begin());
}

std::unique_ptr<BitVector>
BitVectorDictionary::read_bitvector(BitVectorDictionaryLookupResult lookup_result)
{
    if (!lookup_result.valid()) {
        return {};
    }
    int64_t offset = ((int64_t) _vectorSize) * lookup_result.idx + _datHeaderLen;
    return BitVector::create(_docIdLimit, *_datFile, offset, _entries[lookup_result.idx]._numDocs);
}

}
