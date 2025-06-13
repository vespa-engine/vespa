// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/testkit/test_master.hpp>
#include <filesystem>
#include <iostream>
#include <vector>
#include <regex>
#include <system_error>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/size_literals.h>

namespace vespalib {

namespace {

bool fileExists(const std::string& name) {
    return std::filesystem::exists(std::filesystem::path(name));
}

}

std::string normalizeOpenError(const std::string str)
{
    std::regex modeex(" mode=[0-7]+");
    std::regex uidex(" uid=[0-9]+");
    std::regex gidex(" gid=[0-9]+");
    std::regex sizeex(" size=[0-9]+");
    std::regex mtimeex(" mtime=[0-9]+");
    std::regex errnoex(" errno=[0-9]+\\(\"[^\"]+\"\\)");
    std::regex errorex("^error=[0-9]+\\(\"[^\"]+\"\\)");
    std::string tmp1 = std::regex_replace(std::string(str), modeex, " mode=x");
    std::string tmp2 = std::regex_replace(tmp1, uidex, " uid=x");
    tmp1 = std::regex_replace(tmp2, gidex, " gid=x");
    tmp2 = std::regex_replace(tmp1, sizeex, " size=x");
    tmp1 = std::regex_replace(tmp2, mtimeex, " mtime=x");
    tmp2 = std::regex_replace(tmp1, errnoex, " errno=x");
    tmp1 = std::regex_replace(tmp2, errorex, "error=x");
    return tmp1;
}

TEST(FileUtilTest, require_that_vespalib__File__open_works)
{
        // Opening non-existing file for reading should fail.
    try{
        std::filesystem::remove(std::filesystem::path("myfile")); // Just in case
        File f("myfile");
        f.open(File::READONLY);
        FAIL() << "Opening non-existing file for reading should fail.";
    } catch (IoException& e) {
        //std::cerr << e.what() << "\n";
        EXPECT_EQ(IoException::NOT_FOUND, e.getType());
    }
        // Opening non-existing file for writing without CREATE flag fails
    try{
        File f("myfile");
        f.open(0);
        FAIL() << "Opening non-existing file without CREATE flag should fail.";
    } catch (IoException& e) {
        //std::cerr << e.what() << "\n";
        EXPECT_EQ(IoException::NOT_FOUND, e.getType());
    }
        // Opening file in non-existing subdir should fail.
    try{
        std::filesystem::remove_all(std::filesystem::path("mydir")); // Just in case
        File f("mydir/myfile");
        f.open(File::CREATE);
        FAIL() << "Opening non-existing file for reading should fail.";
    } catch (IoException& e) {
        //std::cerr << e.what() << "\n";
        EXPECT_EQ(IoException::NOT_FOUND, e.getType());
    }
        // Opening file for reading in non-existing subdir should not create
        // subdir.
    try{
        File f("mydir/myfile");
        f.open(File::READONLY, true);
        FAIL() << "Read only parameter doesn't work with auto-generate";
    } catch (IllegalArgumentException& e) {
    }
        // Opening file in non-existing subdir without auto-generating
        // directories should not work.
    try{
        File f("mydir/myfile");
        f.open(File::CREATE, false);
        FAIL() << "Need to autogenerate directories for this to work";
    } catch (IoException& e) {
        //std::cerr << e.what() << "\n";
        EXPECT_EQ(IoException::NOT_FOUND, e.getType());
        ASSERT_TRUE(!fileExists("mydir"));
    }
        // Opening file in non-existing subdir works with auto-generate
    {
        File f("mydir/myfile");
        f.open(File::CREATE, true);
        ASSERT_TRUE(fileExists("mydir/myfile"));
        f.unlink();
    }
        // Opening file in existing subdir works with auto-generate
    {
        File f("mydir/myfile");
        f.open(File::CREATE, false);
        ASSERT_TRUE(fileExists("mydir/myfile"));
        f.unlink();
    }
        // Opening plain file works
    {
        File f("myfile");
        f.open(File::CREATE, false);
        ASSERT_TRUE(fileExists("myfile"));
    }
        // Opening directory does not work.
    try{
        File f("mydir");
        f.open(File::CREATE, false);
        FAIL() << "Can't open directory for reading";
    } catch (IoException& e) {
        //std::cerr << e.what() << "\n";
        EXPECT_EQ(IoException::ILLEGAL_PATH, e.getType());
    }
        // Test reopening file in same object
    {
        File f("myfile");
        f.open(File::CREATE, false);
        f.write("a", 1, 0);
        f.close();
        f.open(File::CREATE, false);
        std::vector<char> vec(10);
        size_t read = f.read(&vec[0], 10, 0);
        EXPECT_EQ(1u, read);
        EXPECT_EQ('a', vec[0]);
        f.write("b", 1, 0);
    }
}

TEST(FileUtilTest, require_that_vespalib__File__isOpen_works)
{
    File f("myfile");
    ASSERT_TRUE(!f.isOpen());
    f.open(File::CREATE, false);
    ASSERT_TRUE(f.isOpen());
    f.close();
    ASSERT_TRUE(!f.isOpen());
}

TEST(FileUtilTest, require_that_vespalib__File__resize_works)
{
    std::filesystem::remove(std::filesystem::path("myfile"));
    File f("myfile");
    f.open(File::CREATE, false);
    f.write("foobar", 6, 0);
    EXPECT_EQ(6, f.getFileSize());
    f.resize(10);
    EXPECT_EQ(10, f.getFileSize());
    std::vector<char> vec(20, ' ');
    size_t read = f.read(&vec[0], 20, 0);
    EXPECT_EQ(10u, read);
    EXPECT_EQ(std::string("foobar"), std::string(&vec[0], 6));
    f.resize(3);
    EXPECT_EQ(3, f.getFileSize());
    read = f.read(&vec[0], 20, 0);
    EXPECT_EQ(3u, read);
    EXPECT_EQ(std::string("foo"), std::string(&vec[0], 3));
}

TEST(FileUtilTest, require_that_we_can_read_all_data_written_to_file)
{
    // Write text into a file.
    std::filesystem::remove(std::filesystem::path("myfile"));
    File fileForWriting("myfile");
    fileForWriting.open(File::CREATE);
    std::string text = "This is some text. ";
    fileForWriting.write(text.data(), text.size(), 0);
    fileForWriting.close();

    // Read contents of file, and verify it's identical.
    File file("myfile");
    file.open(File::READONLY);
    std::string content = file.readAll();
    file.close();
    ASSERT_EQ(content, text);

    // Write lots of text into file.
    off_t offset = 0;
    fileForWriting.open(File::TRUNC);
    while (offset < 10000) {
        offset += fileForWriting.write(text.data(), text.size(), offset);
    }
    fileForWriting.close();
    
    // Read it all and verify.
    file.open(File::READONLY);
    content = file.readAll();
    file.close();
    ASSERT_EQ(offset, static_cast<off_t>(content.size()));

    std::string chunk;
    for (offset = 0; offset < 10000; offset += text.size()) {
        chunk.assign(content.data() + offset, text.size());
        ASSERT_EQ(text, chunk);
    }
}

TEST(FileUtilTest, require_that_vespalib__dirname_works)
{
    ASSERT_EQ("mydir", dirname("mydir/foo"));
    ASSERT_EQ(".", dirname("notFound"));
    ASSERT_EQ("/", dirname("/notFound"));
    ASSERT_EQ("here/there", dirname("here/there/everywhere"));
}

TEST(FileUtilTest, require_that_vespalib__getOpenErrorString_works)
{
    std::string_view dirName = "mydir";
    std::filesystem::remove_all(std::filesystem::path(dirName));
    std::filesystem::create_directory(std::filesystem::path(dirName));
    {
        File foo("mydir/foo");
        foo.open(File::CREATE);
        foo.close();
    }
    std::string err1 = getOpenErrorString(1, "mydir/foo");
    std::string normErr1 =  normalizeOpenError(err1);
    std::string expErr1 = "error=x fileStat[name=mydir/foo mode=x uid=x gid=x size=x mtime=x] dirStat[name=mydir mode=x uid=x gid=x size=x mtime=x]";
    std::cerr << "getOpenErrorString(1, \"mydir/foo\") is " << err1 <<
        ", normalized to " << normErr1 << std::endl;
    EXPECT_EQ(expErr1, normErr1);
    std::string err2 = getOpenErrorString(1, "notFound");
    std::string normErr2 =  normalizeOpenError(err2);
    std::string expErr2 = "error=x fileStat[name=notFound errno=x] dirStat[name=. mode=x uid=x gid=x size=x mtime=x]";
    std::cerr << "getOpenErrorString(1, \"notFound\") is " << err2 <<
        ", normalized to " << normErr2 << std::endl;
    EXPECT_EQ(expErr2, normErr2);
    std::filesystem::remove_all(std::filesystem::path(dirName));
}

} // vespalib

GTEST_MAIN_RUN_ALL_TESTS()
