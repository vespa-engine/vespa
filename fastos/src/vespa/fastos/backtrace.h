// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#ifdef __cplusplus
extern "C" {
#endif

int FastOS_backtrace (void **array, int size);

#if defined(__x86_64__)
int backtrace (void **array, int size);
#endif

#ifdef __cplusplus
}
#endif

