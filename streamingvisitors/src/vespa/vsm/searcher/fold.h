// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vsm/common/document.h>

namespace vsm {

const search::byte * sse2_foldaa(const search::byte * toFoldOrg, size_t sz, search::byte * foldedOrg);
const search::byte * sse2_foldua(const search::byte * toFoldOrg, size_t sz, search::byte * foldedOrg);

}

