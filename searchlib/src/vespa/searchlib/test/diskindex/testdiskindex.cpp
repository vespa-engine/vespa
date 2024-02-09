// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "testdiskindex.h"
#include <vespa/searchlib/diskindex/indexbuilder.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/index/i_field_length_inspector.h>
#include <vespa/vespalib/io/fileutil.h>
#include <cassert>

namespace search::diskindex {

using index::DocIdAndFeatures;
using index::DummyFileHeaderContext;
using index::FieldLengthInfo;
using index::IFieldLengthInspector;
using index::Schema;
using index::WordDocElementWordPosFeatures;
using index::schema::DataType;

namespace {

class MockFieldLengthInspector : public IFieldLengthInspector {
    FieldLengthInfo get_field_length_info(const vespalib::string& field_name) const override {
        if (field_name == "f1") {
            return {3.5, 21};
        } else if (field_name == "f2") {
            return {4.0, 23};
        } else {
            return {};
        }
    }
};

}

struct Builder
{
    MockFieldLengthInspector        _mock_field_length_inspector;
    TuneFileIndexing                _tuneFileIndexing;
    DummyFileHeaderContext          _fileHeaderContext;
    search::diskindex::IndexBuilder _ib;
    DocIdAndFeatures                _features;

    Builder(const std::string &dir, const Schema &s, uint32_t docIdLimit, uint64_t numWordIds, bool directio)
        : _tuneFileIndexing(),
          _fileHeaderContext(),
          _ib(s, dir, docIdLimit,numWordIds, _mock_field_length_inspector, _tuneFileIndexing, _fileHeaderContext),
          _features()
    {
        if (directio) {
            _tuneFileIndexing._read.setWantDirectIO();
            _tuneFileIndexing._write.setWantDirectIO();
        }
    }

    void addDoc(index::FieldIndexBuilder & fb, uint32_t docId) {
        _features.clear(docId);
        _features.elements().emplace_back(0, 1, 1);
        _features.elements().back().setNumOccs(1);
        _features.word_positions().emplace_back(0);
        fb.add_document(_features);
    }
};


void
TestDiskIndex::buildSchema()
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

    if (!fieldEmpty) {
        // f1
        auto fb = b._ib.startField(0);
        if (!wordEmpty && !docEmpty) {
            fb->startWord("w1");
            b.addDoc(*fb, 1);
            b.addDoc(*fb, 3);
            fb->endWord();
        }
        fb = b._ib.startField(1);
        if (!wordEmpty && !docEmpty) {
            // f2
            fb->startWord("w1");
            b.addDoc(*fb, 2);
            b.addDoc(*fb, 4);
            b.addDoc(*fb, 6);
            fb->endWord();
            fb->startWord("w2");
            for (uint32_t docId = 1; docId < 18; ++docId) {
                b.addDoc(*fb, docId);
            }
            fb->endWord();
        }
    }
}

void
TestDiskIndex::openIndex(const std::string &dir, bool directio, bool readmmap,
                         bool fieldEmpty, bool docEmpty, bool wordEmpty)
{
    buildIndex(dir, directio, fieldEmpty, docEmpty, wordEmpty);
    TuneFileRandRead    tuneFileRead;
    if (directio) {
        tuneFileRead.setWantDirectIO();
    }
    if (readmmap) {
        tuneFileRead.setWantMemoryMap();
    }
    _index = std::make_unique<DiskIndex>(dir);
    bool ok(_index->setup(tuneFileRead));
    assert(ok);
    (void) ok;
}

TestDiskIndex::TestDiskIndex() = default;

TestDiskIndex::~TestDiskIndex() = default;

}
