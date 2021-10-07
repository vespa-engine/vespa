// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include "table.h"

namespace search {
namespace fef {

/**
 * This is an interface for a factory used to create tables.
 **/
class ITableFactory
{
public:
    /**
     * Convenience typedef for a shared pointer to this class.
     **/
    typedef std::shared_ptr<ITableFactory> SP;

    /**
     * Creates a table with the given name.
     * Table::SP(NULL) is returned if the table cannot be created.
     **/
    virtual Table::SP createTable(const vespalib::string & name) const = 0;

    /**
     * Virtual destructor to allow safe subclassing.
     **/
    virtual ~ITableFactory() {}
};

} // namespace fef
} // namespace search

