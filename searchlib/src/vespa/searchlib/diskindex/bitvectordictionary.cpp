// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bitvectordictionary.h"
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/fastos/file.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".diskindex.bitvectordictionary");

namespace search::diskindex {

BitVectorDictionary::BitVectorDictionary()
    : _docIdLimit(),
      _entries(),
      _vectorSize(),
      _datFile(),
      _datHeaderLen(0u)
{ }


BitVectorDictionary::~BitVectorDictionary() = default;


bool
BitVectorDictionary::open(const vespalib::string &pathPrefix,
                          const TuneFileRandRead &tuneFileRead,
                          BitVectorKeyScope scope)
{
    vespalib::string booloccIdxName = pathPrefix + "boolocc" +
                                      getBitVectorKeyScopeSuffix(scope);
    vespalib::string booloccDatName = pathPrefix + "boolocc.bdat";
    {
        FastOS_File idxFile;
        idxFile.OpenReadOnly(booloccIdxName.c_str());
        if (!idxFile.IsOpened()) {
            LOG(warning, "Could not open bitvector idx file '%s'",
                booloccIdxName.c_str());
            return false;
        }

        vespalib::FileHeader idxHeader;
        uint32_t idxHeaderLen = idxHeader.readFile(idxFile);
        idxFile.SetPosition(idxHeaderLen);
        assert(idxHeader.hasTag("frozen"));
        assert(idxHeader.hasTag("docIdLimit"));
        assert(idxHeader.hasTag("numKeys"));
        assert(idxHeader.getTag("frozen").asInteger() != 0);
        _docIdLimit = idxHeader.getTag("docIdLimit").asInteger();
        uint32_t numEntries = idxHeader.getTag("numKeys").asInteger();

        _entries.resize(numEntries);
        size_t bufSize = sizeof(WordSingleKey) * numEntries;
        assert(idxFile.GetSize() >= static_cast<int64_t>(idxHeaderLen + bufSize));
        if (bufSize > 0) {
            ssize_t has_read = idxFile.Read(&_entries[0], bufSize);
            assert(has_read == ssize_t(bufSize));
        }
    }

    _vectorSize = BitVector::getFileBytes(_docIdLimit);
    _datFile = std::make_unique<FastOS_File>();
    _datFile->setFAdviseOptions(tuneFileRead.getAdvise());

    if (tuneFileRead.getWantMemoryMap()) {
        _datFile->enableMemoryMap(tuneFileRead.getMemoryMapFlags());
    } else if (tuneFileRead.getWantDirectIO()) {
        _datFile->EnableDirectIO();
    }
    _datFile->OpenReadOnly(booloccDatName.c_str());
    if (!_datFile->IsOpened()) {
        LOG(warning, "Could not open bitvector dat file '%s'",
            booloccDatName.c_str());
        return false;
    }
    vespalib::FileHeader datHeader(64);
    _datHeaderLen = datHeader.readFile(*_datFile);
    assert(_datFile->GetSize() >=
           static_cast<int64_t>(_vectorSize * _entries.size() + _datHeaderLen));
    return true;
}


BitVector::UP
BitVectorDictionary::lookup(uint64_t wordNum)
{
    WordSingleKey key;
    key._wordNum = wordNum;
    std::vector<WordSingleKey>::const_iterator itr =
        std::lower_bound(_entries.begin(), _entries.end(), key);
    if (itr == _entries.end() || key < *itr) {
        return BitVector::UP();
    }
    int64_t pos = &*itr - &_entries[0];
    return BitVector::create(_docIdLimit, *_datFile,
                                 ((int64_t) _vectorSize) * pos + _datHeaderLen,
                                 itr->_numDocs);
}

}
