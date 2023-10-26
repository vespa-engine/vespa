// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bitdecode64.h"

namespace search::fakedata {

template class BitDecode64<true>;
template class BitDecode64<false>;

}
