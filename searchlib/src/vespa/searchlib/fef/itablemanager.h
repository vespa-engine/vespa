// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "table.h"

namespace search::fef {

/**
 * This is an interface used to access registered tables.
 **/
class ITableManager
{
public:
    /**
     * Returns a const view of the table with the given name or NULL if not found.
     **/
    virtual const Table * getTable(const vespalib::string & name) const = 0;
    virtual ~ITableManager() = default;
};

}
