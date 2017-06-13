// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//****************************************************************************
/**
 * @file
 * Type definitions used in FastOS.
 * @author  Oivind H. Danielsen
 * @date    Creation date: 2000-01-18
 *****************************************************************************/


#pragma once

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
#ifndef __STDC_FORMAT_MACROS
  #define __STDC_FORMAT_MACROS
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

#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>

#include <sys/resource.h>
#include <signal.h>

#include <sys/time.h>
#include <time.h>

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
#include <inttypes.h>
#include <getopt.h>

#if (__WORDSIZE == 64)

#else
  #error "Only LP 64 environments are supported."
#endif

#define FASTOS_PREFIX(a) FastOS_##a

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
