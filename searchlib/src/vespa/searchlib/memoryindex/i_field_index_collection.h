// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search::index { class FieldLengthCalculator; }

namespace search::memoryindex {

class FieldIndexRemover;
class IOrderedFieldIndexInserter;

/**
 * Interface class for a field index collection which can be used to
 * get the parts needed for wiring in field inverters.
 */
class IFieldIndexCollection {
public:
    virtual FieldIndexRemover &get_remover(uint32_t field_id) = 0;
    virtual IOrderedFieldIndexInserter &get_inserter(uint32_t field_id) = 0;
    virtual index::FieldLengthCalculator &get_calculator(uint32_t field_id) = 0;
    virtual ~IFieldIndexCollection() = default;
};

}
