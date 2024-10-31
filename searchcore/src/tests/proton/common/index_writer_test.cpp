// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/index/index_writer.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/searchcore/proton/test/mock_index_manager.h>
#include <vespa/searchlib/test/doc_builder.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace proton;
using namespace search;
using namespace search::index;
using namespace searchcorespi;
using search::test::DocBuilder;
using vespalib::IDestructorCallback;

using document::Document;

namespace {
std::string
toString(const std::vector<SerialNum> &vec)
{
    std::ostringstream oss;
    for (size_t i = 0; i < vec.size(); ++i) {
        if (i > 0) oss << ",";
        oss << vec[i];
    }
    return oss.str();
}

struct MyIndexManager : public proton::test::MockIndexManager
{
    using LidMap = std::map<uint32_t, std::vector<SerialNum> >;
    LidMap puts;
    LidMap removes;
    SerialNum current;
    SerialNum flushed;
    SerialNum commitSerial;
    uint32_t  wantedLidLimit;
    SerialNum compactSerial;
    MyIndexManager() noexcept;
    ~MyIndexManager() override;
    std::string getPut(uint32_t lid) {
        return toString(puts[lid]);
    }
    std::string getRemove(uint32_t lid) {
        return toString(removes[lid]);
    }
    // Implements IIndexManager
    void putDocument(uint32_t lid, const Document &, SerialNum serialNum, const OnWriteDoneType&) override {
        puts[lid].push_back(serialNum);
    }
    void removeDocuments(LidVector lids, SerialNum serialNum) override {
        for (uint32_t lid : lids) {
            removes[lid].push_back(serialNum);
        }
    }
    void commit(SerialNum serialNum, const OnWriteDoneType&) override {
        commitSerial = serialNum;
    }
    SerialNum getCurrentSerialNum() const override {
        return current;
    }
    SerialNum getFlushedSerialNum() const override {
        return flushed;
    }
    void compactLidSpace(uint32_t lidLimit, SerialNum serialNum) override {
        wantedLidLimit = lidLimit;
        compactSerial = serialNum;
    }
};

MyIndexManager::MyIndexManager() noexcept
    : puts(),
      removes(),
      current(0),
      flushed(0),
      commitSerial(0),
      wantedLidLimit(0),
      compactSerial(0)
{
}

MyIndexManager::~MyIndexManager() = default;

}

class IndexWriterTest : public ::testing::Test
{
protected:
    IIndexManager::SP iim;
    MyIndexManager   &mim;
    IndexWriter      iw;
    DocBuilder   builder;
    Document::UP      dummyDoc;
    IndexWriterTest();
    ~IndexWriterTest() override;
    Document::UP createDoc(uint32_t lid) {
        return builder.make_document(vespalib::make_string("id:ns:searchdocument::%u", lid));
    }
    void put(SerialNum serialNum, const search::DocumentIdT lid) {
        iw.put(serialNum, *dummyDoc, lid, {});
        iw.commit(serialNum, std::shared_ptr<IDestructorCallback>());
    }
    void remove(SerialNum serialNum, const search::DocumentIdT lid) {
        iw.remove(serialNum, lid);
        iw.commit(serialNum, std::shared_ptr<IDestructorCallback>());
    }
};

IndexWriterTest::IndexWriterTest()
    : ::testing::Test(),
      iim(std::make_shared<MyIndexManager>()),
      mim(static_cast<MyIndexManager &>(*iim)),
      iw(iim),
      builder(),
      dummyDoc(createDoc(1234)) // This content of this is not used
{
}

IndexWriterTest::~IndexWriterTest() = default;

TEST_F(IndexWriterTest, require_that_index_writer_ignores_old_operations)
{
    mim.flushed = 10;
    put(8, 1);
    remove(9, 2);
    EXPECT_EQ("", mim.getPut(1));
    EXPECT_EQ("", mim.getRemove(2));
}

TEST_F(IndexWriterTest, require_that_commit_is_forwarded_to_index_manager)
{
    iw.commit(10, std::shared_ptr<IDestructorCallback>());
    EXPECT_EQ(10u, mim.commitSerial);
}

TEST_F(IndexWriterTest, require_that_compactLidSpace_is_forwarded_to_index_manager)
{
    iw.compactLidSpace(4, 2);
    EXPECT_EQ(2u, mim.wantedLidLimit);
    EXPECT_EQ(4u, mim.compactSerial);
}

TEST_F(IndexWriterTest, require_that_old_compactLidSpace_is_not_forwarded_to_index_manager)
{
    mim.flushed = 10;
    iw.compactLidSpace(4, 2);
    EXPECT_EQ(0u, mim.wantedLidLimit);
    EXPECT_EQ(0u, mim.compactSerial);
}
