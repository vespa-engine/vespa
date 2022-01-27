// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "pagedict4file.h"
#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/searchlib/util/file_settings.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/size_literals.h>

#include <vespa/log/log.h>
LOG_SETUP(".diskindex.pagedict4file");

namespace {

vespalib::string myPId("PageDict4P.1");
vespalib::string mySPId("PageDict4SP.1");
vespalib::string mySSId("PageDict4SS.1");

void
assertOpenWriteOnly(bool ok, const vespalib::string &fileName)
{
    if (!ok) {
        int osError = errno;
        LOG(error, "Could not open %s for write: %s",
            fileName.c_str(),
            vespalib::getOpenErrorString(osError, fileName.c_str()).c_str());
        LOG_ABORT("should not be reached");
    }
}

int64_t
getBitSizeAndAssertHeaders(const vespalib::FileHeader & header, vespalib::stringref id) {
    assert(header.hasTag("frozen"));
    assert(header.hasTag("fileBitSize"));
    assert(header.hasTag("format.0"));
    assert(!header.hasTag("format.1"));
    assert(header.hasTag("endian"));
    assert(header.getTag("frozen").asInteger() != 0);
    assert(header.getTag("endian").asString() == "big");
    assert(header.getTag("format.0").asString() == id);
    return header.getTag("fileBitSize").asInteger();
}

}

using search::common::FileHeaderContext;
using search::index::PostingListParams;
using vespalib::getLastErrorString;

namespace search::diskindex {

struct PageDict4FileSeqRead::DictFileReadContext {
    DictFileReadContext(vespalib::stringref id, const vespalib::string & name, const TuneFileSeqRead &tune, bool read_all_upfront);
    ~DictFileReadContext();
    vespalib::FileHeader readHeader();
    void readExtendedHeader();
    bool close();
    const vespalib::string _id;
    uint64_t               _fileBitSize;
    uint32_t               _headerLen;
    bool                   _valid;
    DC                     _dc;
    ComprFileReadContext   _readContext;
    FastOS_File            _file;
};

PageDict4FileSeqRead::DictFileReadContext::DictFileReadContext(vespalib::stringref id, const vespalib::string & name,
                                                               const TuneFileSeqRead &tune, bool read_all_upfront)
    : _id(id),
      _fileBitSize(0u),
      _headerLen(0u),
      _valid(false),
      _dc(),
      _readContext(_dc),
      _file()
{
    _dc.setReadContext(&_readContext);
    if (tune.getWantDirectIO()) {
        _file.EnableDirectIO();
    }
    if (!_file.OpenReadOnly(name.c_str())) {
        LOG(error, "could not open %s: %s", _file.GetFileName(), getLastErrorString().c_str());
        return;
    }
    uint64_t fileSize = _file.GetSize();
    _readContext.setFile(&_file);
    _readContext.setFileSize(fileSize);
    if (read_all_upfront) {
        _readContext.allocComprBuf((fileSize + sizeof(uint64_t) - 1) / sizeof(uint64_t), 32_Ki);
    } else {
        _readContext.allocComprBuf(64_Ki, 32_Ki);
    }
    _dc.emptyBuffer(0);
    _readContext.readComprBuffer();
    if (read_all_upfront) {
        assert(_readContext.getBufferEndFilePos() >= fileSize);
    }
    _valid = true;
}

PageDict4FileSeqRead::DictFileReadContext::~DictFileReadContext() = default;

vespalib::FileHeader
PageDict4FileSeqRead::DictFileReadContext::readHeader() {
    vespalib::FileHeader header;
    uint32_t headerLen = _dc.readHeader(header, _file.getSize());
    _fileBitSize = getBitSizeAndAssertHeaders(header, _id);
    _dc.smallAlign(64);
    uint32_t minHeaderLen = header.getSize();
    minHeaderLen += (-minHeaderLen & 7);
    assert(headerLen >= minHeaderLen);
    assert(_dc.getReadOffset() == headerLen * 8);
    _headerLen = headerLen;
    return header;
}

PageDict4FileSeqRead::PageDict4FileSeqRead()
    : _pReader(),
      _ssReader(),
      _ss(),
      _sp(),
      _p(),
      _wordNum(0u)
{ }


PageDict4FileSeqRead::~PageDict4FileSeqRead() = default;

void
PageDict4FileSeqRead::DictFileReadContext::readExtendedHeader()
{
    vespalib::FileHeader header = readHeader();
    assert(header.hasTag("numWordIds"));
    assert(header.hasTag("avgBitsPerDoc"));
    assert(header.hasTag("minChunkDocs"));
    assert(header.hasTag("docIdLimit"));
    _dc._numWordIds = header.getTag("numWordIds").asInteger();
    _dc._avgBitsPerDoc = header.getTag("avgBitsPerDoc").asInteger();
    _dc._minChunkDocs = header.getTag("minChunkDocs").asInteger();
    _dc._docIdLimit = header.getTag("docIdLimit").asInteger();
}

void
PageDict4FileSeqRead::readWord(vespalib::string &word, uint64_t &wordNum, PostingListCounts &counts)
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
PageDict4FileSeqRead::DictFileReadContext::close() {
    _readContext.dropComprBuf();
    _readContext.setFile(nullptr);
    return _file.Close();
}

bool
PageDict4FileSeqRead::open(const vespalib::string &name,
                           const TuneFileSeqRead &tuneFileRead)
{
    _ss = std::make_unique<DictFileReadContext>(mySSId, name + ".ssdat", tuneFileRead, true);
    _sp = std::make_unique<DictFileReadContext>(mySPId, name + ".spdat", tuneFileRead, false);
    _p = std::make_unique<DictFileReadContext>(myPId, name + ".pdat", tuneFileRead, false);
    if ( !_ss->_valid || !_sp->_valid || !_p->_valid ) {
        return false;
    }

    _ss->readExtendedHeader();
    _sp->readHeader();
    _p->readHeader();

    _ssReader = std::make_unique<SSReader>(_ss->_readContext,
                                           _ss->_headerLen, _ss->_fileBitSize,
                                           _sp->_headerLen, _sp->_fileBitSize,
                                           _p->_headerLen, _p->_fileBitSize);

    // Instantiate helper class for reading
    _pReader = std::make_unique<Reader>(*_ssReader, _sp->_dc, _p->_dc);

    _ssReader->setup(_ss->_dc);
    _pReader->setup();
    _wordNum = 0;

    return true;
}


bool
PageDict4FileSeqRead::close()
{
    _pReader.reset();
    _ssReader.reset();
    if (_ss) {
        return _ss->close() && _sp->close() && _p->close();
    }
    return true;
}


void
PageDict4FileSeqRead::getParams(PostingListParams &params)
{
    params.clear();
    if (_ss) {
        const DC &dc = _ss->_dc;
        params.set("avgBitsPerDoc", dc._avgBitsPerDoc);
        params.set("minChunkDocs", dc._minChunkDocs);
        params.set("docIdLimit", dc._docIdLimit);
        params.set("numWordIds", dc._numWordIds);
        params.set("numCounts", dc._numWordIds);
    }
}


PageDict4FileSeqWrite::PageDict4FileSeqWrite()
    : _pWriter(),
      _spWriter(),
      _ssWriter(),
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


PageDict4FileSeqWrite::~PageDict4FileSeqWrite() = default;


void
PageDict4FileSeqWrite::writeWord(vespalib::stringref word, const PostingListCounts &counts)
{
    _pWriter->addCounts(word, counts);
}


bool
PageDict4FileSeqWrite::open(const vespalib::string &name,
                            const TuneFileSeqWrite &tuneFileWrite,
                            const FileHeaderContext &fileHeaderContext)
{
    assert( ! _pWriter);
    assert( ! _spWriter);
    assert( ! _ssWriter);

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

    _pWriteContext.allocComprBuf(64_Ki, 32_Ki);
    _spWriteContext.allocComprBuf(64_Ki, 32_Ki);
    _ssWriteContext.allocComprBuf(64_Ki, 32_Ki);

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

    _ssWriter = std::make_unique<SSWriter>(_sse);
    _spWriter = std::make_unique<SPWriter>(*_ssWriter, _spe);
    _pWriter = std::make_unique<PWriter>(*_spWriter, _pe);
    _spWriter->setup();
    _pWriter->setup();

    return true;
}


bool
PageDict4FileSeqWrite::close()
{
    bool success = true;
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
    success &= _pfile.Sync();
    success &= _pfile.Close();
    _pWriteContext.setFile(nullptr);
    _spWriteContext.dropComprBuf();
    success &= _spfile.Sync();
    success &= _spfile.Close();
    _spWriteContext.setFile(nullptr);
    _ssWriteContext.dropComprBuf();
    success &= _ssfile.Sync();
    success &= _ssfile.Close();
    _ssWriteContext.setFile(nullptr);

    // Update file headers
    success &= updatePHeader(usedPBits);
    success &= updateSPHeader(usedSPBits);
    success &= updateSSHeader(usedSSBits);

    _pWriter.reset();
    _spWriter.reset();
    _ssWriter.reset();

    return success;
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
    vespalib::FileHeader header(FileSettings::DIRECTIO_ALIGNMENT);

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
    vespalib::FileHeader header(FileSettings::DIRECTIO_ALIGNMENT);

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
    vespalib::FileHeader header(FileSettings::DIRECTIO_ALIGNMENT);

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


bool
PageDict4FileSeqWrite::updatePHeader(uint64_t fileBitSize)
{
    vespalib::FileHeader h(FileSettings::DIRECTIO_ALIGNMENT);
    FastOS_File f;
    f.OpenReadWrite(_pfile.GetFileName());
    h.readFile(f);
    FileHeaderContext::setFreezeTime(h);
    typedef vespalib::GenericHeader::Tag Tag;
    h.putTag(Tag("frozen", 1));
    h.putTag(Tag("fileBitSize", fileBitSize));
    h.rewriteFile(f);
    bool success = f.Sync();
    success &= f.Close();
    return success;
}


bool
PageDict4FileSeqWrite::updateSPHeader(uint64_t fileBitSize)
{
    vespalib::FileHeader h(FileSettings::DIRECTIO_ALIGNMENT);
    FastOS_File f;
    f.OpenReadWrite(_spfile.GetFileName());
    h.readFile(f);
    FileHeaderContext::setFreezeTime(h);
    typedef vespalib::GenericHeader::Tag Tag;
    h.putTag(Tag("frozen", 1));
    h.putTag(Tag("fileBitSize", fileBitSize));
    h.rewriteFile(f);
    bool success = f.Sync();
    success &= f.Close();
    return success;
}


bool
PageDict4FileSeqWrite::updateSSHeader(uint64_t fileBitSize)
{
    vespalib::FileHeader h(FileSettings::DIRECTIO_ALIGNMENT);
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
    bool success = f.Sync();
    success &= f.Close();
    return success;
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
