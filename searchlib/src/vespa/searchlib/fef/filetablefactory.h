// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "itablefactory.h"

namespace search::fef {

/**
 * This factory class is used to instantiate tables that are stored in files on disk.
 **/
class FileTableFactory : public ITableFactory
{
private:
    vespalib::string _path;

public:
    /**
     * Creates a new factory for table files that are located in the given path.
     **/
    FileTableFactory(const vespalib::string & path);

    /**
     * Creates a table by reading the file 'path/name' and setting up a Table object.
     * The numbers in the file should be separated with ' ' or '\n'.
     * Table::SP(NULL) is returned if the file 'path/name' is not found.
     **/
    Table::SP createTable(const vespalib::string & name) const override;
};

}
