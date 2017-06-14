// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/exceptions.h>
#include <boost/filesystem/path.hpp>

namespace filedistribution {

using Path = boost::filesystem::path;

VESPA_DEFINE_EXCEPTION(FileDoesNotExistException, vespalib::Exception);

}
