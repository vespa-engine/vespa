// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <cppunit/extensions/HelperMacros.h>
#include <iostream>
#include <string>
#include <vespa/storage/storageutil/recordflatfile.h>

using namespace document;
using namespace storage;
using namespace std;
using namespace document;

class RecordFlatFile_Test : public CppUnit::TestFixture {
  CPPUNIT_TEST_SUITE(RecordFlatFile_Test);
  CPPUNIT_TEST(testAdd);
  CPPUNIT_TEST(testUpdate);
  CPPUNIT_TEST(testRemove);
  CPPUNIT_TEST(testExists);
  CPPUNIT_TEST(testGetRecord);
  CPPUNIT_TEST(testClear);
  CPPUNIT_TEST(testSimpleUsage);
  CPPUNIT_TEST(testValid);
  CPPUNIT_TEST_SUITE_END();

  string _testFile;
  unsigned int _chunkSize;

  void setupTestFile();

public:
  void setUp();

    RecordFlatFile_Test(void)
        : _testFile(),
          _chunkSize(0)
    {
    }

protected:
  void testAdd();
  void testUpdate();
  void testRemove();
  void testExists();
  void testGetRecord();
  void testClear();
  void testSimpleUsage();
  void testValid();
};

CPPUNIT_TEST_SUITE_REGISTRATION(RecordFlatFile_Test);

namespace {

    const bool debug = false;

    class MyRecord {
    private:
        unsigned int _id;
        unsigned int _value;
        unsigned int _valid;

    public:
        MyRecord(void)
        : _id(0u),
          _value(0u),
          _valid(0u)
        {
        }
        MyRecord(unsigned int id, unsigned int value, bool valid = true)
            : _id(id), _value(value), _valid(valid ? 0 : 0xFFFFFFFF) {}

        const unsigned int& getId() const { return _id; }
        unsigned int getValue() const { return _value; }
        void setValue(unsigned int value) { _value = value; }
        bool isValid() const { return (_valid == 0); }

        bool operator==(const MyRecord& record) const {
            return (_id == record._id && _value == record._value);
        }
    };

    ostream& operator<<(ostream& out, MyRecord record) {
        out << "MyRecord(" << record.getId() << ", " << record.getValue()
            << ")";
        return out;
    }

    class BlockMessage {
    private:
        string _name;
        static unsigned int _indent;

    public:
        BlockMessage(const string& name) : _name(name) {
            if (debug) {
                for (unsigned int i=0; i<_indent; i++) cout << "  ";
                cout << "Block started: " << _name << "\n" << flush;
            }
            _indent++;
        }
        ~BlockMessage() {
            _indent--;
            if (debug) {
                for (unsigned int i=0; i<_indent; i++) cout << "  ";
                cout << "Block completed: " << _name << "\n" << flush;
            }
        }
    };

    unsigned int BlockMessage::_indent(0);

}

void RecordFlatFile_Test::setUp() {
    _testFile = "recordflatfile.testfile";
    _chunkSize = 4;
}

void RecordFlatFile_Test::setupTestFile() {
    BlockMessage message("setupTestFile()");
    RecordFlatFile<MyRecord, unsigned int> flatfile(_testFile, _chunkSize);
    flatfile.clear();
    for (unsigned int i=1; i<=8; ++i) {
        flatfile.add(MyRecord(i, 10+i));
    }
    CPPUNIT_ASSERT_EQUAL(8u, flatfile.getSize());
    for (unsigned int i=1; i<=8; ++i) {
        CPPUNIT_ASSERT_EQUAL(MyRecord(i, 10+i), *flatfile[i-1]);
    }
}


void RecordFlatFile_Test::testAdd() {
    BlockMessage message("testAdd()");
    setupTestFile();
    RecordFlatFile<MyRecord, unsigned int> flatfile(_testFile, _chunkSize);
    flatfile.add(MyRecord(9, 19));
    CPPUNIT_ASSERT_EQUAL(9u, flatfile.getSize());
    CPPUNIT_ASSERT_EQUAL(MyRecord(1, 11), *flatfile[0]);
    CPPUNIT_ASSERT_EQUAL(MyRecord(2, 12), *flatfile[1]);
    CPPUNIT_ASSERT_EQUAL(MyRecord(7, 17), *flatfile[6]);
    CPPUNIT_ASSERT_EQUAL(MyRecord(8, 18), *flatfile[7]);
    CPPUNIT_ASSERT_EQUAL(MyRecord(9, 19), *flatfile[8]);
}

void RecordFlatFile_Test::testUpdate() {
    BlockMessage message("testUpdate()");
    setupTestFile();
    RecordFlatFile<MyRecord, unsigned int> flatfile(_testFile, _chunkSize);
    CPPUNIT_ASSERT(!flatfile.update(MyRecord(0, 20)));
    CPPUNIT_ASSERT(flatfile.update(MyRecord(4, 19)));
    CPPUNIT_ASSERT_EQUAL(8u, flatfile.getSize());
    CPPUNIT_ASSERT_EQUAL(MyRecord(1, 11), *flatfile[0]);
    CPPUNIT_ASSERT_EQUAL(MyRecord(3, 13), *flatfile[2]);
    CPPUNIT_ASSERT_EQUAL(MyRecord(4, 19), *flatfile[3]);
    CPPUNIT_ASSERT_EQUAL(MyRecord(5, 15), *flatfile[4]);
    CPPUNIT_ASSERT_EQUAL(MyRecord(8, 18), *flatfile[7]);
}

void RecordFlatFile_Test::testRemove() {
    BlockMessage message("testRemove()");
    setupTestFile();
    RecordFlatFile<MyRecord, unsigned int> flatfile(_testFile, _chunkSize);
    flatfile.remove(3);
    CPPUNIT_ASSERT_EQUAL(7u, flatfile.getSize());
    CPPUNIT_ASSERT_EQUAL(MyRecord(1, 11), *flatfile[0]);
    CPPUNIT_ASSERT_EQUAL(MyRecord(2, 12), *flatfile[1]);
    CPPUNIT_ASSERT_EQUAL(MyRecord(8, 18), *flatfile[2]);
    CPPUNIT_ASSERT_EQUAL(MyRecord(4, 14), *flatfile[3]);
    CPPUNIT_ASSERT_EQUAL(MyRecord(5, 15), *flatfile[4]);
    CPPUNIT_ASSERT_EQUAL(MyRecord(6, 16), *flatfile[5]);
    CPPUNIT_ASSERT_EQUAL(MyRecord(7, 17), *flatfile[6]);
}

void RecordFlatFile_Test::testExists() {
    BlockMessage message("testExists()");
    setupTestFile();
    RecordFlatFile<MyRecord, unsigned int> flatfile(_testFile, _chunkSize);
    CPPUNIT_ASSERT(flatfile.exists(3));
    CPPUNIT_ASSERT(flatfile.exists(1));
    CPPUNIT_ASSERT(!flatfile.exists(11));
    CPPUNIT_ASSERT(flatfile.exists(6));
    CPPUNIT_ASSERT(flatfile.exists(5));
    CPPUNIT_ASSERT(!flatfile.exists(0));
}

void RecordFlatFile_Test::testGetRecord() {
    BlockMessage message("testGetRecord()");
    setupTestFile();
    RecordFlatFile<MyRecord, unsigned int> flatfile(_testFile, _chunkSize);
    CPPUNIT_ASSERT_EQUAL(MyRecord(4, 14), *flatfile.getRecord(4));
    CPPUNIT_ASSERT(flatfile.getRecord(0).get() == 0);
}

void RecordFlatFile_Test::testClear() {
    try{
        BlockMessage message("testClear()");
        setupTestFile();
        RecordFlatFile<MyRecord, unsigned int> flatfile(_testFile, _chunkSize);
        flatfile.clear();
        struct stat filestats;
        CPPUNIT_ASSERT(stat(_testFile.c_str(), &filestats) == -1);
    } catch (exception& e) {
        cerr << "Caught exception '" << e.what() << "' in testClear()" << endl;
        throw;
    }
}

void RecordFlatFile_Test::testSimpleUsage()
{
    BlockMessage message("testSimpleUsage()");
    RecordFlatFile<MyRecord, unsigned int> flatfile("recordflatfile.testfile");
    flatfile.clear();

    CPPUNIT_ASSERT_EQUAL(false, flatfile.exists(34u));
    CPPUNIT_ASSERT_EQUAL((MyRecord*) 0, flatfile.getRecord(23u).get());

    MyRecord record1(12, 54);
    MyRecord record2(34, 62);

    flatfile.add(record1);
    flatfile.add(record2);

    CPPUNIT_ASSERT_EQUAL(true, flatfile.exists(12u));
    CPPUNIT_ASSERT_EQUAL((MyRecord*) 0, flatfile.getRecord(23u).get());
    unique_ptr<MyRecord> result(flatfile.getRecord(34u));
    CPPUNIT_ASSERT(result.get() != 0);
    CPPUNIT_ASSERT_EQUAL(62u, result->getValue());

    record2.setValue(67);
    flatfile.update(record2);

    unique_ptr<MyRecord> result2(flatfile.getRecord(34u));
    CPPUNIT_ASSERT(result2.get() != 0);
    CPPUNIT_ASSERT_EQUAL(67u, result2->getValue());

    flatfile.remove(12);
    CPPUNIT_ASSERT_EQUAL(false, flatfile.exists(12u));

    flatfile.clear();
    CPPUNIT_ASSERT_EQUAL(false, flatfile.exists(34u));
}

void RecordFlatFile_Test::testValid()
{
    BlockMessage message("testValid()");
    RecordFlatFile<MyRecord, unsigned int> flatfile("recordflatfile.testfile");
    flatfile.clear();

    MyRecord record1(12, 54, true);
    MyRecord record2(34, 62, false);
    MyRecord record3(15, 69, true);
    MyRecord record4(50, 93, false);

      // Test that valid entries doesn't generate errors
    flatfile.add(record1);
    CPPUNIT_ASSERT(!flatfile.errorsFound());
    CPPUNIT_ASSERT_EQUAL((size_t) 0, flatfile.getErrors().size());

      // Test that invalid entries do
    flatfile.add(record2);
    CPPUNIT_ASSERT(flatfile.errorsFound());
    CPPUNIT_ASSERT_EQUAL((size_t) 1, flatfile.getErrors().size());
    string expected("Adding invalid record '34' to file "
                    "recordflatfile.testfile.");
    CPPUNIT_ASSERT_EQUAL(expected, *flatfile.getErrors().begin());

      // Checking that errors are kept if not cleared
    flatfile.add(record3);
    CPPUNIT_ASSERT_EQUAL((size_t) 1, flatfile.getErrors().size());
    CPPUNIT_ASSERT_EQUAL(expected, *flatfile.getErrors().begin());

      // Checking that clearing errors work
    flatfile.clearErrors();
    CPPUNIT_ASSERT_EQUAL((size_t) 0, flatfile.getErrors().size());

    flatfile.add(record4);
    flatfile.clearErrors();

      // Checking that entries read in get method generates warning
    unique_ptr<MyRecord> result(flatfile.getRecord(12));
    CPPUNIT_ASSERT_EQUAL((size_t) 0, flatfile.getErrors().size());
    result = flatfile.getRecord(15);
    CPPUNIT_ASSERT_EQUAL((size_t) 1, flatfile.getErrors().size());
    expected = "Found corrupted entry in file recordflatfile.testfile";
    CPPUNIT_ASSERT_EQUAL(expected, *flatfile.getErrors().begin());
    flatfile.clearErrors();

      // Checking that reading invalid entries generate exception
    try{
        result = flatfile.getRecord(50);
        CPPUNIT_FAIL("Expected exception");
    } catch (IoException& e) {
        expected = "IoException(): Entry requested '50' is corrupted in file "
                   "recordflatfile.testfile at getRecord in";
        string actual(e.what());
        if (actual.size() > expected.size())
            actual = actual.substr(0, expected.size());
        CPPUNIT_ASSERT_EQUAL(expected, actual);
    }
    CPPUNIT_ASSERT_EQUAL((size_t) 1, flatfile.getErrors().size());
    expected = "Found corrupted entry in file recordflatfile.testfile";
    CPPUNIT_ASSERT_EQUAL(expected, *flatfile.getErrors().begin());
    flatfile.clearErrors();

      // Check that you get warning when deleting if last entry is invalid
    flatfile.remove(12);
    CPPUNIT_ASSERT_EQUAL((size_t) 1, flatfile.getErrors().size());
    expected = "Last entry in file recordflatfile.testfile is invalid";
    CPPUNIT_ASSERT_EQUAL(expected, *flatfile.getErrors().begin());

    flatfile.clear();
}
