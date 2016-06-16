// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//****************************************************************************
/**
 * @file
 * Type definitions used in FastOS.
 * @author  Oivind H. Danielsen
 * @date    Creation date: 2000-01-18
 *****************************************************************************/


#pragma once

#include <vespa/fastos/autoconf.h>

/**
 * @def __STDC_LIMIT_MACROS
 * According to C99, C++ implementations will only define UINT64_MAX
 * etc when __STDC_LIMIT_MACROS is defined when including stdint.h.
 * UINT64_C etc will only be defined when __STDC_CONSTANT_MACROS is
 * defined.  Since this file can be included from any of the files
 * below, we define the behaviour here.
 */
#ifndef __STDC_LIMIT_MACROS
#define __STDC_LIMIT_MACROS 1
#endif
#ifndef __STDC_CONSTANT_MACROS
#define __STDC_CONSTANT_MACROS 1
#endif

#include <assert.h>

#include <pthread.h>
#include <sys/mman.h>

#ifdef __TYPES_H_PTHREAD_U98
#undef __USE_UNIX98
#endif

#include <sys/types.h>
#include <sys/uio.h>
#include <sys/param.h>
#include <sys/wait.h>
#include <sys/utsname.h>
#include <rpc/types.h>
#include <stdarg.h>
#include <ctype.h>

#ifndef __USE_UNIX98
#define __TYPES_H_UNISTD_U98
#define __USE_UNIX98
#endif
#include <unistd.h>
#ifdef __TYPES_H_UNISTD_U98
#undef __USE_UNIX98
#endif

#include <dirent.h>

#include <sys/socket.h>

#ifndef SHUT_RDWR
#define SHUT_RDWR 2
#endif

#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>

#include <sys/resource.h>
#include <signal.h>

#include <sys/time.h>
#include <time.h>

#define FASTOS_EMFILE_VERIFIED (-1)
#ifdef EMFILE
#undef  FASTOS_EMFILE_VERIFIED
#define FASTOS_EMFILE_VERIFIED EMFILE
#endif

#define FASTOS_ENFILE_VERIFIED (-1)
#ifdef ENFILE
#undef  FASTOS_ENFILE_VERIFIED
#define FASTOS_ENFILE_VERIFIED ENFILE
#endif

#ifndef __USE_GNU
#define __USE_GNU  /* for O_DIRECT define */
#define __TYPES_H_DIRECTIO_GNU
#endif

#include <fcntl.h>

#ifdef __TYPES_H_DIRECTIO_GNU
#undef __USE_GNU  /* for O_DIRECT define */
#endif

#include <stdio.h>
#include <stdlib.h>

#include <string.h>
#include <errno.h>
#include <sys/stat.h>
#include <limits.h>
#include <float.h>

#include <netinet/tcp.h>

#define FASTOS_PREFIX(a) FastOS_##a

#include <inttypes.h>

#ifndef INT64_C
#ifdef FASTOS_64BIT_LONG
#define INT64_C(c) c ## L
#else
# warning "You need to define INT64_C or find a header that defines this macro"
#endif
#endif /* INT64_C */

#ifndef UINT64_C
#ifdef FASTOS_64BIT_LONG
#define UINT64_C(c) c ## UL
#else
#define UINT64_C(c) c ## ULL
#endif
#endif /* UINT64_C */


#include <getopt.h>

/* 64bit printf specifiers */
#ifdef FASTOS_64BIT_LONG
#ifndef PRId64
#define PRId64        "ld"
#endif

#ifndef PRIu64
#define PRIu64        "lu"
#endif

#ifndef PRIo64
#define PRIo64        "lo"
#endif

#ifndef PRIx64
#define PRIx64        "lx"
#endif

#ifndef PRIX64
#define PRIX64        "lX"
#endif

#ifndef SCNd64
#define SCNd64        "ld"
#endif

#ifndef SCNu64
#define SCNu64        "lu"
#endif

#ifndef SCNo64
#define SCNo64        "lo"
#endif

#ifndef SCNx64
#define SCNx64        "lx"
#endif

#ifndef SCNX64
#define SCNX64        "lX"
#endif

#else /* ! FASTOS_64BIT_LONG */

#ifndef PRId64
#define PRId64        "lld"
#endif

#ifndef PRIu64
#define PRIu64        "llu"
#endif

#ifndef PRIo64
#define PRIo64        "llo"
#endif

#ifndef PRIx64
#define PRIx64        "llx"
#endif

#ifndef PRIX64
#define PRIX64        "llX"
#endif

#ifndef SCNd64
#define SCNd64        "lld"
#endif

#ifndef SCNu64
#define SCNu64        "llu"
#endif

#ifndef SCNo64
#define SCNo64        "llo"
#endif

#ifndef SCNx64
#define SCNx64        "llx"
#endif

#ifndef SCNX64
#define SCNX64        "llX"
#endif

#endif /* FASTOS_64BIT_LONG */

#ifndef PRId32
#define PRId32        "d"
#endif

#ifndef PRIu32
#define PRIu32        "u"
#endif

#ifndef PRIo32
#define PRIo32        "o"
#endif

#ifndef PRIx32
#define PRIx32        "x"
#endif

#ifndef PRIX32
#define PRIX32        "X"
#endif

#ifndef SCNd32
#define SCNd32        "d"
#endif

#ifndef SCNu32
#define SCNu32        "u"
#endif

#ifndef SCNo32
#define SCNo32        "o"
#endif

#ifndef SCNx32
#define SCNx32        "x"
#endif

#ifndef SCNX32
#define SCNX32        "X"
#endif


typedef pthread_t FastOS_ThreadId;

#define FASTOS_EXTERNC extern "C"
#define FASTOS_KLASS   class
