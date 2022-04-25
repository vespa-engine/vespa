// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/config/common/payload_converter.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP("payload_converter");

using namespace config;
using namespace vespalib;
using namespace vespalib::slime;

TEST("require that v2 payload leaf values can be converted to cfg format") {
    Slime slime;
    Cursor & root(slime.setObject());
    root.setString("foo", "bar");
    root.setLong("bar", 8);
    root.setDouble("baz", 3.1);
    root.setBool("quux", true);
    PayloadConverter converter(root);
    StringVector lines(converter.convert());
    std::sort(lines.begin(), lines.end());
    
    ASSERT_EQUAL(4u, lines.size());
    EXPECT_EQUAL("bar 8", lines[0]);
    EXPECT_EQUAL("baz 3.1", lines[1]);
    EXPECT_EQUAL("foo \"bar\"", lines[2]);
    EXPECT_EQUAL("quux true", lines[3]);
}

TEST("require that v2 payload struct values can be converted to cfg format") {
    Slime slime;
    Cursor & root(slime.setObject());
    Cursor & inner(root.setObject("obj"));
    inner.setString("foo", "bar");
    inner.setLong("bar", 8);
    PayloadConverter converter(root);
    StringVector lines(converter.convert());
    std::sort(lines.begin(), lines.end());

    ASSERT_EQUAL(2u, lines.size());
    EXPECT_EQUAL("obj.bar 8", lines[0]);
    EXPECT_EQUAL("obj.foo \"bar\"", lines[1]);
}

TEST("require that v2 payload array values can be converted to cfg format") {
    Slime slime;
    Cursor & root(slime.setObject());
    Cursor & inner(root.setArray("arr"));
    inner.addString("foo");
    inner.addLong(8);
    PayloadConverter converter(root);
    StringVector lines(converter.convert());
    ASSERT_EQUAL(2u, lines.size());
    EXPECT_EQUAL("arr[0] \"foo\"", lines[0]);
    EXPECT_EQUAL("arr[1] 8", lines[1]);
}


TEST("require that v2 payload nested structures can be converted to cfg format") {
    Slime slime;
    Cursor & root(slime.setObject());
    Cursor & inner(root.setArray("arr"));
    Cursor & obj1(inner.addObject());
    Cursor & obj2(inner.addObject());
    obj1.setString("foo", "bar");
    obj2.setLong("bar", 5);
    Cursor & inner2(root.setObject("obj"));
    Cursor & innerArr(inner2.setArray("arr"));
    Cursor & innerobj(innerArr.addObject());
    Cursor & innerArr2(innerobj.setArray("arr2"));
    innerArr2.addString("muhaha");
    PayloadConverter converter(root);
    StringVector lines(converter.convert());
    std::sort(lines.begin(), lines.end());
    ASSERT_EQUAL(3u, lines.size());
    EXPECT_EQUAL("arr[0].foo \"bar\"", lines[0]);
    EXPECT_EQUAL("arr[1].bar 5", lines[1]);
    EXPECT_EQUAL("obj.arr[0].arr2[0] \"muhaha\"", lines[2]);
}

TEST_MAIN() { TEST_RUN_ALL(); }
