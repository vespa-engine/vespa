// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#ifdef __linux__
#include "linux_file.h"
using FastOS_File = FastOS_Linux_File;
#else
#include "unix_file.h"
using FastOS_File = FastOS_UNIX_File;
#endif
