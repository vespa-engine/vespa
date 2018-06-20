// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/diskindex/fusion.h>
#include <vespa/searchlib/diskindex/indexbuilder.h>
#include <vespa/searchlib/diskindex/zcposoccrandread.h>
#include <vespa/searchlib/fef/fieldpositionsiterator.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/index/docbuilder.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/btree/btreeroot.hpp>
#include <vespa/searchlib/btree/btreenodeallocator.hpp>
#include <vespa/searchlib/btree/btreenode.hpp>
#include <vespa/searchlib/memoryindex/dictionary.h>
#include <vespa/searchlib/memoryindex/documentinverter.h>
#include <vespa/searchlib/memoryindex/postingiterator.h>
#include <vespa/searchlib/diskindex/diskindex.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/util/filekit.h>
#include <vespa/searchlib/common/sequencedtaskexecutor.h>

#include <vespa/log/log.h>
LOG_SETUP("fusion_test");

namespace search {

using document::Document;
using fef::FieldPositionsIterator;
using fef::TermFieldMatchData;
using fef::TermFieldMatchDataArray;
using memoryindex::Dictionary;
using memoryindex::DocumentInverter;
using queryeval::SearchIterator;
using search::common::FileHeaderContext;
using search::index::schema::CollectionType;
using search::index::schema::DataType;

using namespace index;

namespace diskindex {


class Test : public vespalib::TestApp
{
private:
    Schema _schema;
    const Schema & getSchema() const { return _schema; }

    void requireThatFusionIsWorking(const vespalib::string &prefix, bool directio, bool readmmap);
public:
    Test();
    int Main() override;
};

namespace {

void
myPushDocument(DocumentInverter &inv, Dictionary &d)
{
    inv.pushDocuments(d, std::shared_ptr<IDestructorCallback>());
}

}

vespalib::string
toString(FieldPositionsIterator posItr,
         bool hasElements = false, bool hasWeights = false)
{
    vespalib::asciistream ss;
    ss << "{";
    ss << posItr.getFieldLength() << ":";
    bool first = true;
    for (; posItr.valid(); posItr.next()) {
        if (!first) ss << ",";
        ss << posItr.getPosition();
        first = false;
        if (hasElements) {
            ss << "[e=" << posItr.getElementId();
            if (hasWeights)
                ss << ",w=" << posItr.getElementWeight();
            ss << ",l=" << posItr.getElementLen() << "]";
        }
    }
    ss << "}";
    return ss.str();
}


#if 0
vespalib::string
toString(DocIdAndFeatures &features)
{
    vespalib::asciistream ss;
    ss << "{";
    std::vector<search::index::WordDocFieldElementFeatures>::const_iterator
        element = features._elements.begin();
    std::vector<search::index::WordDocFieldElementWordPosFeatures>::
        const_iterator position = features._wordPositions.begin();
    for (; field != fielde; ++field) {
        ss << "f=" << field->getFieldId() << "{";
        uint32_t numElements = field->getNumElements();
        while (numElements--) {
            ss << "e=" << element->getElementId() << ","
               << "ew=" << element->getWeight() << ","
               << "el=" << element->getElementLen() << "{";
            uint32_t numOccs = element->getNumOccs();
            while (numOccs--) {
                ss << position->getWordPos();
                if (numOccs != 0)
                    ss << ",";
            }
            ss << "}";
            if (numElements != 0)
                ss << ",";
        }
        ss << "}";
    }
    ss << "}";
    return ss.str();
}
#endif


void
validateDiskIndex(DiskIndex &dw,
                  bool f2HasElements,
                  bool f3HasWeights)
{
    typedef DiskIndex::LookupResult LR;
    typedef index::PostingListHandle PH;
    typedef search::queryeval::SearchIterator SB;

    const Schema &schema(dw.getSchema());

    {
        uint32_t id1(schema.getIndexFieldId("f0"));
        LR::UP lr1(dw.lookup(id1, "c"));
        EXPECT_TRUE(lr1.get() != NULL);
        PH::UP wh1(dw.readPostingList(*lr1));
        EXPECT_TRUE(wh1.get() != NULL);
        TermFieldMatchData f0;
        TermFieldMatchDataArray a;
        a.add(&f0);
        SB::UP sbap(wh1->createIterator(lr1->counts, a));
        sbap->initFullRange();
        EXPECT_EQUAL("{1000000:}", toString(f0.getIterator()));
        EXPECT_TRUE(sbap->seek(10));
        sbap->unpack(10);
        EXPECT_EQUAL("{7:2}", toString(f0.getIterator()));
    }
    {
        uint32_t id1(schema.getIndexFieldId("f2"));
        LR::UP lr1(dw.lookup(id1, "ax"));
        EXPECT_TRUE(lr1.get() != NULL);
        PH::UP wh1(dw.readPostingList(*lr1));
        EXPECT_TRUE(wh1.get() != NULL);
        TermFieldMatchData f2;
        TermFieldMatchDataArray a;
        a.add(&f2);
        SB::UP sbap(wh1->createIterator(lr1->counts, a));
        sbap->initFullRange();
        EXPECT_EQUAL("{1000000:}", toString(f2.getIterator()));
        EXPECT_TRUE(sbap->seek(10));
        sbap->unpack(10);
        if (f2HasElements) {
            EXPECT_EQUAL("{3:0[e=0,l=3],0[e=1,l=1]}",
                         toString(f2.getIterator(), true));
        } else {
            EXPECT_EQUAL("{3:0[e=0,l=3]}",
                         toString(f2.getIterator(), true));
        }
    }
    {
        uint32_t id1(schema.getIndexFieldId("f3"));;
        LR::UP lr1(dw.lookup(id1, "wx"));
        EXPECT_TRUE(lr1.get() != NULL);
        PH::UP wh1(dw.readPostingList(*lr1));
        EXPECT_TRUE(wh1.get() != NULL);
        TermFieldMatchData f3;
        TermFieldMatchDataArray a;
        a.add(&f3);
        SB::UP sbap(wh1->createIterator(lr1->counts, a));
        sbap->initFullRange();
        EXPECT_EQUAL("{1000000:}", toString(f3.getIterator()));
        EXPECT_TRUE(sbap->seek(10));
        sbap->unpack(10);
        if (f3HasWeights) {
            EXPECT_EQUAL("{2:0[e=0,w=4,l=2]}",
                         toString(f3.getIterator(), true, true));
        } else {
            EXPECT_EQUAL("{2:0[e=0,w=1,l=2]}",
                         toString(f3.getIterator(), true, true));
        }
    }
    {
        uint32_t id1(schema.getIndexFieldId("f3"));;
        LR::UP lr1(dw.lookup(id1, "zz"));
        EXPECT_TRUE(lr1.get() != NULL);
        PH::UP wh1(dw.readPostingList(*lr1));
        EXPECT_TRUE(wh1.get() != NULL);
        TermFieldMatchData f3;
        TermFieldMatchDataArray a;
        a.add(&f3);
        SB::UP sbap(wh1->createIterator(lr1->counts, a));
        sbap->initFullRange();
        EXPECT_EQUAL("{1000000:}", toString(f3.getIterator()));
        EXPECT_TRUE(sbap->seek(11));
        sbap->unpack(11);
        if (f3HasWeights) {
            EXPECT_EQUAL("{1:0[e=0,w=-27,l=1]}",
                         toString(f3.getIterator(), true, true));
        } else {
            EXPECT_EQUAL("{1:0[e=0,w=1,l=1]}",
                         toString(f3.getIterator(), true, true));
        }
    }
    {
        uint32_t id1(schema.getIndexFieldId("f3"));;
        LR::UP lr1(dw.lookup(id1, "zz0"));
        EXPECT_TRUE(lr1.get() != NULL);
        PH::UP wh1(dw.readPostingList(*lr1));
        EXPECT_TRUE(wh1.get() != NULL);
        TermFieldMatchData f3;
        TermFieldMatchDataArray a;
        a.add(&f3);
        SB::UP sbap(wh1->createIterator(lr1->counts, a));
        sbap->initFullRange();
        EXPECT_EQUAL("{1000000:}", toString(f3.getIterator()));
        EXPECT_TRUE(sbap->seek(12));
        sbap->unpack(12);
        if (f3HasWeights) {
            EXPECT_EQUAL("{1:0[e=0,w=0,l=1]}",
                         toString(f3.getIterator(), true, true));
        } else {
            EXPECT_EQUAL("{1:0[e=0,w=1,l=1]}",
                         toString(f3.getIterator(), true, true));
        }
    }
}


void
Test::requireThatFusionIsWorking(const vespalib::string &prefix,
                                 bool directio,
                                 bool readmmap)
{
    Schema schema;
    Schema schema2;
    Schema schema3;
    for (SchemaUtil::IndexIterator it(getSchema()); it.isValid(); ++it) {
        const Schema::IndexField &iField =
            _schema.getIndexField(it.getIndex());
        schema.addIndexField(Schema::IndexField(iField.getName(),
                                     iField.getDataType(),
                                     iField.getCollectionType()));
        if (iField.getCollectionType() == CollectionType::WEIGHTEDSET) {
            schema2.addIndexField(Schema::IndexField(iField.getName(),
                                                     iField.getDataType(),
                                                     CollectionType::ARRAY));
        } else {
            schema2.addIndexField(Schema::IndexField(iField.getName(),
                                                     iField.getDataType(),
                                                     iField.getCollectionType()));
        }
        schema3.addIndexField(Schema::IndexField(iField.getName(),
                                      iField.getDataType(),
                                      CollectionType::SINGLE));
    }
    schema3.addIndexField(Schema::IndexField("f4", DataType::STRING));
    schema.addFieldSet(Schema::FieldSet("nc0").
                              addField("f0").addField("f1"));
    schema2.addFieldSet(Schema::FieldSet("nc0").
                               addField("f1").addField("f0"));
    schema3.addFieldSet(Schema::FieldSet("nc2").
                               addField("f0").addField("f1").
                               addField("f2").addField("f3").
                               addField("f4"));
    Dictionary d(schema);
    DocBuilder b(schema);
    SequencedTaskExecutor invertThreads(2);
    SequencedTaskExecutor pushThreads(2);
    DocumentInverter inv(schema, invertThreads, pushThreads);
    Document::UP doc;

    b.startDocument("doc::10");
    b.startIndexField("f0").
        addStr("a").addStr("b").addStr("c").addStr("d").
        addStr("e").addStr("f").addStr("z").
        endField();
    b.startIndexField("f1").
        addStr("w").addStr("x").
        addStr("y").addStr("z").
        endField();
    b.startIndexField("f2").
        startElement(4).addStr("ax").addStr("ay").addStr("z").endElement().
        startElement(5).addStr("ax").endElement().
        endField();
    b.startIndexField("f3").
        startElement(4).addStr("wx").addStr("z").endElement().
        endField();

    doc = b.endDocument();
    inv.invertDocument(10, *doc);
    invertThreads.sync();
    myPushDocument(inv, d);
    pushThreads.sync();

    b.startDocument("doc::11").
        startIndexField("f3").
        startElement(-27).addStr("zz").endElement().
        endField();
    doc = b.endDocument();
    inv.invertDocument(11, *doc);
    invertThreads.sync();
    myPushDocument(inv, d);
    pushThreads.sync();

    b.startDocument("doc::12").
        startIndexField("f3").
        startElement(0).addStr("zz0").endElement().
        endField();
    doc = b.endDocument();
    inv.invertDocument(12, *doc);
    invertThreads.sync();
    myPushDocument(inv, d);
    pushThreads.sync();

    IndexBuilder ib(schema);
    vespalib::string dump2dir = prefix + "dump2";
    ib.setPrefix(dump2dir);
    uint32_t numDocs = 12 + 1;
    uint32_t numWords = d.getNumUniqueWords();
    bool dynamicKPosOcc = false;
    TuneFileIndexing tuneFileIndexing;
    TuneFileSearch tuneFileSearch;
    DummyFileHeaderContext fileHeaderContext;
    if (directio) {
        tuneFileIndexing._read.setWantDirectIO();
        tuneFileIndexing._write.setWantDirectIO();
        tuneFileSearch._read.setWantDirectIO();
    }
    if (readmmap)
        tuneFileSearch._read.setWantMemoryMap();
    ib.open(numDocs, numWords, tuneFileIndexing, fileHeaderContext);
    d.dump(ib);
    ib.close();

    vespalib::string tsName = dump2dir + "/.teststamp";
    typedef search::FileKit FileKit;
    EXPECT_TRUE(FileKit::createStamp(tsName));
    EXPECT_TRUE(FileKit::hasStamp(tsName));
    EXPECT_TRUE(FileKit::removeStamp(tsName));
    EXPECT_FALSE(FileKit::hasStamp(tsName));

    do {
        DiskIndex dw2(prefix + "dump2");
        if (!EXPECT_TRUE(dw2.setup(tuneFileSearch)))
            break;
        TEST_DO(validateDiskIndex(dw2, true, true));
    } while (0);

    do {
        std::vector<vespalib::string> sources;
        SelectorArray selector(numDocs, 0);
        sources.push_back(prefix + "dump2");
        if (!EXPECT_TRUE(Fusion::merge(schema,
                                       prefix + "dump3",
                                       sources, selector,
                                       dynamicKPosOcc,
                                       tuneFileIndexing,
                                       fileHeaderContext)))
            return;
    } while (0);
    do {
        DiskIndex dw3(prefix + "dump3");
        if (!EXPECT_TRUE(dw3.setup(tuneFileSearch)))
            break;
        TEST_DO(validateDiskIndex(dw3, true, true));
    } while (0);
    do {
        std::vector<vespalib::string> sources;
        SelectorArray selector(numDocs, 0);
        sources.push_back(prefix + "dump3");
        if (!EXPECT_TRUE(Fusion::merge(schema2,
                                       prefix + "dump4",
                                       sources, selector,
                                       dynamicKPosOcc,
                                       tuneFileIndexing,
                                       fileHeaderContext)))
            return;
    } while (0);
    do {
        DiskIndex dw4(prefix + "dump4");
        if (!EXPECT_TRUE(dw4.setup(tuneFileSearch)))
            break;
        TEST_DO(validateDiskIndex(dw4, true, false));
    } while (0);
    do {
        std::vector<vespalib::string> sources;
        SelectorArray selector(numDocs, 0);
        sources.push_back(prefix + "dump3");
        if (!EXPECT_TRUE(Fusion::merge(schema3,
                                       prefix + "dump5",
                                       sources, selector,
                                       dynamicKPosOcc,
                                       tuneFileIndexing,
                                       fileHeaderContext)))
            return;
    } while (0);
    do {
        DiskIndex dw5(prefix + "dump5");
        if (!EXPECT_TRUE(dw5.setup(tuneFileSearch)))
            break;
        TEST_DO(validateDiskIndex(dw5, false, false));
    } while (0);
    do {
        std::vector<vespalib::string> sources;
        SelectorArray selector(numDocs, 0);
        sources.push_back(prefix + "dump3");
        if (!EXPECT_TRUE(Fusion::merge(schema,
                                       prefix + "dump6",
                                       sources, selector,
                                       !dynamicKPosOcc,
                                       tuneFileIndexing,
                                       fileHeaderContext)))
            return;
    } while (0);
    do {
        DiskIndex dw6(prefix + "dump6");
        if (!EXPECT_TRUE(dw6.setup(tuneFileSearch)))
            break;
        TEST_DO(validateDiskIndex(dw6, true, true));
    } while (0);
    do {
        std::vector<vespalib::string> sources;
        SelectorArray selector(numDocs, 0);
        sources.push_back(prefix + "dump2");
        if (!EXPECT_TRUE(Fusion::merge(schema,
                                       prefix + "dump3",
                                       sources, selector,
                                       dynamicKPosOcc,
                                       tuneFileIndexing,
                                       fileHeaderContext)))
            return;
    } while (0);
    do {
        DiskIndex dw3(prefix + "dump3");
        if (!EXPECT_TRUE(dw3.setup(tuneFileSearch)))
            break;
        TEST_DO(validateDiskIndex(dw3, true, true));
    } while (0);
}

Test::Test()
    : _schema()
{
    _schema.addIndexField(Schema::IndexField("f0", DataType::STRING));
    _schema.addIndexField(Schema::IndexField("f1", DataType::STRING));
    _schema.addIndexField(Schema::IndexField("f2", DataType::STRING, CollectionType::ARRAY));
    _schema.addIndexField(Schema::IndexField("f3", DataType::STRING, CollectionType::WEIGHTEDSET));
}

int
Test::Main()
{
    TEST_INIT("fusion_test");

    if (_argc > 0) {
        DummyFileHeaderContext::setCreator(_argv[0]);
    }

    TEST_DO(requireThatFusionIsWorking("", false, false));
    TEST_DO(requireThatFusionIsWorking("d", true, false));
    TEST_DO(requireThatFusionIsWorking("m", false, true));
    TEST_DO(requireThatFusionIsWorking("dm", true, true));

    TEST_DONE();
}

}

}

TEST_APPHOOK(search::diskindex::Test);
