// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/storageutil/piechart.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <fstream>

namespace storage {

struct PieChartTest : public CppUnit::TestFixture
{
    void testWriteHtmlFile();

    CPPUNIT_TEST_SUITE(PieChartTest);
    CPPUNIT_TEST(testWriteHtmlFile);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(PieChartTest);

namespace {
    void printHtmlFile(const std::string& filename, const PieChart& chart) {
        std::ofstream out(filename.c_str());
        out << "<html>\n"
            << "  <head>\n"
            << "    ";
        PieChart::printHtmlHeadAdditions(out, "    ");
        out << "\n  <title>Pie example</title>\n"
            << "  </head>\n"
            << "  <body>\n"
            << "    ";
        chart.printCanvas(out, 500, 400);
        out << "\n    ";
        chart.printScript(out, "    ");
        out << "\n  </body>\n"
            << "</html>\n";
        out.close();
    }
}

void
PieChartTest::testWriteHtmlFile()
{
    {
        PieChart chart("mypie");
        chart.add(10, "put");
        chart.add(20, "get");
        chart.add(50, "free");

        printHtmlFile("piefile.html", chart);
    }
    {
        PieChart chart("mypie", PieChart::SCHEME_CUSTOM);
        chart.add(10, "put", PieChart::RED);
        chart.add(20, "get", PieChart::GREEN);
        chart.add(50, "free", PieChart::BLUE);

        printHtmlFile("piefile-customcols.html", chart);
    }
}

} // storage
