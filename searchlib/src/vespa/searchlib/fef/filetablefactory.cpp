// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "filetablefactory.h"
#include <fstream>

#include <vespa/log/log.h>
LOG_SETUP(".fef.filetablefactory");

namespace search::fef {

FileTableFactory::FileTableFactory(const vespalib::string & path) :
    _path(path)
{
}

Table::SP
FileTableFactory::createTable(const vespalib::string & name) const
{
    vespalib::string completeName(_path);
    completeName.append("/");
    completeName.append(name);
    std::ifstream file(completeName.c_str(), std::ifstream::in);
    if (file.is_open()) {
        Table::SP table(new Table());
        for (;;) {
            double val = 0;
            file >> val;
            if (!file.good()) {
                break;
            }
            table->add(val);
        }
        return table;
    }
    LOG(warning, "Could not open file '%s' for creating table '%s'", completeName.c_str(), name.c_str());
    return Table::SP(NULL);
}

}
