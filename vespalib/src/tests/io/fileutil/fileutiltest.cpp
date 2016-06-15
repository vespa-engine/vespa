// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("fileutil_test");
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <iostream>
#include <vector>

namespace vespalib {

class Test : public vespalib::TestApp
{
public:
    void testOpen();
    void testIsOpen();
    void testStat();
    void testResize();
    void testDirFunctions();
    void testUnlink();
    void testRename();
    void testCopy();
    void testCopyConstructorAndAssignmentOperator();
    void testLazyFile();
    void testSymlink();
    void testReadAll();
    int Main();
};

int
Test::Main()
{
    TEST_INIT("fileutil_test");
    srandom(1);
    std::cerr << "testOpen\n";
    testOpen();
    std::cerr << "testIsOpen\n";
    testIsOpen();
    std::cerr << "testStat\n";
    testStat();
    std::cerr << "testResize\n";
    testResize();
    std::cerr << "testDirFunctions\n";
    testDirFunctions();
    std::cerr << "testUnlink\n";
    testUnlink();
    std::cerr << "testRename\n";
    testRename();
    std::cerr << "testCopy\n";
    testCopy();
    std::cerr << "testCopyConstructorAndAssignmentOperator\n";
    testCopyConstructorAndAssignmentOperator();
    std::cerr << "testLazyFile\n";
    testLazyFile();
    std::cerr << "testSymlink\n";
    testSymlink();
    std::cerr << "testReadAll\n";
    testReadAll();
    TEST_DONE();
}

void
Test::testOpen()
{
        // Opening non-existing file for reading should fail.
    try{
        unlink("myfile"); // Just in case
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
        rmdir("mydir", true); // Just in case
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
        // Opening with direct IO support works.
    {
        File f("mydir/myfile");
        f.open(File::CREATE | File::DIRECTIO, false);
        ASSERT_TRUE(fileExists("mydir/myfile"));
        if (!f.isOpenWithDirectIO()) {
            std::cerr << "This platform does not support direct IO\n";
        }
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
        // Test opening already open file
    {
        std::unique_ptr<File> f(new File("myfile"));
        f->open(File::CREATE, false);
        f->closeFileWhenDestructed(false);
        File f2(f->getFileDescriptor(), "myfile");
        f.reset();
        ASSERT_TRUE(f2.isOpen());
        f2.write(" ", 1, 0);
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

void
Test::testIsOpen()
{
    File f("myfile");
    ASSERT_TRUE(!f.isOpen());
    f.open(File::CREATE, false);
    ASSERT_TRUE(f.isOpen());
    f.close();
    ASSERT_TRUE(!f.isOpen());
}

void
Test::testStat()
{
    unlink("myfile");
    rmdir("mydir", true);
    EXPECT_EQUAL(false, fileExists("myfile"));
    EXPECT_EQUAL(false, fileExists("mydir"));
    mkdir("mydir");
    FileInfo::UP info = stat("myfile");
    ASSERT_TRUE(info.get() == 0);
    File f("myfile");
    f.open(File::CREATE, false);
    f.write("foobar", 6, 0);

    info = stat("myfile");
    ASSERT_TRUE(info.get() != 0);
    FileInfo info2 = f.stat();
    EXPECT_EQUAL(*info, info2);
    EXPECT_EQUAL(6, info->_size);
    EXPECT_EQUAL(true, info->_plainfile);
    EXPECT_EQUAL(false, info->_directory);

    EXPECT_EQUAL(6, f.getFileSize());
    f.close();
    EXPECT_EQUAL(6, getFileSize("myfile"));

    EXPECT_EQUAL(true, isDirectory("mydir"));
    EXPECT_EQUAL(false, isDirectory("myfile"));
    EXPECT_EQUAL(false, isPlainFile("mydir"));
    EXPECT_EQUAL(true, isPlainFile("myfile"));
    EXPECT_EQUAL(true, fileExists("myfile"));
    EXPECT_EQUAL(true, fileExists("mydir"));
}

void
Test::testResize()
{
    unlink("myfile");
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

void
Test::testDirFunctions()
{
    rmdir("mydir", true);
    ASSERT_TRUE(!fileExists("mydir"));
        // Cannot create recursive without recursive option
    try{
        mkdir("mydir/otherdir", false);
        TEST_FATAL("Should not work without recursive option set");
    } catch (IoException& e) {
        //std::cerr << e.what() << "\n";
        EXPECT_EQUAL(IoException::NOT_FOUND, e.getType());
    }
        // Works with recursive option
    {
        ASSERT_TRUE(mkdir("mydir/otherdir"));
        ASSERT_TRUE(!mkdir("mydir/otherdir"));
            // Test chdir / getCurrentDirectory
        chdir("mydir");
        std::string currDir = getCurrentDirectory();
        std::string::size_type pos = currDir.rfind('/');
        ASSERT_TRUE(pos != std::string::npos);
        EXPECT_EQUAL("mydir", currDir.substr(pos + 1));
        EXPECT_EQUAL('/', currDir[0]);
        chdir("..");
        currDir = getCurrentDirectory();
        pos = currDir.rfind('/');
        EXPECT_EQUAL("fileutil", currDir.substr(pos + 1));
    }
        // rmdir fails with content
    try{
        ASSERT_TRUE(mkdir("mydir/otherdir/evenmorestuff"));
        rmdir("mydir");
        TEST_FATAL("Should not work without recursive option set");
    } catch (IoException& e) {
        //std::cerr << e.what() << "\n";
        EXPECT_EQUAL(IoException::DIRECTORY_HAVE_CONTENT, e.getType());
    }
        // Works with recursive option
    {
        ASSERT_TRUE(rmdir("mydir", true));
        ASSERT_TRUE(!fileExists("mydir"));
        ASSERT_TRUE(!rmdir("mydir", true));
    }
        // Doesn't work on file
    try{
        {
            File f("myfile");
            f.open(File::CREATE);
            f.write("foo", 3, 0);
        }
        rmdir("myfile");
        TEST_FATAL("Should have failed to run rmdir on file.");
    } catch (IoException& e) {
        //std::cerr << e.what() << "\n";
        EXPECT_EQUAL(IoException::ILLEGAL_PATH, e.getType());
    }

    // mkdir works when a path component is a symlink which points to
    // another directory and the final path component does not exist.
    {
        rmdir("mydir", true);
        rmdir("otherdir", true);
        unlink("linkeddir"); // symlink if exists
        mkdir("otherdir/stuff");
        symlink("otherdir", "linkeddir");
        // Should now be able to resolve through symlink and create dir
        // at the appropriate (linked) location.
        ASSERT_TRUE(mkdir("linkeddir/stuff/fluff"));
    }

    // mkdir works when the final path component is a symlink which points
    // to another directory (causing OS mkdir to fail since something already
    // exists at the location).
    {
        rmdir("mydir", true);
        rmdir("otherdir", true);
        unlink("linkeddir"); // symlink if exists
        mkdir("otherdir/stuff");
        symlink("otherdir", "linkeddir");
        // Should now be able to resolve through symlink and create dir
        // at the appropriate (linked) location.
        ASSERT_FALSE(mkdir("linkeddir"));
    }
}

void
Test::testUnlink()
{
        // Fails on directory
    try{
        mkdir("mydir");
        unlink("mydir");
        TEST_FATAL("Should work on directories.");
    } catch (IoException& e) {
        //std::cerr << e.what() << "\n";
        EXPECT_EQUAL(IoException::ILLEGAL_PATH, e.getType());
    }
        // Works for file
    {
        {
            File f("myfile");
            f.open(File::CREATE);
            f.write("foo", 3, 0);
        }
        ASSERT_TRUE(fileExists("myfile"));
        ASSERT_TRUE(unlink("myfile"));
        ASSERT_TRUE(!fileExists("myfile"));
        ASSERT_TRUE(!unlink("myfile"));
    }
}

void
Test::testRename()
{
    rmdir("mydir", true);
    File f("myfile");
    f.open(File::CREATE | File::TRUNC);
    f.write("Hello World!\n", 13, 0);
    f.close();
        // Renaming to non-existing dir doesn't work
    try{
        rename("myfile", "mydir/otherfile");
        TEST_FATAL("This shouldn't work when mydir doesn't exist");
    } catch (IoException& e) {
        //std::cerr << e.what() << "\n";
        EXPECT_EQUAL(IoException::NOT_FOUND, e.getType());
    }
        // Renaming to non-existing dir works if autocreating dirs
    {
        ASSERT_TRUE(rename("myfile", "mydir/otherfile", true, true));
        ASSERT_TRUE(!fileExists("myfile"));
        ASSERT_TRUE(fileExists("mydir/otherfile"));

        File f2("mydir/otherfile");
        f2.open(File::READONLY);
        std::vector<char> vec(20, ' ');
        size_t read = f2.read(&vec[0], 20, 0);
        EXPECT_EQUAL(13u, read);
        EXPECT_EQUAL(std::string("Hello World!\n"), std::string(&vec[0], 13));
    }
        // Renaming non-existing returns false
    ASSERT_TRUE(!rename("myfile", "mydir/otherfile", true));
        // Rename to overwrite works
    {
        f.open(File::CREATE | File::TRUNC);
        f.write("Bah\n", 4, 0);
        f.close();
        ASSERT_TRUE(rename("myfile", "mydir/otherfile", true, true));

        File f2("mydir/otherfile");
        f2.open(File::READONLY);
        std::vector<char> vec(20, ' ');
        size_t read = f2.read(&vec[0], 20, 0);
        EXPECT_EQUAL(4u, read);
        EXPECT_EQUAL(std::string("Bah\n"), std::string(&vec[0], 4));
    }
        // Overwriting directory fails (does not put inside dir)
    try{
        mkdir("mydir");
        f.open(File::CREATE | File::TRUNC);
        f.write("Bah\n", 4, 0);
        f.close();
        ASSERT_TRUE(rename("myfile", "mydir"));
    } catch (IoException& e) {
        //std::cerr << e.what() << "\n";
        EXPECT_EQUAL(IoException::ILLEGAL_PATH, e.getType());
    }
        // Moving directory works
    {
        ASSERT_TRUE(isDirectory("mydir"));
        rmdir("myotherdir", true);
        ASSERT_TRUE(rename("mydir", "myotherdir"));
        ASSERT_TRUE(isDirectory("myotherdir"));
        ASSERT_TRUE(!isDirectory("mydir"));
        ASSERT_TRUE(!rename("mydir", "myotherdir"));
    }
        // Overwriting directory fails
    try{
        File f2("mydir/yetanotherfile");
        f2.open(File::CREATE, true);
        f2.write("foo", 3, 0);
        f2.open(File::READONLY);
        f2.close();
        rename("mydir", "myotherdir");
        TEST_FATAL("Should fail trying to overwrite directory");
    } catch (IoException& e) {
        //std::cerr << e.what() << "\n";
        EXPECT_TRUE((IoException::DIRECTORY_HAVE_CONTENT == e.getType()) ||
                    (IoException::ALREADY_EXISTS == e.getType()));
    }
}

void
Test::testCopy()
{
    rmdir("mydir", true);
    File f("myfile");
    f.open(File::CREATE | File::TRUNC);

    MallocAutoPtr buffer = getAlignedBuffer(5000);
    memset(buffer.get(), 0, 5000);
    strncpy(static_cast<char*>(buffer.get()), "Hello World!\n", 13);
    f.write(buffer.get(), 4096, 0);
    f.close();
    std::cerr << "Simple copy\n";
        // Simple copy works (512b dividable file)
    copy("myfile", "targetfile");
    ASSERT_TRUE(system("diff myfile targetfile") == 0);
    std::cerr << "Overwriting\n";
        // Overwriting works (may not be able to use direct IO writing on all
        // systems, so will always use cached IO)
    {
        f.open(File::CREATE | File::TRUNC);
        f.write("Bah\n", 4, 0);
        f.close();

        ASSERT_TRUE(system("diff myfile targetfile > /dev/null") != 0);
        copy("myfile", "targetfile");
        ASSERT_TRUE(system("diff myfile targetfile > /dev/null") == 0);
    }
        // Fails if target is directory
    try{
        mkdir("mydir");
        copy("myfile", "mydir");
        TEST_FATAL("Should fail trying to overwrite directory");
    } catch (IoException& e) {
        //std::cerr << e.what() << "\n";
        EXPECT_EQUAL(IoException::ILLEGAL_PATH, e.getType());
    }
        // Fails if source is directory
    try{
        mkdir("mydir");
        copy("mydir", "myfile");
        TEST_FATAL("Should fail trying to copy directory");
    } catch (IoException& e) {
        //std::cerr << e.what() << "\n";
        EXPECT_EQUAL(IoException::ILLEGAL_PATH, e.getType());
    }
}

void
Test::testCopyConstructorAndAssignmentOperator()
{
        // Copy file not opened.
    {
        File f("myfile");
        File f2(f);
        EXPECT_EQUAL(f.getFilename(), f2.getFilename());
    }
        // Copy file opened
    {
        File f("myfile");
        f.open(File::CREATE);
        File f2(f);
        EXPECT_EQUAL(f.getFilename(), f2.getFilename());
        ASSERT_TRUE(f2.isOpen());
        ASSERT_TRUE(!f.isOpen());
    }
        // Assign file opened to another file opened
    {
        File f("myfile");
        f.open(File::CREATE);
        int fd = f.getFileDescriptor();
        File f2("targetfile");
        f2.open(File::CREATE);
        f = f2;
        EXPECT_EQUAL(std::string("targetfile"), f2.getFilename());
        EXPECT_EQUAL(f.getFilename(), f2.getFilename());
        ASSERT_TRUE(!f2.isOpen());
        ASSERT_TRUE(f.isOpen());
        try{
            File f3(fd, "myfile");
            f3.closeFileWhenDestructed(false); // Already closed
            f3.write("foo", 3, 0);
            TEST_FATAL("This file descriptor should have been closed");
        } catch (IoException& e) {
            //std::cerr << e.what() << "\n";
            EXPECT_EQUAL(IoException::INTERNAL_FAILURE, e.getType());
        }
    }
}

void
Test::testLazyFile()
{
        // Copy constructor
    {
        LazyFile file("myfile", File::CREATE, true);
        LazyFile file2(file);
        EXPECT_EQUAL(file.getFlags(), file2.getFlags());
        EXPECT_EQUAL(file.autoCreateDirectories(), file2.autoCreateDirectories());
    }
        // Assignment
    {
        LazyFile file("myfile", File::CREATE, true);
        LazyFile file2("targetfile", File::READONLY);
        file = file2;
        EXPECT_EQUAL(file.getFlags(), file2.getFlags());
        EXPECT_EQUAL(file.autoCreateDirectories(), file2.autoCreateDirectories());
    }
        // Lazily write
    {
        LazyFile file("myfile", File::CREATE, true);
        file.write("foo", 3, 0);
    }
        // Lazy stat
    {
        LazyFile file("myfile", File::CREATE, true);
        EXPECT_EQUAL(3, file.getFileSize());
        file.close();

        LazyFile file2("myfile", File::CREATE, true);
        FileInfo info = file2.stat();
        EXPECT_EQUAL(3, info._size);
        EXPECT_EQUAL(true, info._plainfile);
    }

        // Lazy read
    {
        LazyFile file("myfile", File::CREATE, true);
        std::vector<char> buf(10, ' ');
        EXPECT_EQUAL(3u, file.read(&buf[0], 10, 0));
        EXPECT_EQUAL(std::string("foo"), std::string(&buf[0], 3));
    }
        // Lazy resize
    {
        LazyFile file("myfile", File::CREATE, true);
        file.resize(5);
        EXPECT_EQUAL(5, file.getFileSize());
    }
        // Lazy get file descriptor
    {
        LazyFile file("myfile", File::CREATE, true);
        int fd = file.getFileDescriptor();
        ASSERT_TRUE(fd != -1);
    }
}

void
Test::testSymlink()
{
    // Target exists
    {
        rmdir("mydir", true);
        mkdir("mydir");

        File f("mydir/myfile");
        f.open(File::CREATE | File::TRUNC);
        f.write("Hello World!\n", 13, 0);
        f.close();

        symlink("myfile", "mydir/linkyfile");
        EXPECT_TRUE(fileExists("mydir/linkyfile"));

        File f2("mydir/linkyfile");
        f2.open(File::READONLY);
        std::vector<char> vec(20, ' ');
        size_t read = f2.read(&vec[0], 20, 0);
        EXPECT_EQUAL(13u, read);
        EXPECT_EQUAL(std::string("Hello World!\n"), std::string(&vec[0], 13));
    }

    // POSIX symlink() fails
    {
        rmdir("mydir", true);
        mkdir("mydir/a", true);
        mkdir("mydir/b");
        try {
            // Link already exists
            symlink("a", "mydir/b");
            TEST_FATAL("Exception not thrown on already existing link");
        } catch (IoException& e) {
            EXPECT_EQUAL(IoException::ALREADY_EXISTS, e.getType());
        }
    }

    {
        rmdir("mydir", true);
        mkdir("mydir");

        File f("mydir/myfile");
        f.open(File::CREATE | File::TRUNC);
        f.write("Hello World!\n", 13, 0);
        f.close();
    }

    // readLink success
    {
        symlink("myfile", "mydir/linkyfile");
        EXPECT_EQUAL("myfile", readLink("mydir/linkyfile"));
    }
    // readLink failure
    {
        try {
            readLink("no/such/link");
        } catch (IoException& e) {
            EXPECT_EQUAL(IoException::NOT_FOUND, e.getType());
        }        
    }
}

void
Test::testReadAll()
{
    // Write text into a file.
    unlink("myfile");
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

} // vespalib

TEST_APPHOOK(vespalib::Test)

