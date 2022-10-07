// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace vespalib { class nbostream; }
namespace vespalib::eval { struct Value; }

namespace search::tensor {

extern std::unique_ptr<vespalib::eval::Value>
deserialize_tensor(vespalib::nbostream &stream);

} // namespace
