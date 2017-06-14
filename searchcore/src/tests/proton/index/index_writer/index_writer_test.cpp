// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("index_writer_test");
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/searchcore/proton/index/index_writer.h>
#include <vespa/searchcore/proton/test/mock_index_manager.h>
#include <vespa/searchlib/index/docbuilder.h>

using namespace proton;
using namespace search;
using namespace search::index;
using namespace searchcorespi;

using document::Document;

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

struct MyIndexManager : public test::MockIndexManager
{
    typedef std::map<uint32_t, std::vector<SerialNum> > LidMap;
    LidMap puts;
    LidMap removes;
    SerialNum current;
    SerialNum flushed;
    SerialNum commitSerial;
    MyIndexManager() : puts(), removes(), current(0), flushed(0),
                       commitSerial(0)
    {
    }
    std::string getPut(uint32_t lid) {
        return toString(puts[lid]);
    }
    std::string getRemove(uint32_t lid) {
        return toString(removes[lid]);
    }
    // Implements IIndexManager
    virtual void putDocument(uint32_t lid, const Document &,
                             SerialNum serialNum) override {
        puts[lid].push_back(serialNum);
    }
    virtual void removeDocument(uint32_t lid,
                                SerialNum serialNum) override {
        removes[lid].push_back(serialNum);
    }
    virtual void commit(SerialNum serialNum,
                        OnWriteDoneType) override {
        commitSerial = serialNum;
    }
    virtual SerialNum getCurrentSerialNum() const override {
        return current;
    }
    virtual SerialNum getFlushedSerialNum() const override {
        return flushed;
    }
};

struct Fixture
{
    IIndexManager::SP iim;
    MyIndexManager   &mim;
    IndexWriter      iw;
    Schema            schema;
    DocBuilder        builder;
    Document::UP      dummyDoc;
    Fixture()
        : iim(new MyIndexManager()),
          mim(static_cast<MyIndexManager &>(*iim)),
          iw(iim),
          schema(),
          builder(schema),
          dummyDoc(createDoc(1234)) // This content of this is not used
    {
    }
    Document::UP createDoc(uint32_t lid) {
        builder.startDocument(vespalib::make_string("doc:test:%u", lid));
        return builder.endDocument();
    }
    void put(SerialNum serialNum, const search::DocumentIdT lid) {
        iw.put(serialNum, *dummyDoc, lid);
        iw.commit(serialNum, std::shared_ptr<IDestructorCallback>());
    }
    void remove(SerialNum serialNum, const search::DocumentIdT lid) {
        iw.remove(serialNum, lid);
        iw.commit(serialNum, std::shared_ptr<IDestructorCallback>());
    }
};

TEST_F("require that index adapter ignores old operations", Fixture)
{
    f.mim.flushed = 10;
    f.put(8, 1);
    f.remove(9, 2);
    EXPECT_EQUAL("", f.mim.getPut(1));
    EXPECT_EQUAL("", f.mim.getRemove(2));
}

TEST_F("require that commit is forwarded to index manager", Fixture)
{
    f.iw.commit(10, std::shared_ptr<IDestructorCallback>());
    EXPECT_EQUAL(10u, f.mim.commitSerial);
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
