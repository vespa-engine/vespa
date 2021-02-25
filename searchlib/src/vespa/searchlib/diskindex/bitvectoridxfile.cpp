// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bitvectoridxfile.h"
#include <vespa/searchlib/index/bitvectorkeys.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/util/size_literals.h>
#include <cassert>

namespace search::diskindex {

using search::index::BitVectorWordSingleKey;
using search::common::FileHeaderContext;

namespace {

void
readHeader(vespalib::FileHeader &h, const vespalib::string &name)
{
    Fast_BufferedFile file(32_Ki);
    file.OpenReadOnly(name.c_str());
    h.readFile(file);
    file.Close();
}

const size_t FILE_HEADERSIZE_ALIGNMENT = 4_Ki;

}

BitVectorIdxFileWrite::BitVectorIdxFileWrite(BitVectorKeyScope scope)
    : _idxFile(),
      _numKeys(0),
      _docIdLimit(0),
      _idxHeaderLen(0),
      _scope(scope)
{
}

BitVectorIdxFileWrite::~BitVectorIdxFileWrite() = default;

uint64_t
BitVectorIdxFileWrite::idxSize() const
{
    return _idxHeaderLen +
        static_cast<int64_t>(_numKeys) * sizeof(BitVectorWordSingleKey);
}

void
BitVectorIdxFileWrite::open(const vespalib::string &name,
                         uint32_t docIdLimit,
                         const TuneFileSeqWrite &tuneFileWrite,
                         const FileHeaderContext &fileHeaderContext)
{
    if (_numKeys != 0) {
        assert(docIdLimit == _docIdLimit);
    } else {
        _docIdLimit = docIdLimit;
    }
    vespalib::string idxname = name + getBitVectorKeyScopeSuffix(_scope);

    assert( !_idxFile);
    _idxFile = std::make_unique<Fast_BufferedFile>(new FastOS_File());
    if (tuneFileWrite.getWantSyncWrites()) {
        _idxFile->EnableSyncWrites();
    }
    if (tuneFileWrite.getWantDirectIO()) {
        _idxFile->EnableDirectIO();
    }

    // XXX no checking for success:
    _idxFile->OpenWriteOnly(idxname.c_str());

    if (_idxHeaderLen == 0) {
        assert(_numKeys == 0);
        makeIdxHeader(fileHeaderContext);
    }

    int64_t pos = idxSize();

    int64_t oldidxsize = _idxFile->GetSize();
    assert(oldidxsize >= pos);
    (void) oldidxsize;

    _idxFile->SetSize(pos);

    assert(pos == _idxFile->GetPosition());
}

void
BitVectorIdxFileWrite::makeIdxHeader(const FileHeaderContext &fileHeaderContext)
{
    vespalib::FileHeader h(FILE_HEADERSIZE_ALIGNMENT);
    typedef vespalib::GenericHeader::Tag Tag;
    fileHeaderContext.addTags(h, _idxFile->GetFileName());
    h.putTag(Tag("docIdLimit", _docIdLimit));
    h.putTag(Tag("numKeys", _numKeys));
    h.putTag(Tag("frozen", 0));
    if (_scope != BitVectorKeyScope::SHARED_WORDS) {
        h.putTag(Tag("fileBitSize", 0));
    }
    h.putTag(Tag("desc", "Bitvector dictionary file, single words"));
    _idxFile->SetPosition(0);
    _idxHeaderLen = h.writeFile(*_idxFile);
    _idxFile->Flush();
}

void
BitVectorIdxFileWrite::updateIdxHeader(uint64_t fileBitSize)
{
    vespalib::FileHeader h(FILE_HEADERSIZE_ALIGNMENT);
    typedef vespalib::GenericHeader::Tag Tag;
    readHeader(h, _idxFile->GetFileName());
    FileHeaderContext::setFreezeTime(h);
    h.putTag(Tag("numKeys", _numKeys));
    h.putTag(Tag("frozen", 1));
    if (_scope != BitVectorKeyScope::SHARED_WORDS) {
        h.putTag(Tag("fileBitSize", fileBitSize));
    }
    _idxFile->Flush();
    _idxFile->Sync();
    assert(h.getSize() == _idxHeaderLen);
    _idxFile->SetPosition(0);
    h.writeFile(*_idxFile);
    _idxFile->Flush();
    _idxFile->Sync();
}

void
BitVectorIdxFileWrite::addWordSingle(uint64_t wordNum, uint32_t numDocs)
{
    BitVectorWordSingleKey key;
    key._wordNum = wordNum;
    key._numDocs = numDocs;
    _idxFile->WriteBuf(&key, sizeof(key));
    ++_numKeys;
}

void
BitVectorIdxFileWrite::flush()
{
    _idxFile->Flush();

    uint64_t pos = _idxFile->GetPosition();
    assert(pos == idxSize());
    (void) pos;
}

void
BitVectorIdxFileWrite::syncCommon()
{
    _idxFile->Sync();
}

void
BitVectorIdxFileWrite::sync()
{
    flush();
    syncCommon();
}

void
BitVectorIdxFileWrite::close()
{
    if (_idxFile) {
        if (_idxFile->IsOpened()) {
            uint64_t pos = _idxFile->GetPosition();
            assert(pos == idxSize());
            _idxFile->alignEndForDirectIO();
            updateIdxHeader(pos * 8);
            _idxFile->Close();
        }
        _idxFile.reset();
    }
}

}
