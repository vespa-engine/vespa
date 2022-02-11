// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "pagedict4file.h"
#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/searchlib/util/file_settings.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/fastos/file.h>

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

struct PageDict4FileSeqWrite::DictFileContext {
    DictFileContext(bool extended, vespalib::stringref id, vespalib::stringref desc,
                    const vespalib::string &name, const TuneFileSeqWrite &tune);
    ~DictFileContext();
    void makeHeader(const FileHeaderContext &fileHeaderContext);
    bool updateHeader(uint64_t fileBitSize, uint64_t wordNum);
    void writeExtendedHeader(vespalib::GenericHeader &header);
    bool close();
    const vespalib::string _id;
    const vespalib::string _desc;
    const bool             _extended;
    uint32_t               _headerLen;
    bool                   _valid;
    EC                     _ec;
    ComprFileWriteContext  _writeContext;
    FastOS_File            _file;
};

PageDict4FileSeqWrite::DictFileContext::DictFileContext(bool extended, vespalib::stringref id, vespalib::stringref desc,
                                                        const vespalib::string & name, const TuneFileSeqWrite &tune)
    : _id(id),
      _desc(desc),
      _extended(extended),
      _headerLen(0u),
      _valid(false),
      _ec(),
      _writeContext(_ec),
      _file()
{
    _ec.setWriteContext(&_writeContext);
    if (tune.getWantSyncWrites()) {
        _file.EnableSyncWrites();
    }
    if (tune.getWantDirectIO()) {
        _file.EnableDirectIO();
    }
    bool ok = _file.OpenWriteOnly(name.c_str());
    assertOpenWriteOnly(ok, name);
    _writeContext.setFile(&_file);
    _writeContext.allocComprBuf(64_Ki, 32_Ki);
    uint64_t fileSize = _file.GetSize();
    uint64_t bufferStartFilePos = _writeContext.getBufferStartFilePos();
    assert(fileSize >= bufferStartFilePos);
    _file.SetSize(bufferStartFilePos);
    assert(bufferStartFilePos == static_cast<uint64_t>(_file.GetPosition()));

    _ec.setupWrite(_writeContext);
    assert(_ec.getWriteOffset() == 0);
    _valid = true;
}

bool
PageDict4FileSeqWrite::DictFileContext::DictFileContext::close() {
    //uint64_t usedPBits = _ec.getWriteOffset();
    _ec.flush();
    _writeContext.writeComprBuffer(true);

    _writeContext.dropComprBuf();
    bool success = _file.Sync();
    success &= _file.Close();
    _writeContext.setFile(nullptr);
    return success;
}

PageDict4FileSeqWrite::DictFileContext::~DictFileContext() = default;

PageDict4FileSeqWrite::PageDict4FileSeqWrite()
    : _params(),
      _pWriter(),
      _spWriter(),
      _ssWriter(),
      _ss(),
      _sp(),
      _p()
{ }

PageDict4FileSeqWrite::~PageDict4FileSeqWrite() = default;

void
PageDict4FileSeqWrite::writeWord(vespalib::stringref word, const PostingListCounts &counts)
{
    _pWriter->addCounts(word, counts);
}

bool
PageDict4FileSeqWrite::open(const vespalib::string &name,
                            const TuneFileSeqWrite &tune,
                            const FileHeaderContext &fileHeaderContext)
{
    assert( ! _pWriter);
    assert( ! _spWriter);
    assert( ! _ssWriter);
    _ss = std::make_unique<DictFileContext>(true, mySSId, "Dictionary sparse sparse file", name + ".ssdat", tune);
    _sp = std::make_unique<DictFileContext>(false, mySPId, "Dictionary sparse page file", name + ".spdat", tune);
    _p = std::make_unique<DictFileContext>(false, myPId, "Dictionary page file", name + ".pdat", tune);
    activateParams(_params);
    // Write initial file headers
    _p->makeHeader(fileHeaderContext);
    _sp->makeHeader(fileHeaderContext);
    _ss->makeHeader(fileHeaderContext);

    _ssWriter = std::make_unique<SSWriter>(_ss->_ec);
    _spWriter = std::make_unique<SPWriter>(*_ssWriter, _sp->_ec);
    _pWriter = std::make_unique<PWriter>(*_spWriter, _p->_ec);
    _spWriter->setup();
    _pWriter->setup();
    return true;
}

bool
PageDict4FileSeqWrite::close()
{
    bool success = true;
    _pWriter->flush();
    uint64_t usedPBits = _p->_ec.getWriteOffset();
    uint64_t usedSPBits = _sp->_ec.getWriteOffset();
    uint64_t usedSSBits = _ss->_ec.getWriteOffset();
    success &= _p->close();
    success &= _sp->close();
    success &= _ss->close();

    uint64_t wordNum = _pWriter->getWordNum();
    // Update file headers
    success &= _p->updateHeader(usedPBits, wordNum);
    success &= _sp->updateHeader(usedSPBits, wordNum);
    success &= _ss->updateHeader(usedSSBits, wordNum);

    _pWriter.reset();
    _spWriter.reset();
    _ssWriter.reset();

    return success;
}

void
PageDict4FileSeqWrite::DictFileContext::writeExtendedHeader(vespalib::GenericHeader &header)
{
    typedef vespalib::GenericHeader::Tag Tag;
    header.putTag(Tag("numWordIds", _ec._numWordIds));
    header.putTag(Tag("avgBitsPerDoc", _ec._avgBitsPerDoc));
    header.putTag(Tag("minChunkDocs", _ec._minChunkDocs));
    header.putTag(Tag("docIdLimit", _ec._docIdLimit));
}

void
PageDict4FileSeqWrite::DictFileContext::makeHeader(const FileHeaderContext &fileHeaderContext)
{
    typedef vespalib::GenericHeader::Tag Tag;
    vespalib::FileHeader header(FileSettings::DIRECTIO_ALIGNMENT);

    fileHeaderContext.addTags(header, _file.GetFileName());
    header.putTag(Tag("frozen", 0));
    header.putTag(Tag("fileBitSize", 0));
    header.putTag(Tag("format.0", _id));
    header.putTag(Tag("endian", "big"));
    header.putTag(Tag("desc", _desc));
    if (_extended) {
        writeExtendedHeader(header);
    }
    _ec.setupWrite(_writeContext);
    _ec.writeHeader(header);
    _ec.smallAlign(64);
    _ec.flush();
    uint32_t headerLen = header.getSize();
    headerLen += (-headerLen & 7);
    assert(_ec.getWriteOffset() == headerLen * 8);
    assert((_ec.getWriteOffset() & 63) == 0); // Header must be word aligned
    if (_headerLen != 0) {
        assert(_headerLen == headerLen);
    }
    _headerLen = headerLen;
}

bool
PageDict4FileSeqWrite::DictFileContext::updateHeader(uint64_t fileBitSize, uint64_t wordNum)
{
    vespalib::FileHeader h(FileSettings::DIRECTIO_ALIGNMENT);
    FastOS_File f;
    f.OpenReadWrite(_file.GetFileName());
    h.readFile(f);
    FileHeaderContext::setFreezeTime(h);
    typedef vespalib::GenericHeader::Tag Tag;
    h.putTag(Tag("frozen", 1));
    h.putTag(Tag("fileBitSize", fileBitSize));
    if (_extended) {
        assert(wordNum <= _ec._numWordIds);
        h.putTag(Tag("numWordIds", wordNum));
    }
    h.rewriteFile(f);
    bool success = f.Sync();
    success &= f.Close();
    return success;
}

void
PageDict4FileSeqWrite::setParams(const PostingListParams &params) {
    _params.add(params);
    if (_ss) {
        activateParams(_params);
    }
}

void
PageDict4FileSeqWrite::activateParams(const PostingListParams &params) {
    assert(_ss);
    EC & ec = _ss->_ec;
    params.get("avgBitsPerDoc", ec._avgBitsPerDoc);
    params.get("minChunkDocs", ec._minChunkDocs);
    params.get("docIdLimit", ec._docIdLimit);
    params.get("numWordIds", ec._numWordIds);
    _sp->_ec.copyParams(_ss->_ec);
    _p->_ec.copyParams(_ss->_ec);
}

void
PageDict4FileSeqWrite::getParams(PostingListParams &params)
{
    params.clear();
    if (_ss) {
        EC &ec = _ss->_ec;
        params.set("avgBitsPerDoc", ec._avgBitsPerDoc);
        params.set("minChunkDocs", ec._minChunkDocs);
        params.set("docIdLimit", ec._docIdLimit);
        params.set("numWordIds", ec._numWordIds);
    } else {
        params = _params;
    }
}

}
