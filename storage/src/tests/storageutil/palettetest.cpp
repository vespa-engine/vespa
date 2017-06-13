// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/storageutil/palette.h>
#include <vespa/vdstestlib/cppunit/macros.h>

namespace storage {

struct PaletteTest : public CppUnit::TestFixture {
    void testNormalUsage();

    CPPUNIT_TEST_SUITE(PaletteTest);
    CPPUNIT_TEST(testNormalUsage);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(PaletteTest);

void
PaletteTest::testNormalUsage()
{
    std::ofstream out("palette.html");
    out << "<html><body>\n";
    Palette palette(75);
    palette.printHtmlTablePalette(out);
    out << "</body></html>\n";
    out.close();
}

} // storage
