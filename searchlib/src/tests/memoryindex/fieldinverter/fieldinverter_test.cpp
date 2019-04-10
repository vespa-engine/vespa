// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/index/docbuilder.h>
#include <vespa/searchlib/memoryindex/fieldinverter.h>
#include <vespa/searchlib/test/memoryindex/ordered_field_index_inserter.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/document/repo/fixedtyperepo.h>

namespace search {

using document::Document;
using index::DocBuilder;
using index::Schema;
using index::schema::CollectionType;
using index::schema::DataType;

using namespace index;

namespace memoryindex {


namespace {


Document::UP
makeDoc10(DocBuilder &b)
{
    b.startDocument("doc::10");
    b.startIndexField("f0").
        addStr("a").addStr("b").addStr("c").addStr("d").
        endField();
    return b.endDocument();
}


Document::UP
makeDoc11(DocBuilder &b)
{
    b.startDocument("doc::11");
    b.startIndexField("f0").
        addStr("a").addStr("b").addStr("e").addStr("f").
        endField();
    b.startIndexField("f1").
        addStr("a").addStr("g").
        endField();
    return b.endDocument();
}


Document::UP
makeDoc12(DocBuilder &b)
{
    b.startDocument("doc::12");
    b.startIndexField("f0").
        addStr("h").addStr("doc12").
        endField();
    return b.endDocument();
}


Document::UP
makeDoc13(DocBuilder &b)
{
    b.startDocument("doc::13");
    b.startIndexField("f0").
        addStr("i").addStr("doc13").
        endField();
    return b.endDocument();
}


Document::UP
makeDoc14(DocBuilder &b)
{
    b.startDocument("doc::14");
    b.startIndexField("f0").
        addStr("j").addStr("doc14").
        endField();
    return b.endDocument();
}


Document::UP
makeDoc15(DocBuilder &b)
{
    b.startDocument("doc::15");
    return b.endDocument();
}


Document::UP
makeDoc16(DocBuilder &b)
{
    b.startDocument("doc::16");
    b.startIndexField("f0").addStr("foo").addStr("bar").addStr("baz").
        addTermAnnotation("altbaz").addStr("y").addTermAnnotation("alty").
        addStr("z").endField();
    return b.endDocument();
}

}

struct Fixture
{
    Schema _schema;
    DocBuilder _b;
    std::vector<std::unique_ptr<FieldInverter> > _inverters;
    test::OrderedFieldIndexInserter _inserter;

    static Schema
    makeSchema()
    {
        Schema schema;
        schema.addIndexField(Schema::IndexField("f0", DataType::STRING));
        schema.addIndexField(Schema::IndexField("f1", DataType::STRING));
        schema.addIndexField(Schema::IndexField("f2", DataType::STRING, CollectionType::ARRAY));
        schema.addIndexField(Schema::IndexField("f3", DataType::STRING, CollectionType::WEIGHTEDSET));
        return schema;
    }

    Fixture()
        : _schema(makeSchema()),
          _b(_schema),
          _inverters(),
          _inserter()
    {
        for (uint32_t fieldId = 0; fieldId < _schema.getNumIndexFields();
             ++fieldId) {
            _inverters.push_back(std::make_unique<FieldInverter>(_schema,
                                                                 fieldId));
        }
    }

    void
    invertDocument(uint32_t docId, const Document &doc)
    {
        uint32_t fieldId = 0;
        for (auto &inverter : _inverters) {
            vespalib::stringref fieldName =
                _schema.getIndexField(fieldId).getName();
            inverter->invertField(docId, doc.getValue(fieldName));
            ++fieldId;
        }
    }

    void
    pushDocuments()
    {
        uint32_t fieldId = 0;
        for (auto &inverter : _inverters) {
            _inserter.setFieldId(fieldId);
            inverter->pushDocuments(_inserter);
            ++fieldId;
        }
    }

    void
    removeDocument(uint32_t docId) {
        for (auto &inverter : _inverters) {
            inverter->removeDocument(docId);
        }
    }
};


TEST_F("requireThatFreshInsertWorks", Fixture)
{
    f.invertDocument(10, *makeDoc10(f._b));
    f.pushDocuments();
    EXPECT_EQUAL("f=0,w=a,a=10,"
                 "w=b,a=10,"
                 "w=c,a=10,"
                 "w=d,a=10",
                 f._inserter.toStr());
}


TEST_F("requireThatMultipleDocsWork", Fixture)
{
    f.invertDocument(10, *makeDoc10(f._b));
    f.invertDocument(11, *makeDoc11(f._b));
    f.pushDocuments();
    EXPECT_EQUAL("f=0,w=a,a=10,a=11,"
                 "w=b,a=10,a=11,"
                 "w=c,a=10,w=d,a=10,"
                 "w=e,a=11,"
                 "w=f,a=11,"
                 "f=1,w=a,a=11,"
                 "w=g,a=11",
                 f._inserter.toStr());
}


TEST_F("requireThatRemoveWorks", Fixture)
{
    f._inverters[0]->remove("b", 10);
    f._inverters[0]->remove("a", 10);
    f._inverters[0]->remove("b", 11);
    f._inverters[2]->remove("c", 12);
    f._inverters[1]->remove("a", 10);
    f.pushDocuments();
    EXPECT_EQUAL("f=0,w=a,r=10,"
                 "w=b,r=10,r=11,"
                 "f=1,w=a,r=10,"
                 "f=2,w=c,r=12",
                 f._inserter.toStr());
}


TEST_F("requireThatReputWorks", Fixture)
{
    f.invertDocument(10, *makeDoc10(f._b));
    f.invertDocument(10, *makeDoc11(f._b));
    f.pushDocuments();
    EXPECT_EQUAL("f=0,w=a,a=10,"
                 "w=b,a=10,"
                 "w=e,a=10,"
                 "w=f,a=10,"
                 "f=1,w=a,a=10,"
                 "w=g,a=10",
                 f._inserter.toStr());
}


TEST_F("requireThatAbortPendingDocWorks", Fixture)
{
    Document::UP doc10 = makeDoc10(f._b);
    Document::UP doc11 = makeDoc11(f._b);
    Document::UP doc12 = makeDoc12(f._b);
    Document::UP doc13 = makeDoc13(f._b);
    Document::UP doc14 = makeDoc14(f._b);

    f.invertDocument(10, *doc10);
    f.invertDocument(11, *doc11);
    f.removeDocument(10);
    f.pushDocuments();
    EXPECT_EQUAL("f=0,w=a,a=11,"
                 "w=b,a=11,"
                 "w=e,a=11,"
                 "w=f,a=11,"
                 "f=1,w=a,a=11,"
                 "w=g,a=11",
                 f._inserter.toStr());

    f.invertDocument(10, *doc10);
    f.invertDocument(11, *doc11);
    f.invertDocument(12, *doc12);
    f.invertDocument(13, *doc13);
    f.invertDocument(14, *doc14);
    f.removeDocument(11);
    f.removeDocument(13);
    f._inserter.reset();
    f.pushDocuments();
    EXPECT_EQUAL("f=0,w=a,a=10,"
                 "w=b,a=10,"
                 "w=c,a=10,"
                 "w=d,a=10,"
                 "w=doc12,a=12,"
                 "w=doc14,a=14,"
                 "w=h,a=12,"
                 "w=j,a=14",
                 f._inserter.toStr());

    f.invertDocument(10, *doc10);
    f.invertDocument(11, *doc11);
    f.invertDocument(12, *doc12);
    f.invertDocument(13, *doc13);
    f.invertDocument(14, *doc14);
    f.removeDocument(11);
    f.removeDocument(12);
    f.removeDocument(13);
    f.removeDocument(14);
    f._inserter.reset();
    f.pushDocuments();
    EXPECT_EQUAL("f=0,w=a,a=10,"
                 "w=b,a=10,"
                 "w=c,a=10,"
                 "w=d,a=10",
                 f._inserter.toStr());


}


TEST_F("requireThatMixOfAddAndRemoveWorks", Fixture)
{
    f._inverters[0]->remove("a", 11);
    f._inverters[0]->remove("c", 9);
    f._inverters[0]->remove("d", 10);
    f._inverters[0]->remove("z", 12);
    f.invertDocument(10, *makeDoc10(f._b));
    f.pushDocuments();
    EXPECT_EQUAL("f=0,w=a,a=10,r=11,"
                 "w=b,a=10,"
                 "w=c,r=9,a=10,"
                 "w=d,r=10,a=10,"
                 "w=z,r=12",
                 f._inserter.toStr());
}


TEST_F("require that empty document can be inverted", Fixture)
{
    f.invertDocument(15, *makeDoc15(f._b));
    f.pushDocuments();
    EXPECT_EQUAL("",
                 f._inserter.toStr());
}

TEST_F("require that multiple words at same position works", Fixture)
{
    f.invertDocument(16, *makeDoc16(f._b));
    f._inserter.setVerbose();
    f.pushDocuments();
    EXPECT_EQUAL("f=0,"
                 "w=altbaz,a=16(e=0,w=1,l=5[2]),"
                 "w=alty,a=16(e=0,w=1,l=5[3]),"
                 "w=bar,a=16(e=0,w=1,l=5[1]),"
                 "w=baz,a=16(e=0,w=1,l=5[2]),"
                 "w=foo,a=16(e=0,w=1,l=5[0]),"
                 "w=y,a=16(e=0,w=1,l=5[3]),"
                 "w=z,a=16(e=0,w=1,l=5[4])",
                 f._inserter.toStr());
}


} // namespace memoryindex
} // namespace search

TEST_MAIN() { TEST_RUN_ALL(); }
