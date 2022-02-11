// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "filesourcefactory.h"
#include "filesource.h"
#include <vespa/config/subscription/sourcespec.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/log/log.h>
LOG_SETUP(".config.filesourcefactory");

namespace config {

DirSourceFactory::DirSourceFactory(const DirSpec & dirSpec)
    : _dirName(dirSpec.getDirName()),
      _fileNames()
{
    vespalib::DirectoryList files(vespalib::listDirectory(_dirName));
    for (size_t i = 0; i < files.size(); i++) {
        const vespalib::DirectoryList::value_type & fname(files[i]);
        if (fname.length() > 4 && fname.substr(fname.length() - 4) == ".cfg") {
            _fileNames.push_back(fname);
        }
    }
}

std::unique_ptr<Source>
DirSourceFactory::createSource(std::shared_ptr<IConfigHolder> holder, const ConfigKey & key) const
{
    vespalib::string fileId(key.getDefName());
    if (!key.getConfigId().empty()) {
        fileId += "." + key.getConfigId();
    }
    fileId += ".cfg";

    bool found(false);
    for (const auto & fileName : _fileNames) {
        if (fileName.compare(fileId) == 0) {
            found = true;
            break;
        } 
    }
    if ( !found ) {
        LOG(warning, "Filename '%s' was expected in the spec, but does not exist.", fileId.c_str());
    }
    vespalib::string fName = _dirName;
    if (!fName.empty()) fName += "/";
    fName += fileId;
    return std::make_unique<FileSource>(std::move(holder), fName);
}

FileSourceFactory::FileSourceFactory(const FileSpec & fileSpec)
    : _fileName(fileSpec.getFileName())
{
}

std::unique_ptr<Source>
FileSourceFactory::createSource(std::shared_ptr<IConfigHolder> holder, const ConfigKey & key) const
{
    (void) key;
    return std::make_unique<FileSource>(std::move(holder), _fileName);
}

} // namespace config

