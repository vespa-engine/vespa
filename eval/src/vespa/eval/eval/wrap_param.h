// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib::eval::tensor_function {

template <typename T, typename IN> uint64_t wrap_param(const IN &value_in) {
    const T &value = value_in;
    static_assert(sizeof(uint64_t) == sizeof(&value));
    return (uint64_t)&value;
}

template <typename T> const T &unwrap_param(uint64_t param) {
    return *((const T *)param);
}

} // namespace
