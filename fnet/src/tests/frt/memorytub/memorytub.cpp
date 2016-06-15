// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/fnet/frt/frt.h>

//---------------------------------------------------------------

enum {
  SMALL_ALLOCS = 90,
  BIG_ALLOCS   = 10,
  ALLOCS       = SMALL_ALLOCS + BIG_ALLOCS,
  SMALL_SIZE   = 407,
  BIG_SIZE     = 40700,
  NOID         = 99999
};

//---------------------------------------------------------------

struct Fixture {
  FRT_MemoryTub  _tub;
  char          *_res[ALLOCS];
  uint32_t       _i;
  uint32_t       _j;
  Fixture() : _tub(), _res(), _i(0), _j(0) {}
  bool overlap(char *start1, char *end1,
               char *start2, char *end2);
  bool inTub(char *pt, char *end);
  bool notInTub(char *pt, char *end);
};

//---------------------------------------------------------------

bool
Fixture::overlap(char *start1, char *end1,
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
Fixture::inTub(char *pt, char *end)
{
  for (char *p = pt; p < end; p++)
    if (!_tub.InTub(p))
      return false;
  return true;
}


bool
Fixture::notInTub(char *pt, char *end)
{
  for (char *p = pt; p < end; p++)
    if (_tub.InTub(p))
      return false;
  return true;
}

//---------------------------------------------------------------

TEST_F("memory tub", Fixture()) {
  for(f1._i = 0; f1._i < ALLOCS; f1._i++)
    f1._res[f1._i] = NULL;
  f1._i = NOID;
  f1._j = NOID;

  EXPECT_TRUE(!f1._tub.InTub(&f1._tub));
  EXPECT_TRUE((uint32_t)SMALL_SIZE < (uint32_t)FRT_MemoryTub::ALLOC_LIMIT);
  EXPECT_TRUE((uint32_t)BIG_SIZE   > (uint32_t)FRT_MemoryTub::ALLOC_LIMIT);
  EXPECT_TRUE((SMALL_SIZE * SMALL_ALLOCS)
         > (FRT_MemoryTub::FIXED_SIZE + FRT_MemoryTub::CHUNK_SIZE));
  TEST_FLUSH();

  for (f1._i = 0; f1._i < ALLOCS; f1._i++) {
    uint32_t size_i = f1._i < SMALL_ALLOCS ? SMALL_SIZE : BIG_SIZE;

    f1._res[f1._i] = (char *) f1._tub.Alloc(size_i);
    EXPECT_TRUE(((void *)f1._res[f1._i]) != ((void *)&f1._tub));
    memset(f1._res[f1._i], 0x55, size_i);
    EXPECT_TRUE(f1.inTub(f1._res[f1._i], f1._res[f1._i] + size_i));
  }
  TEST_FLUSH();

  for (f1._i = 0; f1._i < ALLOCS; f1._i++) {
    uint32_t size_i = f1._i < SMALL_ALLOCS ? SMALL_SIZE : BIG_SIZE;
    EXPECT_TRUE(f1.inTub(f1._res[f1._i], f1._res[f1._i] + size_i));

    for (f1._j = f1._i + 1; f1._j < ALLOCS; f1._j++) {
      uint32_t size_j = f1._j < SMALL_ALLOCS ? SMALL_SIZE : BIG_SIZE;
      EXPECT_TRUE(!f1.overlap(f1._res[f1._i], f1._res[f1._i] + size_i,
                      f1._res[f1._j], f1._res[f1._j] + size_j));
    }
  }
  TEST_FLUSH();

  f1._tub.Reset();
  f1._j = NOID;

  for (f1._i = 0; f1._i < ALLOCS; f1._i++) {
    uint32_t size_i = f1._i < SMALL_ALLOCS ? SMALL_SIZE : BIG_SIZE;
    EXPECT_TRUE(!f1.inTub(f1._res[f1._i], f1._res[f1._i] + size_i));
  }
  TEST_FLUSH();
}

TEST_MAIN() { TEST_RUN_ALL(); }
