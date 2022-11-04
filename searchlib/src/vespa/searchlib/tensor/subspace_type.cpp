// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "subspace_type.h"
#include <vespa/eval/eval/value_type.h>

namespace search::tensor {

SubspaceType::SubspaceType(const vespalib::eval::ValueType& type)
    : _cell_type(type.cell_type()),
      _size(type.dense_subspace_size()),
      _mem_size(vespalib::eval::CellTypeUtils::mem_size(_cell_type, _size))
{
}

}
