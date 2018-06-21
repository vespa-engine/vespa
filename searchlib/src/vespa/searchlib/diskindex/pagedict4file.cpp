// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "pagedict4file.h"
#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/io/fileutil.h>

#include <vespa/log/log.h>
LOG_SETUP(".diskindex.pagedict4file");

namespace {

vespalib::string myPId("PageDict4P.1");
vespalib::string mySPId("PageDict4SP.1");
vespalib::string mySSId("PageDict4SS.1");
vespalib::string emptyId;

void assertOpenWriteOnly(bool ok, const vespalib::string &fileName)
{
    if (!ok) {
        int osError = errno;
        LOG(error, "Could not open %s for write: %s",
            fileName.c_str(),
            vespalib::getOpenErrorString(osError, fileName.c_str()).c_str());
        LOG_ABORT("should not be reached");
    }
}

}

using search::common::FileHeaderContext;
using search::index::PostingListParams;
using vespalib::getLastErrorString;

namespace search::diskindex {

namespace {

const uint32_t headerAlign = 4096;

}

PageDict4FileSeqRead::PageDict4FileSeqRead()
    : _pReader(NULL),
      _ssReader(NULL),
      _ssd(),
      _ssReadContext(_ssd),
      _ssfile(),
      _spd(),
      _spReadContext(_spd),
      _spfile(),
      _pd(),
      _pReadContext(_pd),
      _pfile(),
      _ssFileBitSize(0u),
      _spFileBitSize(0u),
      _pFileBitSize(0u),
      _ssHeaderLen(0u),
      _spHeaderLen(0u),
      _pHeaderLen(0u),
      _ssCompleted(false),
      _spCompleted(false),
      _pCompleted(false),
      _wordNum(0u)
{
    _ssd.setReadContext(&_ssReadContext);
    _spd.setReadContext(&_spReadContext);
    _pd.setReadContext(&_pReadContext);
}


PageDict4FileSeqRead::~PageDict4FileSeqRead()
{
    delete _pReader;
    delete _ssReader;
}


void
PageDict4FileSeqRead::readSSHeader()
{
    DC &ssd = _ssd;

    vespalib::FileHeader header;
    uint32_t headerLen = ssd.readHeader(header, _ssfile.getSize());
    assert(header.hasTag("frozen"));
    assert(header.hasTag("fileBitSize"));
    assert(header.hasTag("format.0"));
    assert(!header.hasTag("format.1"));
    assert(header.hasTag("numWordIds"));
    assert(header.hasTag("avgBitsPerDoc"));
    assert(header.hasTag("minChunkDocs"));
    assert(header.hasTag("docIdLimit"));
    assert(header.hasTag("endian"));
    _ssCompleted = header.getTag("frozen").asInteger() != 0;
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
PageDict4FileSeqRead::readSPHeader()
{
    DC &spd = _spd;

    vespalib::FileHeader header;
    uint32_t headerLen = spd.readHeader(header, _spfile.getSize());
    assert(header.hasTag("frozen"));
    assert(header.hasTag("fileBitSize"));
    assert(header.hasTag("format.0"));
    assert(!header.hasTag("format.1"));
    assert(header.hasTag("endian"));
    _spCompleted = header.getTag("frozen").asInteger() != 0;
    _spFileBitSize = header.getTag("fileBitSize").asInteger();
    assert(header.getTag("format.0").asString() == mySPId);
    assert(header.getTag("endian").asString() == "big");
    spd.smallAlign(64);
    uint32_t minHeaderLen = header.getSize();
    minHeaderLen += (-minHeaderLen & 7);
    assert(headerLen >= minHeaderLen);
    assert(spd.getReadOffset() == headerLen * 8);
    _spHeaderLen = headerLen;
}


void
PageDict4FileSeqRead::readPHeader()
{
    DC &pd = _pd;

    vespalib::FileHeader header;
    uint32_t headerLen = pd.readHeader(header, _pfile.getSize());
    assert(header.hasTag("frozen"));
    assert(header.hasTag("fileBitSize"));
    assert(header.hasTag("format.0"));
    assert(!header.hasTag("format.1"));
    assert(header.hasTag("endian"));
    _pCompleted = header.getTag("frozen").asInteger() != 0;
    _pFileBitSize = header.getTag("fileBitSize").asInteger();
    assert(header.getTag("format.0").asString() == myPId);
    assert(header.getTag("endian").asString() == "big");
    pd.smallAlign(64);
    uint32_t minHeaderLen = header.getSize();
    minHeaderLen += (-minHeaderLen & 7);
    assert(headerLen >= minHeaderLen);
    assert(pd.getReadOffset() == headerLen * 8);
    _pHeaderLen = headerLen;
}


void
PageDict4FileSeqRead::readWord(vespalib::string &word,
                               uint64_t &wordNum,
                               PostingListCounts &counts)
{
    // Map to external ids and filter by what's present in the schema.
    uint64_t checkWordNum = 0;
    _pReader->readCounts(word, checkWordNum, counts);
    if (checkWordNum != noWordNumHigh()) {
        wordNum = ++_wordNum;
        assert(wordNum == checkWordNum);
    } else {
        wordNum = noWordNumHigh();
        counts.clear();
    }
}


bool
PageDict4FileSeqRead::open(const vespalib::string &name,
                           const TuneFileSeqRead &tuneFileRead)
{
    if (tuneFileRead.getWantDirectIO()) {
        _ssfile.EnableDirectIO();
        _spfile.EnableDirectIO();
        _pfile.EnableDirectIO();
    }

    vespalib::string pname = name + ".pdat";
    vespalib::string spname = name + ".spdat";
    vespalib::string ssname = name + ".ssdat";

    if (!_ssfile.OpenReadOnly(ssname.c_str())) {
        LOG(error, "could not open %s: %s",
            _ssfile.GetFileName(), getLastErrorString().c_str());
        return false;
    }
    if (!_spfile.OpenReadOnly(spname.c_str())) {
        LOG(error, "could not open %s: %s",
            _spfile.GetFileName(), getLastErrorString().c_str());
        return false;
    }
    if (!_pfile.OpenReadOnly(pname.c_str())) {
        LOG(error, "could not open %s: %s",
            _pfile.GetFileName(), getLastErrorString().c_str());
        return false;
    }

    _spReadContext.setFile(&_spfile);
    _spReadContext.setFileSize(_spfile.GetSize());
    _spReadContext.allocComprBuf(65536u, 32768u);
    _spd.emptyBuffer(0);

    _pReadContext.setFile(&_pfile);
    _pReadContext.setFileSize(_pfile.GetSize());
    _pReadContext.allocComprBuf(65536u, 32768u);
    _pd.emptyBuffer(0);

    uint64_t fileSize = _ssfile.GetSize();
    _ssReadContext.setFile(&_ssfile);
    _ssReadContext.setFileSize(fileSize);
    _ssReadContext.allocComprBuf((fileSize + sizeof(uint64_t) - 1) /
                                 sizeof(uint64_t),
                                 32768u);
    _ssd.emptyBuffer(0);

    _ssReadContext.readComprBuffer();
    assert(_ssReadContext.getBufferEndFilePos() >= fileSize);
    readSSHeader();
    _spReadContext.readComprBuffer();
    readSPHeader();
    _pReadContext.readComprBuffer();
    readPHeader();

    _ssReader = new SSReader(_ssReadContext,
                             _ssHeaderLen,
                             _ssFileBitSize,
                             _spHeaderLen,
                             _spFileBitSize,
                             _pHeaderLen,
                             _pFileBitSize);

    // Instantiate helper class for reading
    _pReader = new Reader(*_ssReader,
                          _spd,
                          _pd);

    _ssReader->setup(_ssd);
    _pReader->setup();
    _wordNum = 0;

    return true;
}


bool
PageDict4FileSeqRead::close()
{
    delete _pReader;
    delete _ssReader;
    _pReader = NULL;
    _ssReader = NULL;

    _ssReadContext.dropComprBuf();
    _spReadContext.dropComprBuf();
    _pReadContext.dropComprBuf();
    _ssReadContext.setFile(NULL);
    _spReadContext.setFile(NULL);
    _pReadContext.setFile(NULL);
    _ssfile.Close();
    _spfile.Close();
    _pfile.Close();
    return true;
}


void
PageDict4FileSeqRead::getParams(PostingListParams &params)
{
    params.clear();
    params.set("avgBitsPerDoc", _ssd._avgBitsPerDoc);
    params.set("minChunkDocs", _ssd._minChunkDocs);
    params.set("docIdLimit", _ssd._docIdLimit);
    params.set("numWordIds", _ssd._numWordIds);
    params.set("numCounts", _ssd._numWordIds);
}


PageDict4FileSeqWrite::PageDict4FileSeqWrite()
    : _pWriter(NULL),
      _spWriter(NULL),
      _ssWriter(NULL),
      _pe(),
      _pWriteContext(_pe),
      _pfile(),
      _spe(),
      _spWriteContext(_spe),
      _spfile(),
      _sse(),
      _ssWriteContext(_sse),
      _ssfile(),
      _pHeaderLen(0),
      _spHeaderLen(0),
      _ssHeaderLen(0)
{
    _pe.setWriteContext(&_pWriteContext);
    _spe.setWriteContext(&_spWriteContext);
    _sse.setWriteContext(&_ssWriteContext);
}


PageDict4FileSeqWrite::~PageDict4FileSeqWrite()
{
    delete _pWriter;
    delete _spWriter;
    delete _ssWriter;
}


void
PageDict4FileSeqWrite::writeWord(const vespalib::stringref &word,
                                 const PostingListCounts &counts)
{
    _pWriter->addCounts(word, counts);
}


bool
PageDict4FileSeqWrite::open(const vespalib::string &name,
                            const TuneFileSeqWrite &tuneFileWrite,
                            const FileHeaderContext &fileHeaderContext)
{
    assert(_pWriter == NULL);
    assert(_spWriter == NULL);
    assert(_ssWriter == NULL);

    vespalib::string pname = name + ".pdat";
    vespalib::string spname = name + ".spdat";
    vespalib::string ssname = name + ".ssdat";

    if (tuneFileWrite.getWantSyncWrites()) {
        _pfile.EnableSyncWrites();
        _spfile.EnableSyncWrites();
        _ssfile.EnableSyncWrites();
    }
    if (tuneFileWrite.getWantDirectIO()) {
        _pfile.EnableDirectIO();
        _spfile.EnableDirectIO();
        _ssfile.EnableDirectIO();
    }
    bool ok = _pfile.OpenWriteOnly(pname.c_str());
    assertOpenWriteOnly(ok, pname);
    _pWriteContext.setFile(&_pfile);

    ok = _spfile.OpenWriteOnly(spname.c_str());
    assertOpenWriteOnly(ok, spname);
    _spWriteContext.setFile(&_spfile);

    ok = _ssfile.OpenWriteOnly(ssname.c_str());
    assertOpenWriteOnly(ok, ssname);
    _ssWriteContext.setFile(&_ssfile);

    _pWriteContext.allocComprBuf(65536u, 32768u);
    _spWriteContext.allocComprBuf(65536u, 32768u);
    _ssWriteContext.allocComprBuf(65536u, 32768u);

    uint64_t pFileSize = _pfile.GetSize();
    uint64_t spFileSize = _spfile.GetSize();
    uint64_t ssFileSize = _ssfile.GetSize();
    uint64_t pBufferStartFilePos = _pWriteContext.getBufferStartFilePos();
    uint64_t spBufferStartFilePos = _spWriteContext.getBufferStartFilePos();
    uint64_t ssBufferStartFilePos = _ssWriteContext.getBufferStartFilePos();
    assert(pFileSize >= pBufferStartFilePos);
    assert(spFileSize >= spBufferStartFilePos);
    assert(ssFileSize >= ssBufferStartFilePos);
    (void) pFileSize;
    (void) spFileSize;
    (void) ssFileSize;
    _pfile.SetSize(pBufferStartFilePos);
    _spfile.SetSize(spBufferStartFilePos);
    _ssfile.SetSize(ssBufferStartFilePos);
    assert(pBufferStartFilePos == static_cast<uint64_t>(_pfile.GetPosition()));
    assert(spBufferStartFilePos ==
           static_cast<uint64_t>(_spfile.GetPosition()));
    assert(ssBufferStartFilePos ==
           static_cast<uint64_t>(_ssfile.GetPosition()));

    _pe.setupWrite(_pWriteContext);
    _spe.setupWrite(_spWriteContext);
    _sse.setupWrite(_ssWriteContext);
    assert(_pe.getWriteOffset() == 0);
    assert(_spe.getWriteOffset() == 0);
    assert(_sse.getWriteOffset() == 0);
    _spe.copyParams(_sse);
    _pe.copyParams(_sse);
    // Write initial file headers
    makePHeader(fileHeaderContext);
    makeSPHeader(fileHeaderContext);
    makeSSHeader(fileHeaderContext);

    _ssWriter = new SSWriter(_sse);
    _spWriter = new SPWriter(*_ssWriter, _spe);
    _pWriter = new PWriter(*_spWriter, _pe);
    _spWriter->setup();
    _pWriter->setup();

    return true;
}


bool
PageDict4FileSeqWrite::close()
{
    _pWriter->flush();
    uint64_t usedPBits = _pe.getWriteOffset();
    uint64_t usedSPBits = _spe.getWriteOffset();
    uint64_t usedSSBits = _sse.getWriteOffset();
    _pe.flush();
    _pWriteContext.writeComprBuffer(true);
    _spe.flush();
    _spWriteContext.writeComprBuffer(true);
    _sse.flush();
    _ssWriteContext.writeComprBuffer(true);

    _pWriteContext.dropComprBuf();
    _pfile.Sync();
    _pfile.Close();
    _pWriteContext.setFile(NULL);
    _spWriteContext.dropComprBuf();
    _spfile.Sync();
    _spfile.Close();
    _spWriteContext.setFile(NULL);
    _ssWriteContext.dropComprBuf();
    _ssfile.Sync();
    _ssfile.Close();
    _ssWriteContext.setFile(NULL);

    // Update file headers
    updatePHeader(usedPBits);
    updateSPHeader(usedSPBits);
    updateSSHeader(usedSSBits);

    delete _pWriter;
    delete _spWriter;
    delete _ssWriter;
    _pWriter = NULL;
    _spWriter = NULL;
    _ssWriter = NULL;

    return true;
}


void
PageDict4FileSeqWrite::writeSSSubHeader(vespalib::GenericHeader &header)
{
    SSEC &e = _sse;
    typedef vespalib::GenericHeader::Tag Tag;
    header.putTag(Tag("numWordIds", e._numWordIds));
    header.putTag(Tag("avgBitsPerDoc", e._avgBitsPerDoc));
    header.putTag(Tag("minChunkDocs", e._minChunkDocs));
    header.putTag(Tag("docIdLimit", e._docIdLimit));
}


void
PageDict4FileSeqWrite::makePHeader(const FileHeaderContext &fileHeaderContext)
{
    PEC &e = _pe;
    ComprFileWriteContext &wc = _pWriteContext;

    // subheader only written to SS file.

    typedef vespalib::GenericHeader::Tag Tag;
    vespalib::FileHeader header(headerAlign);

    fileHeaderContext.addTags(header, _pfile.GetFileName());
    header.putTag(Tag("frozen", 0));
    header.putTag(Tag("fileBitSize", 0));
    header.putTag(Tag("format.0", myPId));
    header.putTag(Tag("endian", "big"));
    header.putTag(Tag("desc", "Dictionary page file"));
    e.setupWrite(wc);
    e.writeHeader(header);
    e.smallAlign(64);
    e.flush();
    uint32_t headerLen = header.getSize();
    headerLen += (-headerLen & 7);
    assert(e.getWriteOffset() == headerLen * 8);
    assert((e.getWriteOffset() & 63) == 0); // Header must be word aligned
    if (_pHeaderLen != 0) {
        assert(_pHeaderLen == headerLen);
    }
    _pHeaderLen = headerLen;
}


void
PageDict4FileSeqWrite::makeSPHeader(const FileHeaderContext &fileHeaderContext)
{
    SPEC &e = _spe;
    ComprFileWriteContext &wc = _spWriteContext;

    // subheader only written to SS file.

    typedef vespalib::GenericHeader::Tag Tag;
    vespalib::FileHeader header(headerAlign);

    fileHeaderContext.addTags(header, _spfile.GetFileName());
    header.putTag(Tag("frozen", 0));
    header.putTag(Tag("fileBitSize", 0));
    header.putTag(Tag("format.0", mySPId));
    header.putTag(Tag("endian", "big"));
    header.putTag(Tag("desc", "Dictionary sparse page file"));
    e.setupWrite(wc);
    e.writeHeader(header);
    e.smallAlign(64);
    e.flush();
    uint32_t headerLen = header.getSize();
    headerLen += (-headerLen & 7);
    assert(e.getWriteOffset() == headerLen * 8);
    assert((e.getWriteOffset() & 63) == 0); // Header must be word aligned
    if (_spHeaderLen != 0) {
        assert(_spHeaderLen == headerLen);
    }
    _spHeaderLen = headerLen;
}


void
PageDict4FileSeqWrite::makeSSHeader(const FileHeaderContext &fileHeaderContext)
{
    SSEC &e = _sse;
    ComprFileWriteContext &wc = _ssWriteContext;

    typedef vespalib::GenericHeader::Tag Tag;
    vespalib::FileHeader header(headerAlign);

    fileHeaderContext.addTags(header, _ssfile.GetFileName());
    header.putTag(Tag("frozen", 0));
    header.putTag(Tag("fileBitSize", 0));
    header.putTag(Tag("format.0", mySSId));
    header.putTag(Tag("endian", "big"));
    header.putTag(Tag("desc", "Dictionary sparse sparse file"));
    writeSSSubHeader(header);

    e.setupWrite(wc);
    e.writeHeader(header);
    e.smallAlign(64);
    e.flush();
    uint32_t headerLen = header.getSize();
    headerLen += (-headerLen & 7);
    assert(e.getWriteOffset() == headerLen * 8);
    assert((e.getWriteOffset() & 63) == 0); // Header must be word aligned
    if (_ssHeaderLen != 0) {
        assert(_ssHeaderLen == headerLen);
    }
    _ssHeaderLen = headerLen;
}


void
PageDict4FileSeqWrite::updatePHeader(uint64_t fileBitSize)
{
    vespalib::FileHeader h(headerAlign);
    FastOS_File f;
    f.OpenReadWrite(_pfile.GetFileName());
    h.readFile(f);
    FileHeaderContext::setFreezeTime(h);
    typedef vespalib::GenericHeader::Tag Tag;
    h.putTag(Tag("frozen", 1));
    h.putTag(Tag("fileBitSize", fileBitSize));
    h.rewriteFile(f);
    f.Sync();
    f.Close();
}


void
PageDict4FileSeqWrite::updateSPHeader(uint64_t fileBitSize)
{
    vespalib::FileHeader h(headerAlign);
    FastOS_File f;
    f.OpenReadWrite(_spfile.GetFileName());
    h.readFile(f);
    FileHeaderContext::setFreezeTime(h);
    typedef vespalib::GenericHeader::Tag Tag;
    h.putTag(Tag("frozen", 1));
    h.putTag(Tag("fileBitSize", fileBitSize));
    h.rewriteFile(f);
    f.Sync();
    f.Close();
}


void
PageDict4FileSeqWrite::updateSSHeader(uint64_t fileBitSize)
{
    vespalib::FileHeader h(headerAlign);
    FastOS_File f;
    f.OpenReadWrite(_ssfile.GetFileName());
    h.readFile(f);
    FileHeaderContext::setFreezeTime(h);
    typedef vespalib::GenericHeader::Tag Tag;
    h.putTag(Tag("frozen", 1));
    h.putTag(Tag("fileBitSize", fileBitSize));
    uint64_t wordNum = _pWriter->getWordNum();
    assert(wordNum <= _sse._numWordIds);
    h.putTag(Tag("numWordIds", wordNum));
    h.rewriteFile(f);
    f.Sync();
    f.Close();
}


void
PageDict4FileSeqWrite::setParams(const PostingListParams &params)
{
    params.get("avgBitsPerDoc", _sse._avgBitsPerDoc);
    params.get("minChunkDocs", _sse._minChunkDocs);
    params.get("docIdLimit", _sse._docIdLimit);
    params.get("numWordIds", _sse._numWordIds);
    _spe.copyParams(_sse);
    _pe.copyParams(_sse);
}


void
PageDict4FileSeqWrite::getParams(PostingListParams &params)
{
    params.clear();
    params.set("avgBitsPerDoc", _sse._avgBitsPerDoc);
    params.set("minChunkDocs", _sse._minChunkDocs);
    params.set("docIdLimit", _sse._docIdLimit);
    params.set("numWordIds", _sse._numWordIds);
}

}
