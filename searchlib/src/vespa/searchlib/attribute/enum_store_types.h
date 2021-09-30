// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/vespalib/datastore/entryref.h>
#include <vespa/vespalib/util/array.h>

namespace search::enumstore {

using Index = vespalib::datastore::EntryRef;
using InternalIndex = vespalib::datastore::EntryRefT<22>;
using IndexVector = vespalib::Array<Index>;
using EnumHandle = attribute::IAttributeVector::EnumHandle;
using EnumVector = vespalib::Array<uint32_t>;

}
