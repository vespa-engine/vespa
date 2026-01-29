// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/util/directory_traverse.h>
#include <vespa/searchlib/util/disk_space_calculator.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <filesystem>
#include <fstream>

using search::DiskSpaceCalculator;
using search::DirectoryTraverse;

inline namespace directory_traverse_test {

std::filesystem::path testdir("testdir");

constexpr uint32_t block_size = 4_Ki;
constexpr uint32_t directory_placeholder_size = block_size;

}

class DirectoryTraverseTest : public ::testing::Test {
protected:
    DirectoryTraverseTest();
    ~DirectoryTraverseTest() override;
    static void SetUpTestSuite();
    static void TearDownTestSuite();
    static uint64_t get_tree_size(const std::filesystem::path& path) {
        return DirectoryTraverse::get_tree_size(path.string());
    }
};

DirectoryTraverseTest::DirectoryTraverseTest()
    : ::testing::Test()
{

}

DirectoryTraverseTest::~DirectoryTraverseTest() = default;

void
DirectoryTraverseTest::SetUpTestSuite()
{
    std::filesystem::remove_all(testdir);
    std::filesystem::create_directory(testdir);
}

void
DirectoryTraverseTest::TearDownTestSuite()
{
    std::filesystem::remove_all(testdir);
}


TEST_F(DirectoryTraverseTest, missing_dir)
{
    EXPECT_EQ(0, get_tree_size("missing_dir"));
}

TEST_F(DirectoryTraverseTest, empty_dir)
{
    EXPECT_EQ(directory_placeholder_size, get_tree_size(testdir));
}

TEST_F(DirectoryTraverseTest, dir_with_file)
{
    auto file_path = testdir / "file";
    std::ofstream of(file_path.string());
    of << "Some text" << std::endl;
    of.close();
    EXPECT_EQ(0, get_tree_size(file_path));
    EXPECT_EQ(directory_placeholder_size + block_size, get_tree_size(testdir));
    std::filesystem::remove(file_path);
}

TEST_F(DirectoryTraverseTest, nested_dir_with_file)
{
    auto dir_path = testdir / "dir";
    auto file_path = dir_path / "file";
    std::filesystem::create_directory(dir_path);
    EXPECT_EQ(2 * directory_placeholder_size, get_tree_size(testdir));
    std::ofstream of(file_path.string());
    of << "Some text" << std::endl;
    of.close();
    EXPECT_EQ(2 * directory_placeholder_size + block_size, get_tree_size(testdir));
    std::filesystem::remove_all(dir_path);
}
