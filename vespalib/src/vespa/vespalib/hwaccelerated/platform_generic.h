// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

// FIXME this feels a bit dirty, but will go away if we standardize on Highway anyway
#ifdef __x86_64__
#include "x64_generic.h"
namespace vespalib::hwaccelerated {
using PlatformGenericAccelerator = X64GenericAccelerator;
}
#else
#include "neon.h"
namespace vespalib::hwaccelerated {
using PlatformGenericAccelerator = NeonAccelerator;
}
#endif

