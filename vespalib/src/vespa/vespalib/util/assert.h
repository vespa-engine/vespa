// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib::assert {

/**
 * How many times has asserts against this key failed.
 * @param key
 * @return
 */
size_t getNumAsserts(const char *key);

/**
 * Get the filename that will be used for remembering asserts.
 * @param key
 * @return
 */
vespalib::string getAssertLogFileName(const char *key);

/**
 * If there is no record on file that this assert has failed, it will be recorded and aborted.
 * However if there is a record of it, it will merely be logged the first and then every #freq time.
 * @param expr that failed the assert
 * @param key unique name of assert
 * @param logFreq how often will a failing assert be logged.
 */
void assertOnceOrLog(const char *expr, const char *key, size_t logFreq);

}

#define ASSERT_ONCE_OR_LOG(expr, key, freq) { \
    if ( ! (expr) ) {                  \
        vespalib::assert::assertOnceOrLog(#expr, key, freq); \
    }                                  \
}
