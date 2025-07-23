// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#define VESPA_HWACCEL_TARGET_TYPE Sve2Accelerator
#define VESPA_HWACCEL_TARGET_NAME "SVE2"
#include "generic-inl.h"
#undef VESPA_HWACCEL_TARGET_TYPE
#undef VESPA_HWACCEL_TARGET_NAME
