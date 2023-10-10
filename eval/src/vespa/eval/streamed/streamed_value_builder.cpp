// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "streamed_value_builder.h"

namespace vespalib::eval {

template<typename T>
StreamedValueBuilder<T>::~StreamedValueBuilder() = default;

template class StreamedValueBuilder<double>;
template class StreamedValueBuilder<float>;
template class StreamedValueBuilder<BFloat16>;
template class StreamedValueBuilder<Int8Float>;

} // namespace
