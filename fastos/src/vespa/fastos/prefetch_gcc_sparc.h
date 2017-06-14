// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//************************************************************************
/**
 * Class definition for FastOS_GCCSPARC_Prefetch.
 *
 * @author  Olaf Birkeland
 */

#pragma once


// SPARC on GCC
class FastOS_GCCSPARC_Prefetch : public FastOS_PrefetchInterface
{
    enum Constants
    {
        PREFETCH_SIZE = 64
    };

public:
    inline static int GetPrefetchSize () { return PREFETCH_SIZE; }

    inline static void L0 (const void *data)
    {
        __asm__ ("prefetch [%0], 0" : : "r" (data));
    }
    inline static void L1 (const void *data)
    {
        __asm__ ("prefetch [%0], 0" : : "r" (data));
    }
    inline static void L2 (const void *data)
    {
        __asm__ ("prefetch [%0], 4" : : "r" (data));
    }
    inline static void NT (const void *data)
    {
        __asm__ ("prefetch [%0], 1" : : "r" (data));
    }
};


