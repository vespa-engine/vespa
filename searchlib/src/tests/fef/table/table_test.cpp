// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/fef/filetablefactory.h>
#include <vespa/searchlib/fef/functiontablefactory.h>
#include <vespa/searchlib/fef/tablemanager.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/testkit/test_path.h>
#include <fstream>
#include <iostream>

namespace search::fef {

class TableTest : public ::testing::Test
{
protected:
    const std::string _tables1Dir;
    const std::string _tables2Dir;

    bool assertTable(const Table & act, const Table & exp);
    bool assertCreateTable(const ITableFactory & tf, const vespalib::string & name, const Table & exp);
    void testTable();
    void testFileTableFactory();
    void testFunctionTableFactory();
    void testTableManager();

    TableTest();
    ~TableTest() override;
};

TableTest::TableTest()
    : ::testing::Test(),
      _tables1Dir(TEST_PATH("tables1")),
      _tables2Dir(TEST_PATH("tables2"))
{
}

TableTest::~TableTest() = default;

bool
TableTest::assertTable(const Table & act, const Table & exp)
{
    bool failed = false;
    EXPECT_EQ(act.size(), exp.size()) << (failed = true, "");
    if (failed) {
        return false;
    }
    for (size_t i = 0; i < act.size(); ++i) {
        EXPECT_NEAR(act[i], exp[i], 0.01) << (failed = true, "");
        if (failed) {
            return false;
        }
    }
    return true;
}

bool
TableTest::assertCreateTable(const ITableFactory & tf, const vespalib::string & name, const Table & exp)
{
    Table::SP t = tf.createTable(name);
    bool failed = false;
    EXPECT_TRUE(t.get() != nullptr) << (failed = true, "");
    if (failed) {
        return false;
    }
    return assertTable(*t, exp);
}

TEST_F(TableTest, table)
{
    Table t;
    EXPECT_EQ(t.size(), 0u);
    EXPECT_EQ(t.max(), -std::numeric_limits<double>::max());
    t.add(1).add(2);
    EXPECT_EQ(t.size(), 2u);
    EXPECT_EQ(t.max(), 2);
    EXPECT_EQ(t[0], 1);
    EXPECT_EQ(t[1], 2);
    t.add(10);
    EXPECT_EQ(t.size(), 3u);
    EXPECT_EQ(t.max(), 10);
    EXPECT_EQ(t[2], 10);
    t.add(5);
    EXPECT_EQ(t.size(), 4u);
    EXPECT_EQ(t.max(), 10);
    EXPECT_EQ(t[3], 5);
}

TEST_F(TableTest, file_table_factory)
{
    {
        FileTableFactory ftf(_tables1Dir);
        EXPECT_TRUE(assertCreateTable(ftf, "a", Table().add(1.5).add(2.25).add(3)));
        EXPECT_TRUE(ftf.createTable("b").get() == NULL);
    }
    {
        FileTableFactory ftf(_tables1Dir);
        EXPECT_TRUE(ftf.createTable("a").get() != NULL);
    }
}

TEST_F(TableTest, function_table_factory)
{
    FunctionTableFactory ftf(2);
    EXPECT_TRUE(assertCreateTable(ftf, "expdecay(400,12)",
               Table().add(400).add(368.02)));
    EXPECT_TRUE(assertCreateTable(ftf, "loggrowth(1000,5000,1)",
               Table().add(5000).add(5693.15)));
    EXPECT_TRUE(assertCreateTable(ftf, "linear(10,100)",
               Table().add(100).add(110)));
    // specify table size
    EXPECT_TRUE(assertCreateTable(ftf, "expdecay(400,12,3)",
               Table().add(400).add(368.02).add(338.60)));
    EXPECT_TRUE(assertCreateTable(ftf, "loggrowth(1000,5000,1,3)",
               Table().add(5000).add(5693.15).add(6098.61)));
    EXPECT_TRUE(assertCreateTable(ftf, "linear(10,100,3)",
               Table().add(100).add(110).add(120)));
    EXPECT_TRUE(ftf.createTable("expdecay()").get() == NULL);
    EXPECT_TRUE(ftf.createTable("expdecay(10)").get() == NULL);
    EXPECT_TRUE(ftf.createTable("loggrowth()").get() == NULL);
    EXPECT_TRUE(ftf.createTable("linear()").get() == NULL);
    EXPECT_TRUE(ftf.createTable("none").get() == NULL);
    EXPECT_TRUE(ftf.createTable("none(").get() == NULL);
    EXPECT_TRUE(ftf.createTable("none)").get() == NULL);
    EXPECT_TRUE(ftf.createTable("none)(").get() == NULL);
}

TEST_F(TableTest, table_manager)
{
    {
        TableManager tm;
        tm.addFactory(ITableFactory::SP(new FileTableFactory(_tables1Dir)));
        tm.addFactory(ITableFactory::SP(new FileTableFactory(_tables2Dir)));

        {
            const Table * t = tm.getTable("a"); // from tables1
            ASSERT_TRUE(t != NULL);
            EXPECT_TRUE(assertTable(*t, Table().add(1.5).add(2.25).add(3)));
            EXPECT_TRUE(t == tm.getTable("a"));
        }
        {
            const Table * t = tm.getTable("b"); // from tables2
            ASSERT_TRUE(t != NULL);
            EXPECT_TRUE(assertTable(*t, Table().add(40).add(50).add(60)));
            EXPECT_TRUE(t == tm.getTable("b"));
        }
        {
            EXPECT_TRUE(tm.getTable("c") == NULL);
            EXPECT_TRUE(tm.getTable("c") == NULL);
        }
    }
    {
        TableManager tm;
        ASSERT_TRUE(tm.getTable("a") == NULL);
    }
}

}

GTEST_MAIN_RUN_ALL_TESTS()
