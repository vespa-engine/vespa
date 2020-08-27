// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/tensor/tensor.h>

namespace search::tensor {

extern std::unique_ptr<vespalib::tensor::Tensor>
deserialize_tensor(const void *data, size_t size);

} // namespace
