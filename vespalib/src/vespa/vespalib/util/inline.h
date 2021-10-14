// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#ifndef noinline__
#ifdef __GNUC__
#define noinline__ __attribute__((noinline))
#endif
#ifndef noinline__
#define noinline__
#endif
#endif

#ifndef always_inline__
#ifdef __GNUC__
/* if user specifies -O -fno-inline the compiler may get confused */
#if !defined(__NO_INLINE__) && defined(__OPTIMIZE__)
#define always_inline__ __attribute__((always_inline))
#endif
#endif
#ifndef always_inline__
#define always_inline__
#endif
#endif

