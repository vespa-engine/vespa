// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/* -*- mode: C++; coding: utf-8; -*- */


#include <vespa/searchlib/index/docbuilder.h>
#include <vespa/searchlib/memoryindex/fieldinverter.h>
#include <vespa/searchlib/memoryindex/urlfieldinverter.h>
#include <vespa/searchlib/test/memoryindex/ordereddocumentinserter.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/document/repo/fixedtyperepo.h>

namespace search {

using document::Document;
using index::schema::CollectionType;
using index::schema::DataType;

using namespace index;


namespace memoryindex {

namespace {
const vespalib::string url = "url";

Document::UP
makeDoc10Single(DocBuilder &b)
{
    b.startDocument("doc::10");
    b.startIndexField("url").
        startSubField("all").
        addUrlTokenizedString("http://www.example.com:81/fluke?ab=2#4").
        endSubField().
        startSubField("scheme").
        addUrlTokenizedString("http").
        endSubField().
        startSubField("host").
        addUrlTokenizedString("www.example.com").
        endSubField().
        startSubField("port").
        addUrlTokenizedString("81").
        endSubField().
        startSubField("path").
        addUrlTokenizedString("/fluke").
        addTermAnnotation("altfluke").
        endSubField().
        startSubField("query").
        addUrlTokenizedString("ab=2").
        endSubField().
        startSubField("fragment").
        addUrlTokenizedString("4").
        endSubField().
        endField();
    return b.endDocument();
}


Document::UP
makeDoc10Array(DocBuilder &b)
{
    b.startDocument("doc::10");
    b.startIndexField("url").
        startElement(1).
        startSubField("all").
        addUrlTokenizedString("http://www.example.com:82/fluke?ab=2#8").
        endSubField().
        startSubField("scheme").
        addUrlTokenizedString("http").
        endSubField().
        startSubField("host").
        addUrlTokenizedString("www.example.com").
        endSubField().
        startSubField("port").
        addUrlTokenizedString("82").
        endSubField().
        startSubField("path").
        addUrlTokenizedString("/fluke").
        addTermAnnotation("altfluke").
        endSubField().
        startSubField("query").
        addUrlTokenizedString("ab=2").
        endSubField().
        startSubField("fragment").
        addUrlTokenizedString("8").
        endSubField().
        endElement().
        startElement(1).
        startSubField("all").
        addUrlTokenizedString("http://www.flickr.com:82/fluke?ab=2#9").
        endSubField().
        startSubField("scheme").
        addUrlTokenizedString("http").
        endSubField().
        startSubField("host").
        addUrlTokenizedString("www.flickr.com").
        endSubField().
        startSubField("port").
        addUrlTokenizedString("82").
        endSubField().
        startSubField("path").
        addUrlTokenizedString("/fluke").
        endSubField().
        startSubField("query").
        addUrlTokenizedString("ab=2").
        endSubField().
        startSubField("fragment").
        addUrlTokenizedString("9").
        endSubField().
        endElement().
        endField();
    return b.endDocument();
}

Document::UP
makeDoc10WeightedSet(DocBuilder &b)
{
    b.startDocument("doc::10");
    b.startIndexField("url").
        startElement(4).
        startSubField("all").
        addUrlTokenizedString("http://www.example.com:83/fluke?ab=2#12").
        endSubField().
        startSubField("scheme").
        addUrlTokenizedString("http").
        endSubField().
        startSubField("host").
        addUrlTokenizedString("www.example.com").
        endSubField().
        startSubField("port").
        addUrlTokenizedString("83").
        endSubField().
        startSubField("path").
        addUrlTokenizedString("/fluke").
        addTermAnnotation("altfluke").
        endSubField().
        startSubField("query").
        addUrlTokenizedString("ab=2").
        endSubField().
        startSubField("fragment").
        addUrlTokenizedString("12").
        endSubField().
        endElement().
        startElement(7).
        startSubField("all").
        addUrlTokenizedString("http://www.flickr.com:85/fluke?ab=2#13").
        endSubField().
        startSubField("scheme").
        addUrlTokenizedString("http").
        endSubField().
        startSubField("host").
        addUrlTokenizedString("www.flickr.com").
        endSubField().
        startSubField("port").
        addUrlTokenizedString("85").
        endSubField().
        startSubField("path").
        addUrlTokenizedString("/fluke").
        endSubField().
        startSubField("query").
        addUrlTokenizedString("ab=2").
        endSubField().
        startSubField("fragment").
        addUrlTokenizedString("13").
        endSubField().
        endElement().
        endField();
    return b.endDocument();
}


Document::UP
makeDoc10Empty(DocBuilder &b)
{
    b.startDocument("doc::10");
    return b.endDocument();
}

}

struct Fixture
{
    Schema _schema;
    DocBuilder _b;
    std::vector<std::unique_ptr<FieldInverter> > _inverters;
    std::unique_ptr<UrlFieldInverter> _urlInverter;
    test::OrderedDocumentInserter _inserter;
    index::SchemaIndexFields _schemaIndexFields;

    static Schema
    makeSchema(Schema::CollectionType collectionType)
    {
        Schema schema;
        schema.addUriIndexFields(Schema::IndexField("url", DataType::STRING, collectionType));
        return schema;
    }

    Fixture(Schema::CollectionType collectionType)
        : _schema(makeSchema(collectionType)),
          _b(_schema),
          _inverters(),
          _urlInverter(),
          _inserter(),
          _schemaIndexFields()
    {
        _schemaIndexFields.setup(_schema);
        for (uint32_t fieldId = 0; fieldId < _schema.getNumIndexFields();
             ++fieldId) {
            _inverters.push_back(std::make_unique<FieldInverter>(_schema,
                                                                 fieldId));
        }
        index::UriField &urlField =
            _schemaIndexFields._uriFields.front();
        _urlInverter = std::make_unique<UrlFieldInverter>
                       (collectionType,
                        _inverters[urlField._all].get(),
                        _inverters[urlField._scheme].get(),
                        _inverters[urlField._host].get(),
                        _inverters[urlField._port].get(),
                        _inverters[urlField._path].get(),
                        _inverters[urlField._query].get(),
                        _inverters[urlField._fragment].get(),
                        _inverters[urlField._hostname].get());
    }

    void
    invertDocument(uint32_t docId, const Document &doc)
    {
        _urlInverter->invertField(docId, doc.getValue(url));
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
    enableAnnotations()
    {
        _urlInverter->setUseAnnotations(true);
    }
};


TEST_F("requireThatSingleUrlFieldWorks", Fixture(CollectionType::SINGLE))
{
    f.invertDocument(10, *makeDoc10Single(f._b));
    f.pushDocuments();
    EXPECT_EQUAL("f=0,"
                 "w=2,a=10,"
                 "w=4,a=10,"
                 "w=81,a=10,"
                 "w=ab,a=10,"
                 "w=com,a=10,"
                 "w=example,a=10,"
                 "w=fluke,a=10,"
                 "w=http,a=10,"
                 "w=www,a=10,"
                 "f=1,"
                 "w=http,a=10,"
                 "f=2,"
                 "w=com,a=10,"
                 "w=example,a=10,"
                 "w=www,a=10,"
                 "f=3,"
                 "w=81,a=10,"
                 "f=4,"
                 "w=fluke,a=10,"
                 "f=5,"
                 "w=2,a=10,"
                 "w=ab,a=10,"
                 "f=6,"
                 "w=4,a=10,"
                 "f=7,"
                 "w=EnDhOsT,a=10,"
                 "w=StArThOsT,a=10,"
                 "w=com,a=10,"
                 "w=example,a=10,"
                 "w=www,a=10",
                 f._inserter.toStr());
}


TEST_F("requireThatArrayUrlFieldWorks", Fixture(CollectionType::ARRAY))
{
    f.invertDocument(10, *makeDoc10Array(f._b));
    f.pushDocuments();
    EXPECT_EQUAL("f=0,"
                 "w=2,a=10,"
                 "w=8,a=10,"
                 "w=82,a=10,"
                 "w=9,a=10,"
                 "w=ab,a=10,"
                 "w=com,a=10,"
                 "w=example,a=10,"
                 "w=flickr,a=10,"
                 "w=fluke,a=10,"
                 "w=http,a=10,"
                 "w=www,a=10,"
                 "f=1,"
                 "w=http,a=10,"
                 "f=2,"
                 "w=com,a=10,"
                 "w=example,a=10,"
                 "w=flickr,a=10,"
                 "w=www,a=10,"
                 "f=3,"
                 "w=82,a=10,"
                 "f=4,"
                 "w=fluke,a=10,"
                 "f=5,"
                 "w=2,a=10,"
                 "w=ab,a=10,"
                 "f=6,"
                 "w=8,a=10,"
                 "w=9,a=10,"
                 "f=7,"
                 "w=EnDhOsT,a=10,"
                 "w=StArThOsT,a=10,"
                 "w=com,a=10,"
                 "w=example,a=10,"
                 "w=flickr,a=10,"
                 "w=www,a=10",
                 f._inserter.toStr());
}

TEST_F("requireThatWeightedSetFieldWorks", Fixture(CollectionType::WEIGHTEDSET))
{
    f.invertDocument(10, *makeDoc10WeightedSet(f._b));
    f.pushDocuments();
    EXPECT_EQUAL("f=0,"
                 "w=12,a=10,"
                 "w=13,a=10,"
                 "w=2,a=10,"
                 "w=83,a=10,"
                 "w=85,a=10,"
                 "w=ab,a=10,"
                 "w=com,a=10,"
                 "w=example,a=10,"
                 "w=flickr,a=10,"
                 "w=fluke,a=10,"
                 "w=http,a=10,"
                 "w=www,a=10,"
                 "f=1,"
                 "w=http,a=10,"
                 "f=2,"
                 "w=com,a=10,"
                 "w=example,a=10,"
                 "w=flickr,a=10,"
                 "w=www,a=10,"
                 "f=3,"
                 "w=83,a=10,"
                 "w=85,a=10,"
                 "f=4,"
                 "w=fluke,a=10,"
                 "f=5,"
                 "w=2,a=10,"
                 "w=ab,a=10,"
                 "f=6,"
                 "w=12,a=10,"
                 "w=13,a=10,"
                 "f=7,"
                 "w=EnDhOsT,a=10,"
                 "w=StArThOsT,a=10,"
                 "w=com,a=10,"
                 "w=example,a=10,"
                 "w=flickr,a=10,"
                 "w=www,a=10",
                 f._inserter.toStr());
}

TEST_F("requireThatAnnotatedSingleUrlFieldWorks", Fixture(CollectionType::SINGLE))
{
    f.enableAnnotations();
    f.invertDocument(10, *makeDoc10Single(f._b));
    f.pushDocuments();
    EXPECT_EQUAL("f=0,"
                 "w=2,a=10,"
                 "w=4,a=10,"
                 "w=81,a=10,"
                 "w=ab,a=10,"
                 "w=com,a=10,"
                 "w=example,a=10,"
                 "w=fluke,a=10,"
                 "w=http,a=10,"
                 "w=www,a=10,"
                 "f=1,"
                 "w=http,a=10,"
                 "f=2,"
                 "w=com,a=10,"
                 "w=example,a=10,"
                 "w=www,a=10,"
                 "f=3,"
                 "w=81,a=10,"
                 "f=4,"
                 "w=altfluke,a=10,"
                 "w=fluke,a=10,"
                 "f=5,"
                 "w=2,a=10,"
                 "w=ab,a=10,"
                 "f=6,"
                 "w=4,a=10,"
                 "f=7,"
                 "w=EnDhOsT,a=10,"
                 "w=StArThOsT,a=10,"
                 "w=com,a=10,"
                 "w=example,a=10,"
                 "w=www,a=10",
                 f._inserter.toStr());
}


TEST_F("requireThatAnnotatedArrayUrlFieldWorks", Fixture(CollectionType::ARRAY))
{
    f.enableAnnotations();
    f.invertDocument(10, *makeDoc10Array(f._b));
    f.pushDocuments();
    EXPECT_EQUAL("f=0,"
                 "w=2,a=10,"
                 "w=8,a=10,"
                 "w=82,a=10,"
                 "w=9,a=10,"
                 "w=ab,a=10,"
                 "w=com,a=10,"
                 "w=example,a=10,"
                 "w=flickr,a=10,"
                 "w=fluke,a=10,"
                 "w=http,a=10,"
                 "w=www,a=10,"
                 "f=1,"
                 "w=http,a=10,"
                 "f=2,"
                 "w=com,a=10,"
                 "w=example,a=10,"
                 "w=flickr,a=10,"
                 "w=www,a=10,"
                 "f=3,"
                 "w=82,a=10,"
                 "f=4,"
                 "w=altfluke,a=10,"
                 "w=fluke,a=10,"
                 "f=5,"
                 "w=2,a=10,"
                 "w=ab,a=10,"
                 "f=6,"
                 "w=8,a=10,"
                 "w=9,a=10,"
                 "f=7,"
                 "w=EnDhOsT,a=10,"
                 "w=StArThOsT,a=10,"
                 "w=com,a=10,"
                 "w=example,a=10,"
                 "w=flickr,a=10,"
                 "w=www,a=10",
                 f._inserter.toStr());
}

TEST_F("requireThatAnnotatedWeightedSetFieldWorks",
       Fixture(CollectionType::WEIGHTEDSET))
{
    f.enableAnnotations();
    f._inserter.setVerbose();
    f.invertDocument(10, *makeDoc10WeightedSet(f._b));
    f.pushDocuments();
    EXPECT_EQUAL("f=0,"
                 "w=12,a=10(e=0,w=4,l=9[8]),"
                 "w=13,a=10(e=1,w=7,l=9[8]),"
                 "w=2,a=10(e=0,w=4,l=9[7],e=1,w=7,l=9[7]),"
                 "w=83,a=10(e=0,w=4,l=9[4]),"
                 "w=85,a=10(e=1,w=7,l=9[4]),"
                 "w=ab,a=10(e=0,w=4,l=9[6],e=1,w=7,l=9[6]),"
                 "w=com,a=10(e=0,w=4,l=9[3],e=1,w=7,l=9[3]),"
                 "w=example,a=10(e=0,w=4,l=9[2]),"
                 "w=flickr,a=10(e=1,w=7,l=9[2]),"
                 "w=fluke,a=10(e=0,w=4,l=9[5],e=1,w=7,l=9[5]),"
                 "w=http,a=10(e=0,w=4,l=9[0],e=1,w=7,l=9[0]),"
                 "w=www,a=10(e=0,w=4,l=9[1],e=1,w=7,l=9[1]),"
                 "f=1,"
                 "w=http,a=10(e=0,w=4,l=1[0],e=1,w=7,l=1[0]),"
                 "f=2,"
                 "w=com,a=10(e=0,w=4,l=3[2],e=1,w=7,l=3[2]),"
                 "w=example,a=10(e=0,w=4,l=3[1]),"
                 "w=flickr,a=10(e=1,w=7,l=3[1]),"
                 "w=www,a=10(e=0,w=4,l=3[0],e=1,w=7,l=3[0]),"
                 "f=3,"
                 "w=83,a=10(e=0,w=4,l=1[0]),"
                 "w=85,a=10(e=1,w=7,l=1[0]),"
                 "f=4,"
                 "w=altfluke,a=10(e=0,w=4,l=1[0]),"
                 "w=fluke,a=10(e=0,w=4,l=1[0],e=1,w=7,l=1[0]),"
                 "f=5,"
                 "w=2,a=10(e=0,w=4,l=2[1],e=1,w=7,l=2[1]),"
                 "w=ab,a=10(e=0,w=4,l=2[0],e=1,w=7,l=2[0]),"
                 "f=6,"
                 "w=12,a=10(e=0,w=4,l=1[0]),"
                 "w=13,a=10(e=1,w=7,l=1[0]),"
                 "f=7,"
                 "w=EnDhOsT,a=10(e=0,w=4,l=5[4],e=1,w=7,l=5[4]),"
                 "w=StArThOsT,a=10(e=0,w=4,l=5[0],e=1,w=7,l=5[0]),"
                 "w=com,a=10(e=0,w=4,l=5[3],e=1,w=7,l=5[3]),"
                 "w=example,a=10(e=0,w=4,l=5[2]),"
                 "w=flickr,a=10(e=1,w=7,l=5[2]),"
                 "w=www,a=10(e=0,w=4,l=5[1],e=1,w=7,l=5[1])",
                 f._inserter.toStr());
}


TEST_F("requireThatEmptySingleFieldWorks", Fixture(CollectionType::SINGLE))
{
    f.invertDocument(10, *makeDoc10Empty(f._b));
    f.pushDocuments();
    EXPECT_EQUAL("", f._inserter.toStr());
}

TEST_F("requireThatEmptyArrayFieldWorks", Fixture(CollectionType::ARRAY))
{
    f.invertDocument(10, *makeDoc10Empty(f._b));
    f.pushDocuments();
    EXPECT_EQUAL("",
                 f._inserter.toStr());
}

TEST_F("requireThatEmptyWeightedSetFieldWorks", Fixture(CollectionType::WEIGHTEDSET))
{
    f.invertDocument(10, *makeDoc10Empty(f._b));
    f.pushDocuments();
    EXPECT_EQUAL("", f._inserter.toStr());
}

TEST_F("requireThatAnnotatedEmptySingleFieldWorks", Fixture(CollectionType::SINGLE))
{
    f.enableAnnotations();
    f.invertDocument(10, *makeDoc10Empty(f._b));
    f.pushDocuments();
    EXPECT_EQUAL("", f._inserter.toStr());
}

TEST_F("requireThatAnnotatedEmptyArrayFieldWorks", Fixture(CollectionType::ARRAY))
{
    f.enableAnnotations();
    f.invertDocument(10, *makeDoc10Empty(f._b));
    f.pushDocuments();
    EXPECT_EQUAL("", f._inserter.toStr());
}

TEST_F("requireThatAnnotatedEmptyWeightedSetFieldWorks", Fixture(CollectionType::WEIGHTEDSET))
{
    f.enableAnnotations();
    f.invertDocument(10, *makeDoc10Empty(f._b));
    f.pushDocuments();
    EXPECT_EQUAL("", f._inserter.toStr());
}

} // namespace memoryindex
} // namespace search

TEST_MAIN() { TEST_RUN_ALL(); }
