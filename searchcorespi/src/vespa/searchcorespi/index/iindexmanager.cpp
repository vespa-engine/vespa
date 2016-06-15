// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchcorespi/index/iindexmanager.h>

namespace searchcorespi {

void
IIndexManager::wipeHistory(SerialNum wipeSerial, const Schema &historyFields)
{
    (void) wipeSerial;
    (void) historyFields;
}

IIndexManager::Reconfigurer::~Reconfigurer()
{
}

} // namespace searchcorespi
