// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "table.h"
#include <string>

namespace search::fef {

/**
 * This is an interface for a factory used to create tables.
 **/
class ITableFactory
{
public:
    /**
     * Convenience typedef for a shared pointer to this class.
     **/
    using SP = std::shared_ptr<ITableFactory>;

    /**
     * Creates a table with the given name.
     * Table::SP(NULL) is returned if the table cannot be created.
     **/
    virtual Table::SP createTable(const std::string & name) const = 0;

    /**
     * Virtual destructor to allow safe subclassing.
     **/
    virtual ~ITableFactory() = default;
};

}
