// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib {

template <typename T, size_t N>
constexpr size_t arraysize(const T (&)[N]) { return N; }

}  // namespace vespalib

