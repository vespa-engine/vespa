// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/common/sourcefactory.h>
#include <vespa/config/common/types.h>

namespace config {

class DirSpec;
class FileSpec;

/**
 * Factory creating config payload from config instances.
 */
class FileSourceFactory : public SourceFactory
{
public:
    FileSourceFactory(const FileSpec & fileSpec);

    /**
     * Create source handling config described by key.
     */
    std::unique_ptr<Source> createSource(std::shared_ptr<IConfigHolder> holder, const ConfigKey & key) const override;
private:
    vespalib::string _fileName;
};

/**
 * Factory creating config payload from config instances.
 */
class DirSourceFactory : public SourceFactory
{
public:
    DirSourceFactory(const DirSpec & dirSpec);

    /**
     * Create source handling config described by key.
     */
    std::unique_ptr<Source> createSource(std::shared_ptr<IConfigHolder> holder, const ConfigKey & key) const override;
private:
    vespalib::string _dirName;
    StringVector _fileNames;
};


} // namespace config

