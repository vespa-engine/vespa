// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//************************************************************************
/**
 * Class definition for FastOS_GCCX86_64__Prefetch.
 *
 * @author  Olaf Birkeland
 */

#pragma once


// x86 on GCC
class FastOS_GCCX86_64_Prefetch : public FastOS_PrefetchInterface
{
    enum Constants
    {
        PREFETCH_SIZE = 64
    };

public:
    inline static int GetPrefetchSize () { return PREFETCH_SIZE; }

    inline static void L0 (const void *data)
    {
      __asm__("prefetcht0 %0" : : "m" (data));
    }

    inline static void L1 (const void *data)
    {
      __asm__("prefetcht1 %0" : : "m" (data));
    }

    inline static void L2 (const void *data)
    {
      __asm__("prefetcht2 %0" : : "m" (data));
    }

    inline static void NT (const void *data)
    {
      __asm__("prefetchnta %0" : : "m" (data));
    }
};


