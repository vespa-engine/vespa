// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#define FASTOS_PREFIX(a) FastOS_##a

// New macros to support the new gcc visibility features.
#define VESPA_DLL_EXPORT __attribute__ ((visibility("default")))
#define VESPA_DLL_LOCAL  __attribute__ ((visibility("hidden")))
