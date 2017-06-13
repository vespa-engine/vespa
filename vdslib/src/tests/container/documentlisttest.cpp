// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdslib/container/writabledocumentlist.h>
#include <vespa/document/base/testdocman.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/random.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <cppunit/extensions/HelperMacros.h>

using document::DocumentTypeRepo;
using document::readDocumenttypesConfig;
using vespalib::nbostream;

namespace vdslib {

struct WritableDocumentListTest : public CppUnit::TestFixture {

    void testSimple();
    void testAlignedWriting();
    void testSizeOf();
    void testReadJavaFile();
    void testGetSerializedSize();
    void testCopyEntry();
    void testOperationList();
    void testSetTimestamp();
    void testDifferentBuckets();

    CPPUNIT_TEST_SUITE(WritableDocumentListTest);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST(testAlignedWriting);
    CPPUNIT_TEST(testSizeOf);
    CPPUNIT_TEST(testReadJavaFile);
    CPPUNIT_TEST(testGetSerializedSize);
    CPPUNIT_TEST(testCopyEntry);
    CPPUNIT_TEST(testOperationList);
    CPPUNIT_TEST(testSetTimestamp);
    CPPUNIT_TEST(testDifferentBuckets);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(WritableDocumentListTest);

void WritableDocumentListTest::testDifferentBuckets()
{
    document::TestDocMan docman;
    std::vector<char> buffer(1024);
    WritableDocumentList block(docman.getTypeRepoSP(), &buffer[0], buffer.size());

    std::unique_ptr<document::Document> doc1 = docman.createDocument("This is a test", "userdoc:test:1234:1");
    block.addPut(*doc1);

    std::unique_ptr<document::Document> doc2 = docman.createDocument("This is a test", "userdoc:test:4567:1");
    try {
        block.addPut(*doc2);
        CPPUNIT_ASSERT(false);
    } catch (...) {
    }

    block.addRemove(document::DocumentId("userdoc:test:1234:2"));

    try {
        block.addRemove(document::DocumentId("userdoc:test:4567:2"));
        CPPUNIT_ASSERT(false);
    } catch (...) {
    }
}

void WritableDocumentListTest::testSimple()
{
    document::TestDocMan docman;
    std::vector<char> buffer(1024);
    std::vector<document::Document::SP > docs;
    for (uint32_t i=1; i<10; ++i) {
        docs.push_back(document::Document::SP(
                docman.createDocument("This is a test",
                    vespalib::make_string("userdoc:test:123456789:%d", i)
                ).release()));
    }

    WritableDocumentList block(docman.getTypeRepoSP(), &buffer[0], buffer.size());
    // begin() should now equal start()
    CPPUNIT_ASSERT(block.begin() == block.end());
    // Add docs in the easy way.
    block.addPut(*docs[0]);
    block.addRemove(docs[1]->getId());
    block.addPut(*docs[2], 0xfee1deadbabeb00bull);

    // Add the way slotfile will.
    WritableDocumentList::MetaEntry entry;
    entry.timestamp = 1234;
    entry.headerPos = 0;
    nbostream stream;
    docs[3]->serializeHeader(stream);
    entry.headerLen = stream.size();
    entry.bodyPos = entry.headerLen;
    stream.clear();
    docs[3]->serializeBody(stream);
    entry.bodyLen = stream.size();
    entry.flags = 0;

    CPPUNIT_ASSERT(block.countFree() > entry.headerLen + entry.bodyLen
                                            + sizeof(WritableDocumentList::MetaEntry));
    char *pos = block.prepareMultiput(1, entry.headerLen + entry.bodyLen);
    CPPUNIT_ASSERT(pos != 0);
    document::ByteBuffer bb(pos, entry.headerLen + entry.bodyLen);
    docs[3]->serializeHeader(bb);
    docs[3]->serializeBody(bb);
    std::vector<WritableDocumentList::MetaEntry> entries;
    entries.push_back(entry);
    block.commitMultiput(entries, pos);

        // Copy buffer someplace else to simulate serialize/deserialize
    std::vector<char> copy(buffer);

        // Get documents out again. Verify correctness..
    {
        WritableDocumentList::const_iterator it = block.begin();
            // First document putted
        CPPUNIT_ASSERT(it->valid());
        CPPUNIT_ASSERT(!it->isRemoveEntry());
        CPPUNIT_ASSERT_EQUAL((uint64_t)0, it->getTimestamp());

        std::unique_ptr<document::Document> doc(it->getDocument());
        CPPUNIT_ASSERT(doc.get());

        CPPUNIT_ASSERT_EQUAL(*docs[0], *doc);

        CPPUNIT_ASSERT(++it != block.end());
            // First document deleted
        CPPUNIT_ASSERT(it->valid());
        CPPUNIT_ASSERT(it->isRemoveEntry());
        CPPUNIT_ASSERT_EQUAL((uint64_t)0, it->getTimestamp());

        doc = it->getDocument();
        CPPUNIT_ASSERT(doc.get());

        CPPUNIT_ASSERT_EQUAL(docs[1]->getId(), doc->getId());
        CPPUNIT_ASSERT(++it != block.end());
            // Second document putted
        CPPUNIT_ASSERT(it->valid());
        CPPUNIT_ASSERT(!it->isRemoveEntry());
        CPPUNIT_ASSERT_EQUAL((uint64_t)0xfee1deadbabeb00bull,
                             it->getTimestamp());
        doc = it->getDocument();
        CPPUNIT_ASSERT(doc.get());

        CPPUNIT_ASSERT_EQUAL(*docs[2], *doc);
        CPPUNIT_ASSERT(++it != block.end());
            // Third document putted
        CPPUNIT_ASSERT(it->valid());
        CPPUNIT_ASSERT(!it->isRemoveEntry());
        CPPUNIT_ASSERT_EQUAL((uint64_t)1234, it->getTimestamp());

        doc = it->getDocument();
        CPPUNIT_ASSERT(doc.get());

        CPPUNIT_ASSERT_EQUAL(*docs[3], *doc);
        CPPUNIT_ASSERT(++it == block.end());
    }

        // Test downsizing
    CPPUNIT_ASSERT_EQUAL(621u, block.countFree());
    uint32_t requiredSize = block.getBufferSize() - block.countFree();
    std::vector<char> otherBuffer(requiredSize);
    DocumentList otherBlock(block, &otherBuffer[0], otherBuffer.size());
    CPPUNIT_ASSERT_EQUAL(403u, otherBlock.getBufferSize());
        // Get documents out again of other block. Verify correctness..
    {
        DocumentList::const_iterator it = otherBlock.begin();
            // First document putted
        CPPUNIT_ASSERT(it->valid());
        CPPUNIT_ASSERT(!it->isRemoveEntry());
        CPPUNIT_ASSERT_EQUAL((uint64_t)0, it->getTimestamp());

        std::unique_ptr<document::Document> doc(it->getDocument());
        CPPUNIT_ASSERT(doc.get());

        CPPUNIT_ASSERT_EQUAL(*docs[0], *doc);

        CPPUNIT_ASSERT(++it != block.end());
            // First document deleted
        CPPUNIT_ASSERT(it->valid());
        CPPUNIT_ASSERT(it->isRemoveEntry());
        CPPUNIT_ASSERT_EQUAL((uint64_t)0, it->getTimestamp());

        doc = it->getDocument();
        CPPUNIT_ASSERT(doc.get());

        CPPUNIT_ASSERT_EQUAL(docs[1]->getId(), doc->getId());
        CPPUNIT_ASSERT(++it != block.end());
            // Second document putted
        CPPUNIT_ASSERT(it->valid());
        CPPUNIT_ASSERT(!it->isRemoveEntry());
        CPPUNIT_ASSERT_EQUAL((uint64_t)0xfee1deadbabeb00bull, it->getTimestamp());
        doc = it->getDocument();
        CPPUNIT_ASSERT(doc.get());
        CPPUNIT_ASSERT_EQUAL(*docs[2], *doc);
        CPPUNIT_ASSERT(++it != block.end());
            // Third document putted
        CPPUNIT_ASSERT(it->valid());
        CPPUNIT_ASSERT(!it->isRemoveEntry());
        CPPUNIT_ASSERT_EQUAL((uint64_t)1234, it->getTimestamp());

        doc = it->getDocument();
        CPPUNIT_ASSERT(doc.get());
        CPPUNIT_ASSERT_EQUAL(*docs[3], *doc);
        CPPUNIT_ASSERT(++it == block.end());
    }

        // begin() should equal start() after clear
    block.clear();
    CPPUNIT_ASSERT(block.begin() == block.end());
}

void WritableDocumentListTest::testSetTimestamp()
{
    document::TestDocMan docman;
    std::vector<char> buffer(1024);

    WritableDocumentList block(docman.getTypeRepoSP(), &buffer[0], buffer.size());
    // begin() should now equal start()
    CPPUNIT_ASSERT(block.begin() == block.end());
    // Add docs in the easy way.
    std::unique_ptr<document::Document> doc(
            docman.createDocument("This is a test",
                                    vespalib::make_string("userdoc:test:123456789:t")));

    block.addPut(*doc);

    CPPUNIT_ASSERT(block.begin() != block.end());

    block.begin()->setTimestamp(1234);

    CPPUNIT_ASSERT_EQUAL((Timestamp)1234, block.begin()->getTimestamp());
}

void WritableDocumentListTest::testAlignedWriting()
{
    document::TestDocMan docman;
    vespalib::RandomGen randomizer(5123);
    std::vector<char> buffer(1024*1024);

    std::vector<document::Document::SP > docs;
    for (uint32_t i=1; i<10; ++i) {
        docs.push_back(document::Document::SP(
                docman.createDocument("Aligned writing test blaaaah",
                        vespalib::make_string("userdoc:test:123456789:%d", i)
                ).release()));
    }

    WritableDocumentList block(docman.getTypeRepoSP(), &buffer[0], buffer.size());

        // Add documents aligned the way slotfile will.
    for (uint32_t i=1; i<50; ++i) {
        std::vector<WritableDocumentList::MetaEntry> entries;
        uint32_t currentPos = 0;
        for (uint32_t j=0, n=randomizer.nextUint32(1,10); j<n; ++j) {
            WritableDocumentList::MetaEntry entry;
            entry.timestamp = i * 1000 + j;
            entry.headerPos = currentPos;
            entry.headerLen = randomizer.nextUint32(5, 50);
            entry.bodyPos = currentPos + entry.headerLen;
            entry.bodyLen = randomizer.nextUint32(0, 4000);
            entry.flags = 0;
            currentPos += entry.headerLen + entry.bodyLen;
            entries.push_back(entry);
        }
        currentPos += 512 - (currentPos % 512);
        CPPUNIT_ASSERT(currentPos % 512 == 0);

        CPPUNIT_ASSERT(block.countFree() > currentPos + entries.size()
                            * sizeof(WritableDocumentList::MetaEntry));
        char *pos = block.prepareMultiput(entries.size(), currentPos);
        CPPUNIT_ASSERT(pos != 0);
        CPPUNIT_ASSERT((pos - &buffer[0]) % 512 == 0);
        block.commitMultiput(entries, pos);
    }
}

void WritableDocumentListTest::testSizeOf()
{
    CPPUNIT_ASSERT_EQUAL(size_t(32), sizeof(WritableDocumentList::MetaEntry));

    std::string buffercont("This is a buffer of data we will create meta "
                           " entry from to verify binary compability");
    std::vector<char> buffer(buffercont.begin(), buffercont.end());
    CPPUNIT_ASSERT(buffer.size() > sizeof(WritableDocumentList::MetaEntry));

    WritableDocumentList::MetaEntry* e(reinterpret_cast<WritableDocumentList::MetaEntry*>(&buffer[0]));

    CPPUNIT_ASSERT_EQUAL(Timestamp(2338328219631577172ull), e->timestamp);
    CPPUNIT_ASSERT_EQUAL(uint32_t(1969365089), e->headerPos);
    CPPUNIT_ASSERT_EQUAL(uint32_t(1919247974), e->headerLen);
    CPPUNIT_ASSERT_EQUAL(uint32_t(543584032), e->bodyPos);
    CPPUNIT_ASSERT_EQUAL(uint32_t(1635017060), e->bodyLen);
    CPPUNIT_ASSERT_EQUAL(uint32_t(32), uint32_t(e->flags));
}

void WritableDocumentListTest::testReadJavaFile()
{
    DocumentTypeRepo::SP repo(new DocumentTypeRepo(readDocumenttypesConfig(TEST_PATH("../test/files/documenttypes.cfg"))));

    //read file
    int file = open(TEST_PATH("../test/files/documentlist-java.dat").c_str(), O_RDONLY);
    if (file == -1) {
        CPPUNIT_ASSERT(0);
    }

    uint32_t len = lseek(file, 0, SEEK_END);
    lseek(file, 0, SEEK_SET);

    vespalib::MallocPtr data(len);
    CPPUNIT_ASSERT_EQUAL((ssize_t) len, read(file, data, len));
    close(file);


    //create documentlist
    DocumentList block(repo, data.str(), len, true);

    CPPUNIT_ASSERT_EQUAL((uint32_t) 4, block.size());

    DocumentList::const_iterator it = block.begin();
    CPPUNIT_ASSERT(it->valid());
    CPPUNIT_ASSERT(!it->isRemoveEntry());
    CPPUNIT_ASSERT(!it->isBodyStripped());
    CPPUNIT_ASSERT(!it->isUpdateEntry());
    CPPUNIT_ASSERT_EQUAL((uint64_t)0, it->getTimestamp());
    std::unique_ptr<document::Document> doc(it->getDocument());
    CPPUNIT_ASSERT(doc.get());
    CPPUNIT_ASSERT_EQUAL(document::DocumentId("userdoc:foo:99999999:1"),
                         doc->getId());
    vespalib::string foo = "foo";
    CPPUNIT_ASSERT_EQUAL(foo, doc->getValue("headerstring")->getAsString());

    CPPUNIT_ASSERT(++it != block.end());

    CPPUNIT_ASSERT(it->valid());
    CPPUNIT_ASSERT(it->isRemoveEntry());
    CPPUNIT_ASSERT(!it->isBodyStripped());
    CPPUNIT_ASSERT(!it->isUpdateEntry());
    CPPUNIT_ASSERT_EQUAL((uint64_t)0, it->getTimestamp());
    doc = it->getDocument();
    CPPUNIT_ASSERT(doc.get());
    CPPUNIT_ASSERT_EQUAL(document::DocumentId("userdoc:foo:99999999:2"),
                         doc->getId());

    CPPUNIT_ASSERT(++it != block.end());

    CPPUNIT_ASSERT(it->valid());
    CPPUNIT_ASSERT(!it->isRemoveEntry());
    CPPUNIT_ASSERT(!it->isBodyStripped());
    CPPUNIT_ASSERT(!it->isUpdateEntry());
    CPPUNIT_ASSERT_EQUAL((uint64_t)0, it->getTimestamp());
    doc = it->getDocument();
    CPPUNIT_ASSERT(doc.get());
    CPPUNIT_ASSERT_EQUAL(document::DocumentId("userdoc:foo:99999999:3"),
                         doc->getId());
    CPPUNIT_ASSERT_EQUAL(5.5f, doc->getValue("bodyfloat")->getAsFloat());

    CPPUNIT_ASSERT(++it != block.end());

    CPPUNIT_ASSERT(it->valid());
    CPPUNIT_ASSERT(!it->isRemoveEntry());
    CPPUNIT_ASSERT(!it->isBodyStripped());
    CPPUNIT_ASSERT(it->isUpdateEntry());
    CPPUNIT_ASSERT_EQUAL((uint64_t)0, it->getTimestamp());
    document::DocumentUpdate::UP docUp = it->getUpdate();
    CPPUNIT_ASSERT(docUp.get());
    const document::AssignValueUpdate* valUp = dynamic_cast<const document::AssignValueUpdate*>(docUp->getUpdates().front().getUpdates().front().get());
    vespalib::string ballooooo = "ballooooo";
    CPPUNIT_ASSERT_EQUAL(ballooooo, valUp->getValue().getAsString());

    CPPUNIT_ASSERT(++it == block.end());
}

void WritableDocumentListTest::testGetSerializedSize() {
    document::TestDocMan docman;
    std::vector<char> buffer(1024);
    std::vector<document::Document::SP > docs;
    for (uint32_t i=1; i<3; ++i) {
        docs.push_back(document::Document::SP(
               docman.createDocument("This is a test, blah bloh bluh blih",
                       vespalib::make_string("userdoc:test:1298798789:%d", i)
                ).release()));
    }
    WritableDocumentList block(docman.getTypeRepoSP(), &buffer[0], buffer.size());
    // begin() should now equal start()
    CPPUNIT_ASSERT(block.begin() == block.end());
    // Add docs in the easy way.
    block.addPut(*docs[0]);
    block.addRemove(docs[1]->getId());

    WritableDocumentList::const_iterator it = block.begin();
    CPPUNIT_ASSERT_EQUAL((const uint32_t)(docs[0]->serialize()->getLength()
                                          + sizeof(DocumentList::MetaEntry)),
                         it->getSerializedSize());
}

void WritableDocumentListTest::testCopyEntry() {
    DocumentTypeRepo::SP repo(new DocumentTypeRepo(readDocumenttypesConfig(
            TEST_PATH("../test/files/documenttypes.cfg"))));

    //read file
    int file = open(TEST_PATH("../test/files/documentlist-java.dat").c_str(), O_RDONLY);
    if (file == -1) {
        CPPUNIT_ASSERT(0);
    }

    uint32_t len = lseek(file, 0, SEEK_END);
    lseek(file, 0, SEEK_SET);

    vespalib::MallocPtr data(len);
    CPPUNIT_ASSERT_EQUAL((ssize_t) len, read(file, data, len));
    close(file);


    //create documentlist
    DocumentList block(repo, data.str(), len, true);

    CPPUNIT_ASSERT_EQUAL((uint32_t) 4, block.size());

    //create a writabledocumentlist
    std::vector<char> buffer(1024);
    WritableDocumentList wrBlock(repo, &buffer[0], buffer.size());

    DocumentList::const_iterator it = block.begin();
    wrBlock.addEntry(*it);
    CPPUNIT_ASSERT_EQUAL((uint32_t) 1, wrBlock.size());

    ++it;
    wrBlock.addEntry(*it);
    CPPUNIT_ASSERT_EQUAL((uint32_t) 2, wrBlock.size());

    ++it;
    wrBlock.addEntry(*it);
    CPPUNIT_ASSERT_EQUAL((uint32_t) 3, wrBlock.size());

    ++it;
    wrBlock.addEntry(*it);
    CPPUNIT_ASSERT_EQUAL((uint32_t) 4, wrBlock.size());


    it = block.begin();
    DocumentList::const_iterator wrIt = wrBlock.begin();

    //test equality of first entry
    CPPUNIT_ASSERT_EQUAL(it->getFlags(), wrIt->getFlags());
    std::unique_ptr<document::Document> doc(it->getDocument());
    CPPUNIT_ASSERT(doc.get());

    std::unique_ptr<document::Document> wrDoc(wrIt->getDocument());
    CPPUNIT_ASSERT(wrDoc.get());
    CPPUNIT_ASSERT_EQUAL(*doc, *wrDoc);

    ++it;
    ++wrIt;

    //test equality of second entry
    CPPUNIT_ASSERT_EQUAL(it->getFlags(), wrIt->getFlags());
    doc = it->getDocument();
    CPPUNIT_ASSERT(doc.get());

    wrDoc = wrIt->getDocument();
    CPPUNIT_ASSERT(wrDoc.get());
    CPPUNIT_ASSERT_EQUAL(*doc, *wrDoc);

    ++it;
    ++wrIt;

    //test equality of third entry
    CPPUNIT_ASSERT_EQUAL(it->getFlags(), wrIt->getFlags());
    doc = it->getDocument();
    CPPUNIT_ASSERT(doc.get());

    wrDoc = wrIt->getDocument();
    CPPUNIT_ASSERT(wrDoc.get());
    CPPUNIT_ASSERT_EQUAL(*doc, *wrDoc);

    ++it;
    ++wrIt;

    //test equality of fourth entry
    CPPUNIT_ASSERT_EQUAL(it->getFlags(), wrIt->getFlags());
    document::DocumentUpdate::UP docUp = it->getUpdate();
    CPPUNIT_ASSERT(docUp.get());

    document::DocumentUpdate::UP wrDocUp = wrIt->getUpdate();
    CPPUNIT_ASSERT(wrDocUp.get());
    CPPUNIT_ASSERT_EQUAL(docUp->getId(), wrDocUp->getId());
}

void WritableDocumentListTest::testOperationList()
{
    document::TestDocMan docman;
    OperationList ol;
    for (uint32_t i=0; i<3000; ++i) {
        ol.addPut(docman.createDocument(
                    "This is a test, blah bloh bluh blih",
                    vespalib::make_string("userdoc:test:1298798789:%d", i)));
    }

    for (uint32_t i=5000; i<5900; ++i) {
        ol.addRemove(document::DocumentId(
                vespalib::make_string("userdoc:test:1298798789:%d", i)));
    }

    std::vector<char> buf(ol.getRequiredBufferSize());

    MutableDocumentList mdl(docman.getTypeRepoSP(), &(buf[0]), buf.size());
    mdl.addOperationList(ol);

    DocumentList::const_iterator it = mdl.begin();

    for (uint32_t i=0; i<3000; ++i, it++) {
        CPPUNIT_ASSERT_EQUAL(
                vespalib::make_string("userdoc:test:1298798789:%d", i),
                it->getDocument()->getId().toString());
        CPPUNIT_ASSERT(!it->isRemoveEntry());
    }

    for (uint32_t i=5000; i<5900; ++i, it++) {
        CPPUNIT_ASSERT_EQUAL(
                vespalib::make_string("userdoc:test:1298798789:%d", i),
                it->getDocument()->getId().toString());
        CPPUNIT_ASSERT(it->isRemoveEntry());
    }
}

}
