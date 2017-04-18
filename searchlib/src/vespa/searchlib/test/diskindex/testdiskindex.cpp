// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/searchlib/test/diskindex/testdiskindex.h>
#include <vespa/searchlib/diskindex/indexbuilder.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/vespalib/io/fileutil.h>

namespace search {

using index::DummyFileHeaderContext;
using index::Schema;
using index::WordDocElementWordPosFeatures;
using index::schema::DataType;

namespace diskindex {

struct Builder
{
    search::diskindex::IndexBuilder _ib;
    TuneFileIndexing	_tuneFileIndexing;
    DummyFileHeaderContext   _fileHeaderContext;

    Builder(const std::string &dir,
            const Schema &s,
            uint32_t docIdLimit,
            uint64_t numWordIds,
            bool directio)
        : _ib(s)
    {
        if (directio) {
            _tuneFileIndexing._read.setWantDirectIO();
            _tuneFileIndexing._write.setWantDirectIO();
        }
        _ib.setPrefix(dir);
        _ib.open(docIdLimit, numWordIds, _tuneFileIndexing,
                 _fileHeaderContext);
    }

    void
    addDoc(uint32_t docId)
    {
        _ib.startDocument(docId);
        _ib.startElement(0, 1, 1);
        _ib.addOcc(WordDocElementWordPosFeatures(0));
        _ib.endElement();
        _ib.endDocument();
    }

    void
    close()
    {
        _ib.close();
    }
};


void
TestDiskIndex::buildSchema(void)
{
    _schema.addIndexField(Schema::IndexField("f1", DataType::STRING));
    _schema.addIndexField(Schema::IndexField("f2", DataType::STRING));
    _schema.addFieldSet(Schema::FieldSet("c2").
                        addField("f1").
                        addField("f2"));
}

void
TestDiskIndex::buildIndex(const std::string & dir, bool directio,
                 bool fieldEmpty, bool docEmpty, bool wordEmpty)
{
    Builder b(dir, _schema, docEmpty ? 1 : 32, wordEmpty ? 0 : 2, directio);
    if (!wordEmpty && !fieldEmpty && !docEmpty) {
        // f1
        b._ib.startField(0);
        b._ib.startWord("w1");
        b.addDoc(1);
        b.addDoc(3);
        b._ib.endWord();
        b._ib.endField();
        // f2
        b._ib.startField(1);
        b._ib.startWord("w1");
        b.addDoc(2);
        b.addDoc(4);
        b.addDoc(6);
        b._ib.endWord();
        b._ib.startWord("w2");
        for (uint32_t docId = 1; docId < 18; ++docId) {
            b.addDoc(docId);
        }
        b._ib.endWord();
        b._ib.endField();
    }
    b.close();
}

void
TestDiskIndex::openIndex(const std::string &dir, bool directio, bool readmmap,
                bool fieldEmpty, bool docEmpty, bool wordEmpty)
{
    buildIndex(dir, directio, fieldEmpty, docEmpty, wordEmpty);
    TuneFileRandRead	tuneFileRead;
    if (directio) {
        tuneFileRead.setWantDirectIO();
    }
    if (readmmap) {
        tuneFileRead.setWantMemoryMap();
    }
    _index.reset(new DiskIndex(dir));
    bool ok(_index->setup(tuneFileRead));
    assert(ok);
    (void) ok;
}

TestDiskIndex::TestDiskIndex() :
    _schema(),
    _index()
{
}

}
}
