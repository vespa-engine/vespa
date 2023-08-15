// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/guard.h>
#include <fcntl.h>
#include <unistd.h>

using namespace vespalib;

TEST("testFilePointer")
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

TEST("testFileDescriptor")
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

TEST("testCounterGuard")
{
    int cnt = 10;
    {
        EXPECT_TRUE(cnt == 10);
        CounterGuard guard(cnt);
        EXPECT_TRUE(cnt == 11);
    }
    EXPECT_TRUE(cnt == 10);
}

TEST_MAIN() { TEST_RUN_ALL(); }