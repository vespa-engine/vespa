// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/vsm/common/charbuffer.h>

namespace vsm {

class CharBufferTest : public vespalib::TestApp
{
private:
    void test();
public:
    int Main() override;
};

void
CharBufferTest::test()
{
    { // empty
        CharBuffer buf;
        EXPECT_EQUAL(buf.getLength(), 0u);
        EXPECT_EQUAL(buf.getPos(), 0u);
        EXPECT_EQUAL(buf.getRemaining(), 0u);
    }
    { // explicit length
        CharBuffer buf(8);
        EXPECT_EQUAL(buf.getLength(), 8u);
        EXPECT_EQUAL(buf.getPos(), 0u);
        EXPECT_EQUAL(buf.getRemaining(), 8u);
    }
    { // resize
        CharBuffer buf(8);
        EXPECT_EQUAL(buf.getLength(), 8u);
        buf.resize(16);
        EXPECT_EQUAL(buf.getLength(), 16u);
        buf.resize(8);
        EXPECT_EQUAL(buf.getLength(), 16u);
    }
    { // put with triggered resize
        CharBuffer buf(8);
        buf.put("123456", 6);
        EXPECT_EQUAL(buf.getLength(), 8u);
        EXPECT_EQUAL(buf.getPos(), 6u);
        EXPECT_EQUAL(buf.getRemaining(), 2u);
        EXPECT_EQUAL(std::string(buf.getBuffer(), buf.getPos()), "123456");
        buf.put("789", 3);
        EXPECT_EQUAL(buf.getLength(), 12u);
        EXPECT_EQUAL(buf.getPos(), 9u);
        EXPECT_EQUAL(buf.getRemaining(), 3u);
        EXPECT_EQUAL(std::string(buf.getBuffer(), buf.getPos()), "123456789");
        buf.put('a');
        EXPECT_EQUAL(buf.getLength(), 12u);
        EXPECT_EQUAL(buf.getPos(), 10u);
        EXPECT_EQUAL(buf.getRemaining(), 2u);
        EXPECT_EQUAL(std::string(buf.getBuffer(), buf.getPos()), "123456789a");
        buf.reset();
        EXPECT_EQUAL(buf.getLength(), 12u);
        EXPECT_EQUAL(buf.getPos(), 0u);
        EXPECT_EQUAL(buf.getRemaining(), 12u);
        buf.put("bcd", 3);
        EXPECT_EQUAL(buf.getLength(), 12u);
        EXPECT_EQUAL(buf.getPos(), 3u);
        EXPECT_EQUAL(buf.getRemaining(), 9u);
        EXPECT_EQUAL(std::string(buf.getBuffer(), buf.getPos()), "bcd");
    }
}

int
CharBufferTest::Main()
{
    TEST_INIT("charbuffer_test");

    test();

    TEST_DONE();
}

}

TEST_APPHOOK(vsm::CharBufferTest);
