// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "filesource.h"
#include <vespa/config/subscription/sourcespec.h>
#include <vespa/config/common/misc.h>
#include <vespa/config/common/iconfigholder.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <sys/stat.h>

using vespalib::asciistream;

namespace config {

FileSource::FileSource(std::shared_ptr<IConfigHolder> holder, const vespalib::string & fileName)
    : _holder(std::move(holder)),
      _fileName(fileName),
      _lastLoaded(-1),
      _generation(1)
{ }

FileSource::~FileSource() = default;

void
FileSource::getConfig()
{
    StringVector lines(readConfigFile(_fileName));
    int64_t last = getLast(_fileName);

    if (last > _lastLoaded) {
        _holder->handle(std::make_unique<ConfigUpdate>(ConfigValue(lines), true, _generation));
        _lastLoaded = last;
    } else {
        _holder->handle(std::make_unique<ConfigUpdate>(ConfigValue(lines), false, _generation));
    }
}

void
FileSource::reload(int64_t generation)
{
    _generation = generation;
}

int64_t
FileSource::getLast(const vespalib::string & fileName)
{
    struct stat filestat;
    memset(&filestat, 0, sizeof(filestat));
    stat(fileName.c_str(), &filestat);
    return filestat.st_mtime;
}

StringVector
FileSource::readConfigFile(const vespalib::string & fileName)
{
    asciistream is(asciistream::createFromFile(fileName));
    return getlines(is);
}

void
FileSource::close()
{
}

} // namespace config
