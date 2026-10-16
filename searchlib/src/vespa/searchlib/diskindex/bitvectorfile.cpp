// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bitvectorfile.h"

#include <vespa/check_require.h>
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/searchlib/common/fileheadertags.h>
#include <vespa/searchlib/index/bitvectorkeys.h>
#include <vespa/searchlib/util/file_settings.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/util/size_literals.h>

namespace search::diskindex {

using search::common::FileHeaderContext;
using search::index::BitVectorWordSingleKey;
using namespace tags;

namespace {

void readHeader(vespalib::FileHeader& h, const std::string& name) {
    Fast_BufferedFile file(32_Ki);
    file.ReadOpenExisting(name.c_str());
    h.readFile(file);
}

} // namespace

BitVectorFileWrite::BitVectorFileWrite(BitVectorKeyScope scope)
    : BitVectorIdxFileWrite(scope), _datFile(), _datHeaderLen(0) {
}

BitVectorFileWrite::~BitVectorFileWrite() = default;

void BitVectorFileWrite::open(const std::string& name, uint32_t docIdLimit, const TuneFileSeqWrite& tuneFileWrite,
                              const FileHeaderContext& fileHeaderContext) {
    std::string datname = name + ".bdat";

    CHECK(!_datFile);

    Parent::open(name, docIdLimit, tuneFileWrite, fileHeaderContext);

    _datFile = std::make_unique<Fast_BufferedFile>();
    if (tuneFileWrite.getWantSyncWrites()) {
        _datFile->EnableSyncWrites();
    }
    if (tuneFileWrite.getWantDirectIO()) {
        _datFile->EnableDirectIO();
    }
    _datFile->WriteOpen(datname.c_str());

    if (_datHeaderLen == 0) {
        CHECK(_numKeys == 0);
        makeDatHeader(fileHeaderContext);
    }

    size_t  bitmapbytes = BitVector::getFileBytes(_docIdLimit);
    int64_t pos = static_cast<int64_t>(_numKeys) * static_cast<int64_t>(bitmapbytes) + _datHeaderLen;

    CHECK(_datFile->getSize() >= pos);
    _datFile->SetSize(pos);

    CHECK(pos == _datFile->getPosition());
}

void BitVectorFileWrite::makeDatHeader(const FileHeaderContext& fileHeaderContext) {
    vespalib::FileHeader h(FileSettings::DIRECTIO_ALIGNMENT);
    using Tag = vespalib::GenericHeader::Tag;
    fileHeaderContext.addTags(h, _datFile->GetFileName());
    h.putTag(Tag(ENTRY_SIZE, (int64_t)BitVector::getFileBytes(_docIdLimit)));
    h.putTag(Tag(DOCID_LIMIT, _docIdLimit));
    h.putTag(Tag(NUM_KEYS, _numKeys));
    h.putTag(Tag(FROZEN, 0));
    h.putTag(Tag(FILE_BIT_SIZE, 0));
    h.putTag(Tag(DESC, "Bitvector data file"));
    _datFile->SetPosition(0);
    _datHeaderLen = h.writeFile(*_datFile);
    _datFile->Flush();
}

void BitVectorFileWrite::updateDatHeader(uint64_t fileBitSize) {
    vespalib::FileHeader h(FileSettings::DIRECTIO_ALIGNMENT);
    using Tag = vespalib::GenericHeader::Tag;
    readHeader(h, _datFile->GetFileName());
    FileHeaderContext::setFreezeTime(h);
    h.putTag(Tag(NUM_KEYS, _numKeys));
    h.putTag(Tag(FROZEN, 1));
    h.putTag(Tag(FILE_BIT_SIZE, fileBitSize));
    bool sync_ok = _datFile->Sync();
    CHECK(sync_ok);
    CHECK(h.getSize() == _datHeaderLen);
    _datFile->SetPosition(0);
    h.writeFile(*_datFile);
    sync_ok = _datFile->Sync();
    CHECK(sync_ok);
}

void BitVectorFileWrite::addWordSingle(uint64_t wordNum, const BitVector& bitVector) {
    CHECK(bitVector.size() == _docIdLimit);
    bitVector.invalidateCachedCount();
    Parent::addWordSingle(wordNum, bitVector.countTrueBits());
    _datFile->WriteBuf(bitVector.getStart(), bitVector.getFileBytes());
}

void BitVectorFileWrite::flush() {
    Parent::flush();
    _datFile->Flush();
}

void BitVectorFileWrite::sync() {
    flush();
    Parent::syncCommon();
    bool sync_ok = _datFile->Sync();
    CHECK(sync_ok);
}

void BitVectorFileWrite::close() {
    if (_datFile && _datFile->IsOpened()) {
        size_t   bitmapbytes = BitVector::getFileBytes(_docIdLimit);
        uint64_t pos = _datFile->getPosition();
        CHECK(pos == static_cast<uint64_t>(_numKeys) * static_cast<uint64_t>(bitmapbytes) + _datHeaderLen);
        (void)bitmapbytes;
        _datFile->alignEndForDirectIO();
        updateDatHeader(pos * 8);
        bool close_ok = _datFile->Close();
        CHECK(close_ok);
    }
    _datFile.reset();
    Parent::close();
}

BitVectorCandidate::~BitVectorCandidate() = default;

} // namespace search::diskindex
