// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2000-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once


/*
 * Define FastS_abort and FastS_assert macro's.
 */

/**
 * This logs an "assertion failed" message and aborts.
 */
extern void __FastS_assert_fail(const char *assertion,
                                const char *file,
                                unsigned int line,
                                const char * function);

/**
 * This logs an "abort" message and aborts.
 */
extern void __FastS_abort(const char *message,
                          const char *file,
                          unsigned int line,
                          const char * function);

# ifndef __STRING
#  define __STRING(x) #x
# endif

# ifndef V_TAG
#  define V_TAG "NOTAG"
# endif

# ifndef __ASSERT_FUNCTION
#  define __ASSERT_FUNCTION NULL
# endif


# define FastS_abort(msg) \
  (__FastS_abort(msg, __FILE__, __LINE__, __ASSERT_FUNCTION), abort())

# ifndef NDEBUG
#  define FastS_assert(expr) \
  ((void) ((expr) ? 0 : \
           (__FastS_assert_fail (__STRING(expr), \
                                 __FILE__, __LINE__, \
                                 __ASSERT_FUNCTION), 0)))
# else
#  define FastS_assert(expr)
# endif // #ifndef NDEBUG

