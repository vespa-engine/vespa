// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/bufferwriter.h>
#include <vespa/searchlib/util/drainingbufferwriter.h>
#include <vespa/searchlib/util/rand48.h>

namespace search {

namespace {

class StoreBufferWriter : public BufferWriter
{
    std::vector<char> _buf;
    std::vector<std::unique_ptr<std::vector<char> > > _bufs;
    size_t _bytesWritten;
    uint32_t _incompleteBuffers;
public:
    static constexpr size_t BUFFER_SIZE = 262144;

    StoreBufferWriter();
    ~StoreBufferWriter();

    void flush() override;
    size_t getBytesWritten() const { return _bytesWritten; }
    std::vector<char> getSingleBuffer() const;
};


StoreBufferWriter::StoreBufferWriter()
    : BufferWriter(),
      _buf(),
      _bytesWritten(0),
      _incompleteBuffers(0)
{
    _buf.resize(BUFFER_SIZE);
    setup(&_buf[0], _buf.size());
}

StoreBufferWriter::~StoreBufferWriter() = default;


void
StoreBufferWriter::flush() {
    assert(_incompleteBuffers == 0); // all previous buffers must have been full
    size_t nowLen = usedLen();
    if (nowLen != _buf.size()) {
        // buffer is not full, only allowed for last buffer
        ++_incompleteBuffers;
    }
    if (nowLen == 0) {
        return; // empty buffer
    }
    _bufs.emplace_back(std::make_unique<std::vector<char>>());
    _bufs.back()->resize(BUFFER_SIZE);
    _buf.resize(nowLen);
    _bufs.back()->swap(_buf);
    _bytesWritten += nowLen;
    setup(&_buf[0], _buf.size());
}


std::vector<char>
StoreBufferWriter::getSingleBuffer() const
{
    std::vector<char> res;
    size_t needSize = 0;
    for (const auto &buf : _bufs) {
        needSize += buf->size();
    }
    res.reserve(needSize);
    for (const auto &buf : _bufs) {
        res.insert(res.end(), buf->cbegin(), buf->cend());
    }
    return res;
}

}


TEST("Test that bufferwriter works with no writes")
{
    DrainingBufferWriter writer;
    writer.flush();
    EXPECT_EQUAL(0u, writer.getBytesWritten());
}

TEST("Test that bufferwriter works with single byte write")
{
    DrainingBufferWriter writer;
    char a = 4;
    writer.write(&a, sizeof(a));
    writer.flush();
    EXPECT_EQUAL(1u, writer.getBytesWritten());
}

TEST("Test that bufferwriter works with multiple writes")
{
    DrainingBufferWriter writer;
    char a = 4;
    int16_t b = 5;
    int32_t c = 6;
    writer.write(&a, sizeof(a));
    writer.write(&b, sizeof(b));
    writer.write(&c, sizeof(c));
    writer.flush();
    EXPECT_EQUAL(7u, writer.getBytesWritten());
}


TEST("Test that bufferwriter works with long writes")
{
    std::vector<char> a;
    const size_t mysize = 10000000;
    const size_t drainerBufferSize = DrainingBufferWriter::BUFFER_SIZE;
    EXPECT_GREATER(mysize, drainerBufferSize);
    a.resize(mysize);
    DrainingBufferWriter writer;
    writer.write(&a[0], a.size());
    writer.flush();
    EXPECT_EQUAL(a.size(), writer.getBytesWritten());
}


TEST("Test that bufferwriter passes on written data")
{
    std::vector<int> a;
    const size_t mysize = 25000000;
    const size_t drainerBufferSize = DrainingBufferWriter::BUFFER_SIZE;
    EXPECT_GREATER(mysize * sizeof(int), drainerBufferSize);
    a.reserve(mysize);
    search::Rand48 rnd;
    for (uint32_t i = 0; i < mysize; ++i) {
        a.emplace_back(rnd.lrand48());
    }
    StoreBufferWriter writer;
    writer.write(&a[0], a.size() * sizeof(int));
    writer.flush();
    EXPECT_EQUAL(a.size() * sizeof(int), writer.getBytesWritten());
    std::vector<char> written = writer.getSingleBuffer();
    EXPECT_EQUAL(a.size() * sizeof(int), written.size());
    EXPECT_TRUE(memcmp(&a[0], &written[0], written.size()) == 0);
}

}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
