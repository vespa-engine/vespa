// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
   @author Thomas F. Gundersen
   @version $Id$
   @date 2004-03-15
*/

#pragma once

/* Returns pointer to a key in compacted format, ie 16 unsigned chars */

#ifdef __cplusplus
extern "C" {
void fastc_md5sum(const void *s, size_t len, unsigned char *key);
}
#else
void fastc_md5sum(const void *s, size_t len, unsigned char *key);
#endif

