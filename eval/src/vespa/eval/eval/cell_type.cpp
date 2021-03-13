// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "cell_type.h"
#include <stdio.h>
#include <cstdlib>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string_short::fmt;

namespace vespalib::eval {

uint32_t
CellTypeUtils::alignment(CellType cell_type)
{
    return TypifyCellType::resolve(cell_type, [](auto t)->uint32_t
                                   {
                                       using T = typename decltype(t)::type;
                                       return alignof(T);
                                   });
}

size_t
CellTypeUtils::mem_size(CellType cell_type, size_t sz)
{
    return TypifyCellType::resolve(cell_type, [sz](auto t)->size_t
                                   {
                                       using T = typename decltype(t)::type;
                                       return (sz * sizeof(T));
                                   });
}

std::vector<CellType>
CellTypeUtils::list_types()
{
    return {CellType::FLOAT, CellType::DOUBLE};
}

}
