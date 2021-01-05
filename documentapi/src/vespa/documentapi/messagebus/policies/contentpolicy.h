// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "storagepolicy.h"

namespace documentapi {

class ContentPolicy : public StoragePolicy
{
public:
    ContentPolicy(const string& param);
private:
    string createConfigId(const string & clusterName) const override;
};

}

