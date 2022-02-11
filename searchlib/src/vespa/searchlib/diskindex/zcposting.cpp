// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "zcposting.h"
#include <vespa/searchlib/index/postinglistcounts.h>
#include <vespa/searchlib/index/postinglistcountfile.h>
#include <vespa/searchlib/index/postinglistfile.h>
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/searchlib/index/postinglistparams.h>
#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/vespalib/data/fileheader.h>

#include <vespa/log/log.h>
LOG_SETUP(".diskindex.zcposting");

namespace {

vespalib::string myId5("Zc.5");
vespalib::string myId4("Zc.4");
vespalib::string interleaved_features("interleaved_features");

}

namespace search::diskindex {

using index::PostingListCountFileSeqRead;
using index::PostingListCountFileSeqWrite;
using common::FileHeaderContext;
using bitcompression::FeatureDecodeContextBE;
using bitcompression::FeatureEncodeContextBE;
using vespalib::getLastErrorString;


Zc4PostingSeqRead::Zc4PostingSeqRead(PostingListCountFileSeqRead *countFile, bool dynamic_k)
    : PostingListFileSeqRead(),
      _reader(dynamic_k),
      _file(),
      _numWords(0),
      _fileBitSize(0),
      _countFile(countFile)
{
    if (_countFile != nullptr) {
        PostingListParams params;
        _countFile->getParams(params);
        params.get("docIdLimit", _reader.get_posting_params()._doc_id_limit);
        params.get("minChunkDocs", _reader.get_posting_params()._min_chunk_docs);
    }
}


Zc4PostingSeqRead::~Zc4PostingSeqRead() = default;

void
Zc4PostingSeqRead::readDocIdAndFeatures(DocIdAndFeatures &features)
{
    _reader.read_doc_id_and_features(features);
}

void
Zc4PostingSeqRead::readCounts(const PostingListCounts &counts)
{
    _reader.set_counts(counts);
}


bool
Zc4PostingSeqRead::open(const vespalib::string &name,
                        const TuneFileSeqRead &tuneFileRead)
{
    if (tuneFileRead.getWantDirectIO()) {
        _file.EnableDirectIO();
    }
    bool res = _file.OpenReadOnly(name.c_str());
    if (res) {
        auto &readContext = _reader.get_read_context();
        readContext.setFile(&_file);
        readContext.setFileSize(_file.GetSize());
        auto &d = _reader.get_decode_features();
        readContext.allocComprBuf(65536u, 32768u);
        d.emptyBuffer(0);
        readContext.readComprBuffer();

        readHeader();
        if (d._valI >= d._valE) {
            readContext.readComprBuffer();
        }
    } else {
        LOG(error, "could not open %s: %s",
            _file.GetFileName(), getLastErrorString().c_str());
    }
    return res;
}


bool
Zc4PostingSeqRead::close()
{
    auto &readContext = _reader.get_read_context();
    readContext.dropComprBuf();
    readContext.setFile(nullptr);
    return _file.Close();
}


void
Zc4PostingSeqRead::getParams(PostingListParams &params)
{
    if (_countFile != nullptr) {
        PostingListParams countParams;
        _countFile->getParams(countParams);
        params = countParams;
        uint32_t countDocIdLimit = 0;
        uint32_t countMinChunkDocs = 0;
        countParams.get("docIdLimit", countDocIdLimit);
        countParams.get("minChunkDocs", countMinChunkDocs);
        assert(_reader.get_posting_params()._doc_id_limit == countDocIdLimit);
        assert(_reader.get_posting_params()._min_chunk_docs == countMinChunkDocs);
    } else {
        params.clear();
        params.set("docIdLimit", _reader.get_posting_params()._doc_id_limit);
        params.set("minChunkDocs", _reader.get_posting_params()._min_chunk_docs);
    }
    params.set("minSkipDocs", _reader.get_posting_params()._min_skip_docs);
    params.set(interleaved_features, _reader.get_posting_params()._encode_interleaved_features);
}


void
Zc4PostingSeqRead::getFeatureParams(PostingListParams &params)
{
    _reader.get_decode_features().getParams(params);
}


void
Zc4PostingSeqRead::readHeader()
{
    FeatureDecodeContextBE &d = _reader.get_decode_features();
    auto &posting_params = _reader.get_posting_params();
    const vespalib::string &myId = posting_params._dynamic_k ? myId5 : myId4;

    vespalib::FileHeader header;
    d.readHeader(header, _file.getSize());
    uint32_t headerLen = header.getSize();
    assert(header.hasTag("frozen"));
    assert(header.hasTag("fileBitSize"));
    assert(header.hasTag("format.0"));
    assert(header.hasTag("format.1"));
    assert(!header.hasTag("format.2"));
    assert(header.hasTag("numWords"));
    assert(header.hasTag("minChunkDocs"));
    assert(header.hasTag("docIdLimit"));
    assert(header.hasTag("minSkipDocs"));
    assert(header.hasTag("endian"));
    bool completed = header.getTag("frozen").asInteger() != 0;
    _fileBitSize = header.getTag("fileBitSize").asInteger();
    headerLen += (-headerLen & 7);
    assert(completed);
    (void) completed;
    assert(_fileBitSize >= 8 * headerLen);
    assert(header.getTag("format.0").asString() == myId);
    (void) myId;
    assert(header.getTag("format.1").asString() == d.getIdentifier());
    _numWords = header.getTag("numWords").asInteger();
    posting_params._min_chunk_docs = header.getTag("minChunkDocs").asInteger();
    posting_params._doc_id_limit = header.getTag("docIdLimit").asInteger();
    posting_params._min_skip_docs = header.getTag("minSkipDocs").asInteger();
    if (header.hasTag(interleaved_features) && (header.getTag(interleaved_features).asInteger() != 0)) {
       posting_params._encode_interleaved_features = true;
    }
    assert(header.getTag("endian").asString() == "big");
    // Read feature decoding specific subheader
    d.readHeader(header, "features.");
    // Align on 64-bit unit
    d.smallAlign(64);
    assert(d.getReadOffset() == headerLen * 8);
    _headerBitLen = d.getReadOffset();
}


const vespalib::string &
Zc4PostingSeqRead::getIdentifier(bool dynamic_k)
{
    return (dynamic_k ? myId5 : myId4);
}


Zc4PostingSeqWrite::
Zc4PostingSeqWrite(PostingListCountFileSeqWrite *countFile)
    : PostingListFileSeqWrite(),
      _writer(_counts),
      _file(),
      _fileBitSize(0),
      _countFile(countFile)
{
    if (_countFile != nullptr) {
        PostingListParams params;
        _countFile->getParams(params);
        _writer.set_posting_list_params(params);
    }
}


Zc4PostingSeqWrite::~Zc4PostingSeqWrite() = default;


void
Zc4PostingSeqWrite::writeDocIdAndFeatures(const DocIdAndFeatures &features)
{
    _writer.write_docid_and_features(features);
}


void
Zc4PostingSeqWrite::flushWord()
{
    _writer.flush_word();
}


void
Zc4PostingSeqWrite::makeHeader(const FileHeaderContext &fileHeaderContext)
{
    EncodeContext &f = _writer.get_encode_features();
    EncodeContext &e = _writer.get_encode_context();
    ComprFileWriteContext &wce = _writer.get_write_context();

    const vespalib::string &myId = _writer.get_dynamic_k() ? myId5 : myId4;
    vespalib::FileHeader header;

    typedef vespalib::GenericHeader::Tag Tag;
    fileHeaderContext.addTags(header, _file.GetFileName());
    header.putTag(Tag("frozen", 0));
    header.putTag(Tag("fileBitSize", 0));
    header.putTag(Tag("format.0", myId));
    header.putTag(Tag("format.1", f.getIdentifier()));
    header.putTag(Tag("interleaved_features", _writer.get_encode_interleaved_features() ? 1 : 0));
    header.putTag(Tag("numWords", 0));
    header.putTag(Tag("minChunkDocs", _writer.get_min_chunk_docs()));
    header.putTag(Tag("docIdLimit", _writer.get_docid_limit()));
    header.putTag(Tag("minSkipDocs", _writer.get_min_skip_docs()));
    header.putTag(Tag("endian", "big"));
    header.putTag(Tag("desc", "Posting list file"));

    f.writeHeader(header, "features.");
    e.setupWrite(wce);
    e.writeHeader(header);
    e.smallAlign(64);
    e.flush();
    uint32_t headerLen = header.getSize();
    headerLen += (-headerLen & 7);      // Then to uint64_t
    assert(e.getWriteOffset() == headerLen * 8);
    assert((e.getWriteOffset() & 63) == 0); // Header must be word aligned
}


bool
Zc4PostingSeqWrite::updateHeader()
{
    vespalib::FileHeader h;
    FastOS_File f;
    f.OpenReadWrite(_file.GetFileName());
    h.readFile(f);
    FileHeaderContext::setFreezeTime(h);
    typedef vespalib::GenericHeader::Tag Tag;
    h.putTag(Tag("frozen", 1));
    h.putTag(Tag("fileBitSize", _fileBitSize));
    h.putTag(Tag("numWords", _writer.get_num_words()));
    h.rewriteFile(f);
    bool success = f.Sync();
    success &= f.Close();
    return success;
}


bool
Zc4PostingSeqWrite::open(const vespalib::string &name,
                         const TuneFileSeqWrite &tuneFileWrite,
                         const FileHeaderContext &fileHeaderContext)
{
    if (tuneFileWrite.getWantSyncWrites()) {
        _file.EnableSyncWrites();
    }
    if (tuneFileWrite.getWantDirectIO()) {
        _file.EnableDirectIO();
    }
    bool ok = _file.OpenWriteOnly(name.c_str());
    if (!ok) {
        LOG(error, "could not open '%s' for writing: %s",
            _file.GetFileName(), getLastErrorString().c_str());
        // XXX may need to do something more here, I don't know what...
        return false;
    }
    auto &writeContext = _writer.get_write_context();
    uint64_t bufferStartFilePos = writeContext.getBufferStartFilePos();
    assert(bufferStartFilePos == 0);
    _file.SetSize(0);
    writeContext.setFile(&_file);
    search::ComprBuffer &cb = writeContext;
    EncodeContext &e = _writer.get_encode_context();
    writeContext.allocComprBuf(65536u, 32768u);
    e.setupWrite(cb);
    // Reset accumulated stats
    _fileBitSize = 0;
    // Start write initial header
    makeHeader(fileHeaderContext);
    // end write initial header
    _writer.on_open();
    return true;    // Assume success
}


bool
Zc4PostingSeqWrite::close()
{
    _fileBitSize = _writer.get_encode_context().getWriteOffset();
    _writer.on_close(); // flush and pad
    auto &writeContext = _writer.get_write_context();
    writeContext.dropComprBuf();
    bool success = _file.Sync();
    success &= _file.Close();
    writeContext.setFile(nullptr);
    success &= updateHeader();
    return success;
}

void
Zc4PostingSeqWrite::
setParams(const PostingListParams &params)
{
    if (_countFile != nullptr) {
        _countFile->setParams(params);
    }
    _writer.set_posting_list_params(params);
}


void
Zc4PostingSeqWrite::
getParams(PostingListParams &params)
{
    if (_countFile != nullptr) {
        PostingListParams countParams;
        _countFile->getParams(countParams);
        params = countParams;
        uint32_t countDocIdLimit = 0;
        uint32_t countMinChunkDocs = 0;
        countParams.get("docIdLimit", countDocIdLimit);
        countParams.get("minChunkDocs", countMinChunkDocs);
        assert(_writer.get_docid_limit() == countDocIdLimit);
        assert(_writer.get_min_chunk_docs() == countMinChunkDocs);
    } else {
        params.clear();
        params.set("docIdLimit", _writer.get_docid_limit());
        params.set("minChunkDocs", _writer.get_min_chunk_docs());
    }
    params.set("minSkipDocs", _writer.get_min_skip_docs());
    params.set(interleaved_features, _writer.get_encode_interleaved_features());
}


void
Zc4PostingSeqWrite::
setFeatureParams(const PostingListParams &params)
{
    _writer.get_encode_features().setParams(params);
}


void
Zc4PostingSeqWrite::
getFeatureParams(PostingListParams &params)
{
    _writer.get_encode_features().getParams(params);
}


ZcPostingSeqWrite::ZcPostingSeqWrite(PostingListCountFileSeqWrite *countFile)
    : Zc4PostingSeqWrite(countFile)
{
    _writer.set_dynamic_k(true);
}

}
