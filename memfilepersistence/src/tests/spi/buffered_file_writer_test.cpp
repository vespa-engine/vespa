// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/memfilepersistence/mapper/bufferedfilewriter.h>
#include <vespa/memfilepersistence/mapper/buffer.h>
#include <vespa/vespalib/io/fileutil.h>

namespace storage {
namespace memfile {

class BufferedFileWriterTest : public CppUnit::TestFixture
{
public:
    void noImplicitFlushingWhenDestructing();

    CPPUNIT_TEST_SUITE(BufferedFileWriterTest);
    CPPUNIT_TEST(noImplicitFlushingWhenDestructing);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(BufferedFileWriterTest);

namespace {

// Partial mock of vespalib::File. Unfortunately, there's currently no
// base interface to implement so have to override a class that already has
// implementation code present.
class MockFile : public vespalib::File
{
public:
    bool _didWrite;

    MockFile(const std::string& filename)
        : File(filename),
          _didWrite(false)
    {
    }

    void open(int flags, bool autoCreateDirectories) override {
        (void) flags;
        (void) autoCreateDirectories;
        // Don't do anything here to prevent us from actually opening a file
        // on disk.
    }

    off_t write(const void *buf, size_t bufsize, off_t offset) override {
        (void) buf;
        (void) bufsize;
        (void) offset;
        _didWrite = true;
        return 0;
    }
};

}

void
BufferedFileWriterTest::noImplicitFlushingWhenDestructing()
{
    MockFile file("foo");
    {
        Buffer buffer(1024);
        BufferedFileWriter writer(file, buffer, buffer.getSize());
        // Do a buffered write. This fits well within the buffer and should
        // consequently not be immediately written out to the backing file.
        writer.write("blarg", 5);
        // Escape scope without having flushed anything.
    }
    // Since BufferedFileWriter is meant to be used with O_DIRECT files,
    // flushing just implies writing rather than syncing (this is a half truth
    // since you still sync directories etc to ensure metadata is written, but
    // this constrained assumption works fine in the context of this test).
    CPPUNIT_ASSERT(!file._didWrite);
}

} // memfile
} // storage

