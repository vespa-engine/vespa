// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/util/slime_output_raw_buf_adapter.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/util/size_literals.h>

using namespace search;
using namespace vespalib::slime::convenience;

TEST("use slime with rawbuf") {
    RawBuf buffer(4_Ki);
    Slime src;
    Slime dst;
    {
        Cursor &c = src.setObject();
        c.setLong("foo", 5);
        c.setString("bar", "text");
    }
    EXPECT_NOT_EQUAL(src, dst);
    SlimeOutputRawBufAdapter adapter(buffer);
    vespalib::slime::BinaryFormat::encode(src, adapter);
    vespalib::slime::BinaryFormat::decode(Memory(buffer.GetDrainPos(), buffer.GetUsedLen()), dst);
    EXPECT_EQUAL(src, dst);
}

TEST_MAIN() { TEST_RUN_ALL(); }
