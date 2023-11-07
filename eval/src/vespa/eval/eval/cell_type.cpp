// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "cell_type.h"

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
    return {CellType::DOUBLE, CellType::FLOAT, CellType::BFLOAT16, CellType::INT8 };
}

std::vector<CellType>
CellTypeUtils::list_stable_types()
{
    return {CellType::DOUBLE, CellType::FLOAT};
}

std::vector<CellType>
CellTypeUtils::list_unstable_types()
{
    return {CellType::BFLOAT16, CellType::INT8 };
}

}
