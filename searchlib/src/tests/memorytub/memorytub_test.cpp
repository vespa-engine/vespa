// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("test_memorytub");

#include <vespa/searchlib/util/memorytub.h>
#include <vespa/vespalib/testkit/testapp.h>

#define MEMTUB_ARRAY_ALLOC(tub, type, size) ((type *) tub->Alloc(sizeof(type) * size))


enum {
    SMALL_STRING      = 100,
    BIG_STRING        = 100000,
    SMALL_SMALL_ARRAY = 10,
    BIG_SMALL_ARRAY   = 1000
};


class Small
{
public:
    char filler[SMALL_STRING];
};


class Big
{
public:
    char filler[BIG_STRING];
};


class Test : public vespalib::TestApp
{
private:
    search::util::SmallMemoryTub _tub;

public:
    bool Overlap(char *start1, char *end1,
                 char *start2, char *end2);
    bool InTub(char *pt, char *end);
    bool NotInTub(char *pt, char *end);
    int Main();

    Test(void)
        : _tub()
    {
    }
};


bool
Test::Overlap(char *start1, char *end1,
              char *start2, char *end2)
{
    if (start1 == end1)
        return false;

    if (start2 == end2)
        return false;

    if (start2 >= start1 && start2 < end1)
        return true;

    if (end2 > start1 && end2 <= end1)
        return true;

    if (start1 >= start2 && start1 < end2)
        return true;

    if (end1 > start2 && end1 <= end2)
        return true;

    return false;
}


bool
Test::InTub(char *pt, char *end)
{
    for (char *p = pt; p < end; p++)
        if (!_tub.InTub(p))
            return false;
    return true;
}


bool
Test::NotInTub(char *pt, char *end)
{
    for (char *p = pt; p < end; p++)
        if (_tub.InTub(p))
            return false;
    return true;
}


int
Test::Main()
{
    TEST_INIT("memorytub-test");

    Small *small             = NULL;
    Big   *big               = NULL;
    char  *small_string      = NULL;
    char  *big_string        = NULL;
    Small *small_small_array = NULL;
    Small *big_small_array   = NULL;

    EXPECT_TRUE(!_tub.InTub(&_tub));

    EXPECT_TRUE(sizeof(Small) < _tub.GetAllocLimit());
    EXPECT_TRUE(sizeof(Big)   > _tub.GetAllocLimit());
    EXPECT_TRUE(SMALL_STRING  < _tub.GetAllocLimit());
    EXPECT_TRUE(BIG_STRING    > _tub.GetAllocLimit());
    EXPECT_TRUE(sizeof(Small) * SMALL_SMALL_ARRAY < _tub.GetAllocLimit());
    EXPECT_TRUE(sizeof(Small) * BIG_SMALL_ARRAY   > _tub.GetAllocLimit());

    small = new (&_tub) Small();
    EXPECT_TRUE(((void *)small) != ((void *)&_tub));
    EXPECT_TRUE(InTub((char *)small, (char *)(small + 1)));

    big = new (&_tub) Big();
    EXPECT_TRUE(((void *)big) != ((void *)&_tub));
    EXPECT_TRUE(InTub((char *)big, (char *)(big + 1)));

    small_string = MEMTUB_ARRAY_ALLOC((&_tub), char, SMALL_STRING);
    EXPECT_TRUE(((void *)small_string) != ((void *)&_tub));
    EXPECT_TRUE(InTub(small_string, small_string + SMALL_STRING));

    big_string = MEMTUB_ARRAY_ALLOC((&_tub), char, BIG_STRING);
    EXPECT_TRUE(((void *)big_string) != ((void *)&_tub));
    EXPECT_TRUE(InTub(big_string, big_string + BIG_STRING));

    small_small_array = MEMTUB_ARRAY_ALLOC((&_tub), Small, SMALL_SMALL_ARRAY);
    EXPECT_TRUE(((void *)small_small_array) != ((void *)&_tub));
    EXPECT_TRUE(InTub((char *)small_small_array, (char *)(small_small_array + SMALL_SMALL_ARRAY)));

    big_small_array = MEMTUB_ARRAY_ALLOC((&_tub), Small, BIG_SMALL_ARRAY);
    EXPECT_TRUE(((void *)big_small_array) != ((void *)&_tub));
    EXPECT_TRUE(InTub((char *)big_small_array, (char *)(big_small_array + BIG_SMALL_ARRAY)));


    EXPECT_TRUE(!Overlap((char *)small, (char *)(small + 1),
                    (char *)big, (char *)(big + 1)));

    EXPECT_TRUE(!Overlap((char *)small, (char *)(small + 1),
                    small_string, small_string + SMALL_STRING));

    EXPECT_TRUE(!Overlap((char *)small, (char *)(small + 1),
                    big_string, big_string + BIG_STRING));

    EXPECT_TRUE(!Overlap((char *)small, (char *)(small + 1),
                    (char *)small_small_array, (char *)(small_small_array + SMALL_SMALL_ARRAY)));

    EXPECT_TRUE(!Overlap((char *)small, (char *)(small + 1),
                    (char *)big_small_array, (char *)(big_small_array + BIG_SMALL_ARRAY)));


    EXPECT_TRUE(!Overlap((char *)big, (char *)(big + 1),
                    small_string, small_string + SMALL_STRING));

    EXPECT_TRUE(!Overlap((char *)big, (char *)(big + 1),
                    big_string, big_string + BIG_STRING));

    EXPECT_TRUE(!Overlap((char *)big, (char *)(big + 1),
                    (char *)small_small_array, (char *)(small_small_array + SMALL_SMALL_ARRAY)));

    EXPECT_TRUE(!Overlap((char *)big, (char *)(big + 1),
                    (char *)big_small_array, (char *)(big_small_array + BIG_SMALL_ARRAY)));


    EXPECT_TRUE(!Overlap(small_string, small_string + SMALL_STRING,
                    big_string, big_string + BIG_STRING));

    EXPECT_TRUE(!Overlap(small_string, small_string + SMALL_STRING,
                    (char *)small_small_array, (char *)(small_small_array + SMALL_SMALL_ARRAY)));

    EXPECT_TRUE(!Overlap(small_string, small_string + SMALL_STRING,
                    (char *)big_small_array, (char *)(big_small_array + BIG_SMALL_ARRAY)));


    EXPECT_TRUE(!Overlap(big_string, big_string + BIG_STRING,
                    (char *)small_small_array, (char *)(small_small_array + SMALL_SMALL_ARRAY)));

    EXPECT_TRUE(!Overlap(big_string, big_string + BIG_STRING,
                    (char *)big_small_array, (char *)(big_small_array + BIG_SMALL_ARRAY)));


    EXPECT_TRUE(!Overlap((char *)small_small_array, (char *)(small_small_array + SMALL_SMALL_ARRAY),
                    (char *)big_small_array, (char *)(big_small_array + BIG_SMALL_ARRAY)));


    _tub.Reset();
    EXPECT_TRUE(NotInTub((char *)small, (char *)(small + 1)));
    EXPECT_TRUE(NotInTub((char *)big, (char *)(big + 1)));
    EXPECT_TRUE(NotInTub(small_string, small_string + SMALL_STRING));
    EXPECT_TRUE(NotInTub(big_string, big_string + BIG_STRING));
    EXPECT_TRUE(NotInTub((char *)small_small_array, (char *)(small_small_array + SMALL_SMALL_ARRAY)));
    EXPECT_TRUE(NotInTub((char *)big_small_array, (char *)(big_small_array + BIG_SMALL_ARRAY)));
    TEST_DONE();
}

TEST_APPHOOK(Test)
