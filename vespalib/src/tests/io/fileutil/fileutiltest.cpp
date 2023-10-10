// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <filesystem>
#include <iostream>
#include <vector>
#include <regex>
#include <system_error>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/size_literals.h>

namespace vespalib {

namespace {

bool fileExists(const vespalib::string& name) {
    return std::filesystem::exists(std::filesystem::path(name));
}

}

vespalib::string normalizeOpenError(const vespalib::string str)
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

TEST("require that vespalib::File::open works")
{
        // Opening non-existing file for reading should fail.
    try{
        std::filesystem::remove(std::filesystem::path("myfile")); // Just in case
        File f("myfile");
        f.open(File::READONLY);
        TEST_FATAL("Opening non-existing file for reading should fail.");
    } catch (IoException& e) {
        //std::cerr << e.what() << "\n";
        EXPECT_EQUAL(IoException::NOT_FOUND, e.getType());
    }
        // Opening non-existing file for writing without CREATE flag fails
    try{
        File f("myfile");
        f.open(0);
        TEST_FATAL("Opening non-existing file without CREATE flag should fail.");
    } catch (IoException& e) {
        //std::cerr << e.what() << "\n";
        EXPECT_EQUAL(IoException::NOT_FOUND, e.getType());
    }
        // Opening file in non-existing subdir should fail.
    try{
        std::filesystem::remove_all(std::filesystem::path("mydir")); // Just in case
        File f("mydir/myfile");
        f.open(File::CREATE);
        TEST_FATAL("Opening non-existing file for reading should fail.");
    } catch (IoException& e) {
        //std::cerr << e.what() << "\n";
        EXPECT_EQUAL(IoException::NOT_FOUND, e.getType());
    }
        // Opening file for reading in non-existing subdir should not create
        // subdir.
    try{
        File f("mydir/myfile");
        f.open(File::READONLY, true);
        TEST_FATAL("Read only parameter doesn't work with auto-generate");
    } catch (IllegalArgumentException& e) {
    }
        // Opening file in non-existing subdir without auto-generating
        // directories should not work.
    try{
        File f("mydir/myfile");
        f.open(File::CREATE, false);
        TEST_FATAL("Need to autogenerate directories for this to work");
    } catch (IoException& e) {
        //std::cerr << e.what() << "\n";
        EXPECT_EQUAL(IoException::NOT_FOUND, e.getType());
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
        TEST_FATAL("Can't open directory for reading");
    } catch (IoException& e) {
        //std::cerr << e.what() << "\n";
        EXPECT_EQUAL(IoException::ILLEGAL_PATH, e.getType());
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
        EXPECT_EQUAL(1u, read);
        EXPECT_EQUAL('a', vec[0]);
        f.write("b", 1, 0);
    }
}

TEST("require that vespalib::File::isOpen works")
{
    File f("myfile");
    ASSERT_TRUE(!f.isOpen());
    f.open(File::CREATE, false);
    ASSERT_TRUE(f.isOpen());
    f.close();
    ASSERT_TRUE(!f.isOpen());
}

TEST("require that vespalib::File::resize works")
{
    std::filesystem::remove(std::filesystem::path("myfile"));
    File f("myfile");
    f.open(File::CREATE, false);
    f.write("foobar", 6, 0);
    EXPECT_EQUAL(6, f.getFileSize());
    f.resize(10);
    EXPECT_EQUAL(10, f.getFileSize());
    std::vector<char> vec(20, ' ');
    size_t read = f.read(&vec[0], 20, 0);
    EXPECT_EQUAL(10u, read);
    EXPECT_EQUAL(std::string("foobar"), std::string(&vec[0], 6));
    f.resize(3);
    EXPECT_EQUAL(3, f.getFileSize());
    read = f.read(&vec[0], 20, 0);
    EXPECT_EQUAL(3u, read);
    EXPECT_EQUAL(std::string("foo"), std::string(&vec[0], 3));
}

TEST("require that we can read all data written to file")
{
    // Write text into a file.
    std::filesystem::remove(std::filesystem::path("myfile"));
    File fileForWriting("myfile");
    fileForWriting.open(File::CREATE);
    vespalib::string text = "This is some text. ";
    fileForWriting.write(text.data(), text.size(), 0);
    fileForWriting.close();

    // Read contents of file, and verify it's identical.
    File file("myfile");
    file.open(File::READONLY);
    vespalib::string content = file.readAll();
    file.close();
    ASSERT_EQUAL(content, text);

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
    ASSERT_EQUAL(offset, static_cast<off_t>(content.size()));

    vespalib::string chunk;
    for (offset = 0; offset < 10000; offset += text.size()) {
        chunk.assign(content.begin() + offset, text.size());
        ASSERT_EQUAL(text, chunk);
    }
}

TEST("require that vespalib::dirname works")
{
    ASSERT_EQUAL("mydir", dirname("mydir/foo"));
    ASSERT_EQUAL(".", dirname("notFound"));
    ASSERT_EQUAL("/", dirname("/notFound"));
    ASSERT_EQUAL("here/there", dirname("here/there/everywhere"));
}

TEST("require that vespalib::getOpenErrorString works")
{
    stringref dirName = "mydir";
    std::filesystem::remove_all(std::filesystem::path(dirName));
    std::filesystem::create_directory(std::filesystem::path(dirName));
    {
        File foo("mydir/foo");
        foo.open(File::CREATE);
        foo.close();
    }
    vespalib::string err1 = getOpenErrorString(1, "mydir/foo");
    vespalib::string normErr1 =  normalizeOpenError(err1);
    vespalib::string expErr1 = "error=x fileStat[name=mydir/foo mode=x uid=x gid=x size=x mtime=x] dirStat[name=mydir mode=x uid=x gid=x size=x mtime=x]";
    std::cerr << "getOpenErrorString(1, \"mydir/foo\") is " << err1 <<
        ", normalized to " << normErr1 << std::endl;
    EXPECT_EQUAL(expErr1, normErr1);
    vespalib::string err2 = getOpenErrorString(1, "notFound");
    vespalib::string normErr2 =  normalizeOpenError(err2);
    vespalib::string expErr2 = "error=x fileStat[name=notFound errno=x] dirStat[name=. mode=x uid=x gid=x size=x mtime=x]";
    std::cerr << "getOpenErrorString(1, \"notFound\") is " << err2 <<
        ", normalized to " << normErr2 << std::endl;
    EXPECT_EQUAL(expErr2, normErr2);
    std::filesystem::remove_all(std::filesystem::path(dirName));
}

} // vespalib

TEST_MAIN() { TEST_RUN_ALL(); }
