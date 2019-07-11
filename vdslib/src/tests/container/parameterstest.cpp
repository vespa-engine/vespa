// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/vdslib/container/parameters.h>
#include <vespa/vespalib/gtest/gtest.h>

using document::DocumentTypeRepo;
using namespace vdslib;

TEST(ParametersTest, test_parameters)
{
    Parameters par;
    par.set("fast", "overture");
    par.set("overture", "yahoo");
    par.set("number", 6);
    par.set("long", 8589934590L);
    par.set("double", 0.25);
    std::unique_ptr<document::ByteBuffer> buffer(par.serialize());

    buffer->flip();
    DocumentTypeRepo repo;
    Parameters par2(repo, *buffer);

    EXPECT_EQ(vespalib::stringref("overture"), par2.get("fast"));
    EXPECT_EQ(vespalib::stringref("yahoo"), par2.get("overture"));
    std::string stringDefault = "wayne corp";
    int numberDefault = 123;
    long longDefault = 456;
    double doubleDefault = 0.5;
    EXPECT_EQ(6, par2.get("number", numberDefault));
    EXPECT_EQ(8589934590L, par2.get("long", longDefault));
    EXPECT_DOUBLE_EQ(0.25, par2.get("double", doubleDefault));

    EXPECT_EQ(stringDefault, par2.get("nonexistingstring", stringDefault));
    EXPECT_EQ(numberDefault, par2.get("nonexistingnumber", numberDefault));
    EXPECT_EQ(longDefault,   par2.get("nonexistinglong", longDefault));
    EXPECT_EQ(doubleDefault, par2.get("nonexistingdouble", doubleDefault));
}
