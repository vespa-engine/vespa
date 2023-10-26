// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <stddef.h>

/* Returns pointer to a key in compacted format, ie 16 unsigned chars */

#ifdef __cplusplus
extern "C" {
void fastc_md5sum(const void *s, size_t len, unsigned char *key);
}
#else
void fastc_md5sum(const void *s, size_t len, unsigned char *key);
#endif

