// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "pagedict4randread.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/fastos/file.h>

#include <vespa/log/log.h>
LOG_SETUP(".diskindex.pagedict4randread");

namespace {

vespalib::string myPId("PageDict4P.1");
vespalib::string mySPId("PageDict4SP.1");
vespalib::string mySSId("PageDict4SS.1");

}

using vespalib::getLastErrorString;

namespace search::diskindex {

PageDict4RandRead::PageDict4RandRead()
    : DictionaryFileRandRead(),
      _ssReader(),
      _ssd(),
      _ssReadContext(_ssd),
      _ssfile(std::make_unique<FastOS_File>()),
      _spfile(std::make_unique<FastOS_File>()),
      _pfile(std::make_unique<FastOS_File>()),
      _ssFileBitSize(0u),
      _spFileBitSize(0u),
      _pFileBitSize(0u),
      _ssHeaderLen(0u),
      _spHeaderLen(0u),
      _pHeaderLen(0u)
{
    _ssd.setReadContext(&_ssReadContext);
}


PageDict4RandRead::~PageDict4RandRead() = default;


void
PageDict4RandRead::readSSHeader()
{
    DC &ssd = _ssd;

    vespalib::FileHeader header;
    uint32_t headerLen = ssd.readHeader(header, _ssfile->getSize());
    assert(header.hasTag("frozen"));
    assert(header.hasTag("fileBitSize"));
    assert(header.hasTag("format.0"));
    assert(!header.hasTag("format.1"));
    assert(header.hasTag("numWordIds"));
    assert(header.hasTag("avgBitsPerDoc"));
    assert(header.hasTag("minChunkDocs"));
    assert(header.hasTag("docIdLimit"));
    assert(header.hasTag("endian"));
    assert(header.getTag("frozen").asInteger() != 0);
    _ssFileBitSize = header.getTag("fileBitSize").asInteger();
    assert(header.getTag("format.0").asString() == mySSId);
    ssd._numWordIds = header.getTag("numWordIds").asInteger();
    ssd._avgBitsPerDoc = header.getTag("avgBitsPerDoc").asInteger();
    ssd._minChunkDocs = header.getTag("minChunkDocs").asInteger();
    ssd._docIdLimit = header.getTag("docIdLimit").asInteger();

    assert(header.getTag("endian").asString() == "big");
    ssd.smallAlign(64);
    uint32_t minHeaderLen = header.getSize();
    minHeaderLen += (-minHeaderLen & 7);
    assert(headerLen >= minHeaderLen);
    assert(ssd.getReadOffset() == headerLen * 8);
    _ssHeaderLen = headerLen;
}


void
PageDict4RandRead::readSPHeader()
{
    DC d;
    ComprFileReadContext rc(d);

    d.setReadContext(&rc);
    rc.setFile(_spfile.get());
    rc.setFileSize(_spfile->GetSize());
    rc.allocComprBuf(512, 32768u);
    d.emptyBuffer(0);
    rc.readComprBuffer();

    vespalib::FileHeader header;
    uint32_t headerLen = d.readHeader(header, _spfile->getSize());
    assert(header.hasTag("frozen"));
    assert(header.hasTag("fileBitSize"));
    assert(header.hasTag("format.0"));
    assert(!header.hasTag("format.1"));
    assert(header.hasTag("endian"));
    assert(header.getTag("frozen").asInteger() != 0);
    _spFileBitSize = header.getTag("fileBitSize").asInteger();
    assert(header.getTag("format.0").asString() == mySPId);
    assert(header.getTag("endian").asString() == "big");
    d.smallAlign(64);
    uint32_t minHeaderLen = header.getSize();
    minHeaderLen += (-minHeaderLen & 7);
    assert(headerLen >= minHeaderLen);
    assert(d.getReadOffset() == headerLen * 8);
    _spHeaderLen = headerLen;
}


void
PageDict4RandRead::readPHeader()
{
    DC d;
    ComprFileReadContext rc(d);

    d.setReadContext(&rc);
    rc.setFile(_pfile.get());
    rc.setFileSize(_pfile->GetSize());
    rc.allocComprBuf(512, 32768u);
    d.emptyBuffer(0);
    rc.readComprBuffer();

    vespalib::FileHeader header;
    uint32_t headerLen = d.readHeader(header, _pfile->getSize());
    assert(header.hasTag("frozen"));
    assert(header.hasTag("fileBitSize"));
    assert(header.hasTag("format.0"));
    assert(!header.hasTag("format.1"));
    assert(header.hasTag("endian"));
    assert(header.getTag("frozen").asInteger() != 0);
    _pFileBitSize = header.getTag("fileBitSize").asInteger();
    assert(header.getTag("format.0").asString() == myPId);
    assert(header.getTag("endian").asString() == "big");
    d.smallAlign(64);
    uint32_t minHeaderLen = header.getSize();
    minHeaderLen += (-minHeaderLen & 7);
    assert(headerLen >= minHeaderLen);
    assert(d.getReadOffset() == headerLen * 8);
    _pHeaderLen = headerLen;
}


bool
PageDict4RandRead::lookup(vespalib::stringref word,
                          uint64_t &wordNum,
                          PostingListOffsetAndCounts &offsetAndCounts)
{
    SSLookupRes ssRes(_ssReader->lookup(word));
    if (!ssRes._res) {
        offsetAndCounts._offset = ssRes._l6StartOffset._fileOffset;
        offsetAndCounts._accNumDocs = ssRes._l6StartOffset._accNumDocs;
        wordNum = ssRes._l6WordNum; // XXX ?
        offsetAndCounts._counts.clear();
        return false;
    }

    if (ssRes._overflow) {
        offsetAndCounts._offset = ssRes._startOffset._fileOffset;
        offsetAndCounts._accNumDocs = ssRes._startOffset._accNumDocs;
        wordNum = ssRes._l6WordNum;
        offsetAndCounts._counts = ssRes._counts;
        return true;
    } else {
        SPLookupRes spRes;
        size_t pageSize = PageDict4PageParams::getPageByteSize();
        const char *spData = static_cast<const char *>(_spfile->MemoryMapPtr(0));
        spRes.lookup(*_ssReader,
                     spData + pageSize * ssRes._sparsePageNum,
                     word,
                     ssRes._l6Word,
                     ssRes._lastWord,
                     ssRes._l6StartOffset,
                     ssRes._l6WordNum,
                     ssRes._pageNum);

        PLookupRes pRes;
        const char *pData = static_cast<const char *>(_pfile->MemoryMapPtr(0));
        pRes.lookup(*_ssReader,
                    pData + pageSize * spRes._pageNum,
                    word,
                    spRes._l3Word,
                    spRes._lastWord,
                    spRes._l3StartOffset,
                    spRes._l3WordNum);
        offsetAndCounts._offset = pRes._startOffset._fileOffset;
        offsetAndCounts._accNumDocs = pRes._startOffset._accNumDocs;
        wordNum = pRes._wordNum;
        if (!pRes._res) {
            offsetAndCounts._counts.clear();
            return false;
        }
        offsetAndCounts._counts = pRes._counts;
        return true;
    }
}


bool
PageDict4RandRead::open(const vespalib::string &name,
                        const TuneFileRandRead &tuneFileRead)
{
    vespalib::string pname = name + ".pdat";
    vespalib::string spname = name + ".spdat";
    vespalib::string ssname = name + ".ssdat";

    int mmapFlags(tuneFileRead.getMemoryMapFlags());
    _ssfile->enableMemoryMap(mmapFlags);
    _spfile->enableMemoryMap(mmapFlags);
    _pfile->enableMemoryMap(mmapFlags);

    int fadvise = tuneFileRead.getAdvise();
    _ssfile->setFAdviseOptions(fadvise);
    _spfile->setFAdviseOptions(fadvise);
    _pfile->setFAdviseOptions(fadvise);

    if (!_ssfile->OpenReadOnly(ssname.c_str())) {
        LOG(error, "could not open %s: %s", _ssfile->GetFileName(), getLastErrorString().c_str());
        return false;
    }
    if (!_spfile->OpenReadOnly(spname.c_str())) {
        LOG(error, "could not open %s: %s", _spfile->GetFileName(), getLastErrorString().c_str());
        return false;
    }
    if (!_pfile->OpenReadOnly(pname.c_str())) {
        LOG(error, "could not open %s: %s", _pfile->GetFileName(), getLastErrorString().c_str());
        return false;
    }

    uint64_t fileSize = _ssfile->GetSize();
    _ssReadContext.setFile(_ssfile.get());
    _ssReadContext.setFileSize(fileSize);
    _ssReadContext.allocComprBuf((fileSize + sizeof(uint64_t) - 1) / sizeof(uint64_t), 32768u);
    _ssd.emptyBuffer(0);
    _ssReadContext.readComprBuffer();
    assert(_ssReadContext.getBufferEndFilePos() >= fileSize);

    readSSHeader();
    readSPHeader();
    readPHeader();

    _ssReader = std::make_unique<SSReader>(_ssReadContext, _ssHeaderLen, _ssFileBitSize, _spHeaderLen,
                                           _spFileBitSize, _pHeaderLen, _pFileBitSize);
    _ssReader->setup(_ssd);

    return true;
}


bool
PageDict4RandRead::close()
{
    _ssReader.reset();

    _ssReadContext.dropComprBuf();
    _ssReadContext.setFile(nullptr);
    bool ok = _ssfile->Close();
    ok &= _spfile->Close();
    ok &= _pfile->Close();
    return ok;
}

uint64_t
PageDict4RandRead::getNumWordIds() const
{
    return _ssd._numWordIds;
}

}
