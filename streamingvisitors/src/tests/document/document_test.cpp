// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/vsm/common/storagedocument.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/stllike/asciistream.h>

using namespace document;

namespace vsm {

TEST(DocumentTest, storage_document)
{
    DocumentType dt("testdoc", 0);

    Field fa("a", 0, *DataType::STRING);
    Field fb("b", 1, *DataType::STRING);
    dt.addField(fa);
    dt.addField(fb);

    auto doc = document::Document::make_without_repo(dt, DocumentId());
    doc->setValue(fa, StringFieldValue("foo"));
    doc->setValue(fb, StringFieldValue("bar"));

    SharedFieldPathMap fpmap(new FieldPathMapT());
    fpmap->emplace_back();
    dt.buildFieldPath(fpmap->back(),"a");
    fpmap->emplace_back();
    dt.buildFieldPath(fpmap->back(), "b");
    fpmap->emplace_back();
    ASSERT_TRUE((*fpmap)[0].size() == 1);
    ASSERT_TRUE((*fpmap)[1].size() == 1);
    ASSERT_TRUE((*fpmap)[2].size() == 0);

    StorageDocument sdoc(std::move(doc), fpmap, 3);
    ASSERT_TRUE(sdoc.valid());

    EXPECT_EQ("foo", sdoc.getField(0)->getAsString());
    EXPECT_EQ("bar", sdoc.getField(1)->getAsString());
    EXPECT_TRUE(sdoc.getField(2) == nullptr);
    // test caching
    EXPECT_EQ("foo", sdoc.getField(0)->getAsString());
    EXPECT_EQ("bar", sdoc.getField(1)->getAsString());
    EXPECT_TRUE(sdoc.getField(2) == nullptr);

    // set new values
    EXPECT_TRUE(sdoc.setField(0, FieldValue::UP(new StringFieldValue("baz"))));
    EXPECT_EQ("baz", sdoc.getField(0)->getAsString());
    EXPECT_EQ("bar", sdoc.getField(1)->getAsString());
    EXPECT_TRUE(sdoc.getField(2) == nullptr);
    EXPECT_TRUE(sdoc.setField(1, FieldValue::UP(new StringFieldValue("qux"))));
    EXPECT_EQ("baz", sdoc.getField(0)->getAsString());
    EXPECT_EQ("qux", sdoc.getField(1)->getAsString());
    EXPECT_TRUE(sdoc.getField(2) == nullptr);
    EXPECT_TRUE(sdoc.setField(2, FieldValue::UP(new StringFieldValue("quux"))));
    EXPECT_EQ("baz", sdoc.getField(0)->getAsString());
    EXPECT_EQ("qux", sdoc.getField(1)->getAsString());
    EXPECT_EQ("quux", sdoc.getField(2)->getAsString());

    EXPECT_TRUE(!sdoc.setField(3, FieldValue::UP(new StringFieldValue("thud"))));

    SharedFieldPathMap fim;
    StorageDocument s2(std::make_unique<document::Document>(), fim, 0);
    EXPECT_EQ(IdString().toString(), s2.docDoc().getId().toString());
}

TEST(DocumentTest, string_field_id_t_map)
{
    StringFieldIdTMap m;
    EXPECT_EQ(0u, m.highestFieldNo());
    EXPECT_TRUE(StringFieldIdTMap::npos == m.fieldNo("unknown"));
    m.add("f1");
    EXPECT_EQ(0u, m.fieldNo("f1"));
    EXPECT_EQ(1u, m.highestFieldNo());
    m.add("f1");
    EXPECT_EQ(0u, m.fieldNo("f1"));
    EXPECT_EQ(1u, m.highestFieldNo());
    m.add("f2");
    EXPECT_EQ(1u, m.fieldNo("f2"));
    EXPECT_EQ(2u, m.highestFieldNo());
    m.add("f3", 7);
    EXPECT_EQ(7u, m.fieldNo("f3"));
    EXPECT_EQ(8u, m.highestFieldNo());
    m.add("f3");
    EXPECT_EQ(7u, m.fieldNo("f3"));
    EXPECT_EQ(8u, m.highestFieldNo());
    m.add("f2", 13);
    EXPECT_EQ(13u, m.fieldNo("f2"));
    EXPECT_EQ(14u, m.highestFieldNo());
    m.add("f4");
    EXPECT_EQ(3u, m.fieldNo("f4"));
    EXPECT_EQ(14u, m.highestFieldNo());
    {
        vespalib::asciistream os;
        StringFieldIdTMap t;
        t.add("b");
        t.add("a");
        os << t;
        EXPECT_EQ(vespalib::string("a = 1\nb = 0\n"), os.view());
    }
    
}

}

GTEST_MAIN_RUN_ALL_TESTS()
