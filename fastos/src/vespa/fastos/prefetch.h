// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//************************************************************************
/**
 * @file
 * Class definition for FastOS_PrefetchInterface.
 *
 * @author  Olaf Birkeland
 */

#pragma once

// If a known architecture/compiler is found, PREFETCH_IMPLEMENTED is
// defined.


/**
 * The prefetch functions are used to improve cache management. They
 * will all try to bring data into cache before actually being needed by the
 * code, thus avoiding cache misses. All functions take the address of the
 * element to be prefetched as a parameter. An illegal address will NOT cause
 * any exceptions, thus prefetch can be used speculatively.
 * <p>
 * Prefetch is always done in entire cache line sizes. The cache line size is
 * platform dependent, and defined by the method @ref GetPrefetchSize()
 * (in bytes), usually 32 or 64 bytes. Use this constant to scale the
 * unroll-factor of loops etc. Prefetch is applied to the cache line
 * surrounding the argument address, thus the argument does not need
 * to be aligned in any way.
 * <p>
 * Prefetch has no side effects, and can be omitted without altering functional
 * code behavior (e.g. for easier debugging). Not all implementations have all
 * the defined cache levels, thus some functions will be aliases on some
 * platforms.
 * <p>
 * Performance considerations:<br>
 * <ul>
 * <li>Prefetch only once for each cache line. Manual loop unrolling is thus
 * likely to be needed.</li>
 * <li>How far ahead prefetch should be used is platform and algorithm
 * dependent.</li>
 * <li>Prefetching too early can potentially decrease performance due to cache
 * trashing.</li></ul>
 */
class FastOS_PrefetchInterface
{
public:
    virtual ~FastOS_PrefetchInterface(void) { }
};

#ifdef GCC_X86_64
# include "prefetch_gcc_x86_64.h"
typedef FastOS_GCCX86_64_Prefetch FASTOS_PREFIX(Prefetch);
#else
/**
 * Default fallback for unsupported architecture/compilers
 */
class FastOS_Dummy_Prefetch : public FastOS_PrefetchInterface
{
public:
    inline static int GetPrefetchSize () { return 32; };
    inline static void L0(const void *data) { (void)data; };
    inline static void L1(const void *data) { (void)data; };
    inline static void L2(const void *data) { (void)data; };
    inline static void NT(const void *data) { (void)data; };
};
typedef FastOS_Dummy_Prefetch FASTOS_PREFIX(Prefetch);

#endif

