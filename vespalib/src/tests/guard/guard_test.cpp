// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/guard.h>
#include <fcntl.h>
#include <unistd.h>

using namespace vespalib;

class Test : public TestApp
{
public:
    void testFilePointer();
    void testFileDescriptor();
    void testDirPointer();
    void testValueGuard();
    void testMaxValueGuard();
    void testCounterGuard();
    int Main() override;
};

void
Test::testFilePointer()
{
    {
        FilePointer file(fopen("bogus", "r"));
        EXPECT_TRUE(!file.valid());
    }
    {
        FilePointer file(fopen("filept.txt", "w"));
        EXPECT_TRUE(file.valid());
        fprintf(file, "Hello");
    }
    {
        FilePointer file(fopen("filept.txt", "r"));
        EXPECT_TRUE(file.valid());
        char tmp[128];
        char *fgetsres = fgets(tmp, sizeof(tmp), file);
        ASSERT_EQUAL(tmp, fgetsres);
        EXPECT_TRUE(strcmp(tmp, "Hello") == 0);
    }
    {
        FILE *pt = NULL;
        {
            FilePointer file(fopen("filept.txt", "r"));
            EXPECT_TRUE(file.valid());
            pt = file;
        }
        EXPECT_TRUE(pt != NULL);
        // char tmp[128];
        // EXPECT_TRUE(fgets(tmp, sizeof(tmp), pt) == NULL);
    }
    {
        FilePointer file(fopen("filept.txt", "w"));
        EXPECT_TRUE(file.valid());
        fprintf(file, "World");

        file.reset(fopen("filept.txt", "r"));
        EXPECT_TRUE(file.valid());
        char tmp[128];
        char *fgetsres = fgets(tmp, sizeof(tmp), file.fp());
        ASSERT_EQUAL(tmp, fgetsres);
        EXPECT_TRUE(strcmp(tmp, "World") == 0);

        FILE *ref = file.fp();
        FILE *fp = file.release();
        EXPECT_TRUE(fp != NULL);
        EXPECT_TRUE(fp == ref);
        EXPECT_TRUE(!file.valid());
        EXPECT_TRUE(file.fp() == NULL);
        fclose(fp);
    }
}

void
Test::testFileDescriptor()
{
    {
        FileDescriptor file(open("bogus", O_RDONLY));
        EXPECT_TRUE(!file.valid());
    }
    {
        FileDescriptor file(open("filedesc.txt", O_WRONLY | O_CREAT, 0644));
        EXPECT_TRUE(file.valid());
        EXPECT_TRUE((size_t)write(file.fd(), "Hello", strlen("Hello")) == strlen("Hello"));
    }
    {
        FileDescriptor file(open("filedesc.txt", O_RDONLY));
        EXPECT_TRUE(file.valid());
        char tmp[128];
        size_t res = read(file.fd(), tmp, sizeof(tmp));
        EXPECT_TRUE(res == strlen("Hello"));
        tmp[res] = '\0';
        EXPECT_TRUE(strcmp(tmp, "Hello") == 0);
    }
    {
        int fd = -1;
        {
            FileDescriptor file(open("filedesc.txt", O_RDONLY));
            EXPECT_TRUE(file.valid());
            fd = file.fd();
        }
        char tmp[128];
        EXPECT_TRUE(read(fd, tmp, sizeof(tmp)) == -1);
    }
    {
        FileDescriptor file(open("filedesc.txt", O_WRONLY | O_CREAT, 0644));
        EXPECT_TRUE(file.valid());
        EXPECT_TRUE((size_t)write(file.fd(), "World", strlen("World")) == strlen("World"));

        file.reset(open("filedesc.txt", O_RDONLY));
        EXPECT_TRUE(file.valid());
        char tmp[128];
        size_t res = read(file.fd(), tmp, sizeof(tmp));
        EXPECT_TRUE(res == strlen("World"));
        tmp[res] = '\0';
        EXPECT_TRUE(strcmp(tmp, "World") == 0);

        int ref = file.fd();
        int fd = file.release();
        EXPECT_TRUE(fd >= 0);
        EXPECT_TRUE(fd == ref);
        EXPECT_TRUE(!file.valid());
        EXPECT_TRUE(file.fd() == -1);
        close(fd);
    }
}

void
Test::testDirPointer()
{
    {
        DirPointer dir(opendir("bogus"));
        EXPECT_TRUE(!dir.valid());
    }
    {
        DirPointer dir(opendir(TEST_PATH("").c_str()));
        EXPECT_TRUE(dir.valid());

        dirent *de;
        bool foundGuardCpp = false;
        while ((de = readdir(dir)) != NULL) {
            if (strcmp(de->d_name, "guard_test.cpp") == 0) {
                foundGuardCpp = true;
            }
        }
        EXPECT_TRUE(foundGuardCpp);
    }
    {
        DIR *dp = NULL;
        {
            DirPointer dir(opendir("."));
            EXPECT_TRUE(dir.valid());
            dp = dir;
        }
        EXPECT_TRUE(dp != NULL);
        // EXPECT_TRUE(readdir(dp) == NULL);
    }
    {
        DirPointer dir(opendir("."));
        EXPECT_TRUE(dir.valid());
        dir.reset(opendir("."));
        EXPECT_TRUE(dir.valid());

        DIR *ref = dir.dp();
        DIR *dp = dir.release();
        EXPECT_TRUE(dp != NULL);
        EXPECT_TRUE(dp == ref);
        EXPECT_TRUE(!dir.valid());
        EXPECT_TRUE(dir.dp() == NULL);
        closedir(dp);
    }
}

void
Test::testValueGuard()
{
    int value = 10;
    {
        ValueGuard<int> guard(value);
        value = 20;
        EXPECT_TRUE(value == 20);
    }
    EXPECT_TRUE(value == 10);
    {
        ValueGuard<int> guard(value, 50);
        value = 20;
        EXPECT_TRUE(value == 20);
    }
    EXPECT_TRUE(value == 50);
    {
        ValueGuard<int> guard(value);
        value = 20;
        guard.update(100);
        EXPECT_TRUE(value == 20);
    }
    EXPECT_TRUE(value == 100);
    {
        ValueGuard<int> guard(value);
        value = 20;
        guard.dismiss();
        EXPECT_TRUE(value == 20);
    }
    EXPECT_TRUE(value == 20);
}

void
Test::testMaxValueGuard()
{
    int value = 10;
    {
        MaxValueGuard<int> guard(value);
        value = 20;
        EXPECT_TRUE(value == 20);
    }
    EXPECT_TRUE(value == 10);
    {
        MaxValueGuard<int> guard(value);
        value = 5;
        EXPECT_TRUE(value == 5);
    }
    EXPECT_TRUE(value == 5);
    {
        MaxValueGuard<int> guard(value, 50);
        value = 100;
        EXPECT_TRUE(value == 100);
    }
    EXPECT_TRUE(value == 50);
    {
        MaxValueGuard<int> guard(value);
        value = 200;
        guard.update(100);
        EXPECT_TRUE(value == 200);
    }
    EXPECT_TRUE(value == 100);
    {
        MaxValueGuard<int> guard(value);
        value = 200;
        guard.dismiss();
        EXPECT_TRUE(value == 200);
    }
    EXPECT_TRUE(value == 200);
}

void
Test::testCounterGuard()
{
    int cnt = 10;
    {
        EXPECT_TRUE(cnt == 10);
        CounterGuard guard(cnt);
        EXPECT_TRUE(cnt == 11);
    }
    EXPECT_TRUE(cnt == 10);
}

int
Test::Main()
{
    TEST_INIT("guard_test");
    testFilePointer();
    testFileDescriptor();
    testDirPointer();
    testValueGuard();
    testMaxValueGuard();
    testCounterGuard();
    TEST_DONE();
}

TEST_APPHOOK(Test)
