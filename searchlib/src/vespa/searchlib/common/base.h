// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1999-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

namespace search {

// This constant defines the illegal/undefined value for unsigned 32-bit
// integer ids. Use this instead of the function below to get less
// overhead with not-so-smart compilers.

const uint32_t NoId32 = static_cast<uint32_t>(-1);

}

