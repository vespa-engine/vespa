// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/common/distributorcomponent.h>

namespace storage {

DistributorComponent::DistributorComponent(DistributorComponentRegister& compReg,
                                           vespalib::stringref name)
    : StorageComponent(compReg, name),
      _timeCalculator(0),
      _totalConfig(*this)
{
    compReg.registerDistributorComponent(*this);
}

DistributorComponent::~DistributorComponent() { }

} // storage

