// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/value.h>

namespace search::tensor {

extern std::unique_ptr<vespalib::eval::Value>
deserialize_tensor(const void *data, size_t size);

} // namespace
