// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/data/memorydatastore.h>
#include <vespa/vespalib/stllike/asciistream.h>

using namespace vespalib;

class MemoryDataStoreTest : public vespalib::TestApp
{
private:
    void testMemoryDataStore();
    void testVariableSizeVector();
public:
    int Main() override;
};

void
MemoryDataStoreTest::testMemoryDataStore()
{
    MemoryDataStore s(alloc::Alloc::alloc(256));
    std::vector<MemoryDataStore::Reference> v;
    v.push_back(s.push_back("mumbo", 5));
    for (size_t i(0); i < 50; i++) {
        v.push_back(s.push_back("mumbo", 5));
        EXPECT_EQUAL(static_cast<const char *>(v[i].data()) + 5, v[i+1].data());
    }
    v.push_back(s.push_back("mumbo", 5));
    EXPECT_EQUAL(52ul, v.size());
    EXPECT_NOT_EQUAL(static_cast<const char *>(v[50].data()) + 5, v[51].data());
    for (size_t i(0); i < v.size(); i++) {
        EXPECT_EQUAL(0, memcmp("mumbo", v[i].data(), 5));
    }
}

void
MemoryDataStoreTest::testVariableSizeVector()
{
    VariableSizeVector v(20000, 5*20000);
    for (size_t i(0); i < 10000; i++) {
        asciistream os;
        os << i;
        v.push_back(os.str().data(), os.str().size());
    }
    for (size_t i(0); i < v.size(); i++) {
        asciistream os;
        os << i;
        EXPECT_EQUAL(os.str().size(), v[i].size());
        EXPECT_EQUAL(0, memcmp(os.str().data(), v[i].data(), os.str().size()));
    }
    size_t i(0);
    for (auto it(v.begin()), mt(v.end()); it != mt; it++, i++) {
        asciistream os;
        os << i;
        EXPECT_EQUAL(os.str().size(), it->size());
        EXPECT_EQUAL(0, memcmp(os.str().data(), (*it).data(), os.str().size()));
    }
    
}

int
MemoryDataStoreTest::Main()
{
    TEST_INIT("data_test");
    testMemoryDataStore();
    testVariableSizeVector();

    TEST_DONE();
}

TEST_APPHOOK(MemoryDataStoreTest);

