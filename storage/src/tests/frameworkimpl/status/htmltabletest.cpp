// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/frameworkimpl/thread/htmltable.h>
#include <gtest/gtest.h>

namespace storage {

TEST(HtmlTableTest, testPercentageColumn)
{
    // With total hardcoded to 100
    {
        HtmlTable table("disk");
        PercentageColumn perc("fillrate", 100);
        perc.addColorLimit(70, Column::LIGHT_GREEN);
        perc.addColorLimit(85, Column::LIGHT_YELLOW);
        perc.addColorLimit(100, Column::LIGHT_RED);
        table.addColumn(perc);
        table.addRow(0);
        table.addRow(1);
        table.addRow(2);
        perc[0] = 30;
        perc[1] = 80;
        perc[2] = 100;
        std::ostringstream ost;
        table.print(ost);
        std::string expected(
                "<table border=\"1\" cellpadding=\"2\" cellspacing=\"0\">\n"
                "<tr><th>disk</th><th>fillrate</th></tr>\n"
                "<tr><td>0</td><td bgcolor=\"#a0ffa0\" align=\"right\">30.00 %</td></tr>\n"
                "<tr><td>1</td><td bgcolor=\"#ffffa0\" align=\"right\">80.00 %</td></tr>\n"
                "<tr><td>2</td><td bgcolor=\"#ffa0a0\" align=\"right\">100.00 %</td></tr>\n"
                "</table>\n");
        EXPECT_EQ(expected, ost.str());
    }
    // With automatically gathered total
    {
        HtmlTable table("disk");
        PercentageColumn perc("fillrate");
        table.addColumn(perc);
        table.addRow(0);
        table.addRow(1);
        table.addRow(2);
        perc[0] = 30;
        perc[1] = 80;
        perc[2] = 100;
        std::ostringstream ost;
        table.print(ost);
        std::string expected(
                "<table border=\"1\" cellpadding=\"2\" cellspacing=\"0\">\n"
                "<tr><th>disk</th><th>fillrate</th></tr>\n"
                "<tr><td>0</td><td align=\"right\">14.29 %</td></tr>\n"
                "<tr><td>1</td><td align=\"right\">38.10 %</td></tr>\n"
                "<tr><td>2</td><td align=\"right\">47.62 %</td></tr>\n"
                "</table>\n");
        EXPECT_EQ(expected, ost.str());
    }
}

TEST(HtmlTableTest, testByteSizeColumn)
{
    {
        HtmlTable table("disk");
        ByteSizeColumn size("size");
        table.addColumn(size);
        table.addRow(0);
        table.addRow(1);
        table.addRow(2);
        // Biggest value enforce the denomination
        size[0] = 42123;
        size[1] = 124123151;
        size[2] = 6131231;
        std::ostringstream ost;
        table.print(ost);
        std::string expected(
                "<table border=\"1\" cellpadding=\"2\" cellspacing=\"0\">\n"
                "<tr><th>disk</th><th>size</th></tr>\n"
                "<tr><td>0</td><td align=\"right\">0 MB</td></tr>\n"
                "<tr><td>1</td><td align=\"right\">118 MB</td></tr>\n"
                "<tr><td>2</td><td align=\"right\">5 MB</td></tr>\n"
                "</table>\n");
        EXPECT_EQ(expected, ost.str());
    }

}

} // storage
