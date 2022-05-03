// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

/**
 * Convenience macros for determining if the build is instrumented using sanitizers.
 *
 * If any sanitizer is detected, VESPA_USE_SANITIZER will be defined as 1, otherwise not defined.
 * Additionally, the macro VESPA_USE_<SANITIZER_NAME>_SANITIZER will be defined in case code needs to
 * know exactly which sanitizer is being instrumented with.
 */

// Normalize compiler-specific sanitizer definitions
#ifndef __SANITIZE_ADDRESS__
#  if defined(__has_feature)
#    if __has_feature(address_sanitizer)
#      define __SANITIZE_ADDRESS__
#    endif
#  endif
#endif

#ifndef __SANITIZE_THREAD__
#  if defined(__has_feature)
#    if __has_feature(thread_sanitizer)
#      define __SANITIZE_THREAD__
#    endif
#  endif
#endif

#ifndef __SANITIZE_UNDEFINED__
#  if defined(__has_feature)
#    if __has_feature(undefined_sanitizer)
#      define __SANITIZE_UNDEFINED__
#    endif
#  endif
#endif

#ifdef __SANITIZE_ADDRESS__
#  define VESPA_USE_SANITIZER 1
#  define VESPA_USE_ADDRESS_SANITIZER 1
#  define VESPA_SANITIZER_NAME "address"
#endif

#ifdef __SANITIZE_THREAD__
#  define VESPA_USE_SANITIZER 1
#  define VESPA_USE_THREAD_SANITIZER 1
#  define VESPA_SANITIZER_NAME "thread"
#endif

#ifdef __SANITIZE_UNDEFINED__
#  define VESPA_USE_SANITIZER 1
#  define VESPA_USE_UNDEFINED_SANITIZER 1
#  define VESPA_SANITIZER_NAME "undefined"
#endif
