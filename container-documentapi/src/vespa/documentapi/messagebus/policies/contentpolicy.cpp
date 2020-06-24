// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "contentpolicy.h"

namespace documentapi {

ContentPolicy::ContentPolicy(const string& param)
        : StoragePolicy(param)
{ }

string
ContentPolicy::createConfigId(const string & clusterName) const
{
    return clusterName;
}

} // documentapi
