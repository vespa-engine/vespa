// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#define ENUM_ATTRIBUTE(B) EnumAttribute<B>

#define MULTIVALUE_ARG(T) T
#define MULTIVALUE_ENUM_ARG vespalib::datastore::AtomicEntryRef
#define WEIGHTED_MULTIVALUE_ARG(T) multivalue::WeightedValue<T>
#define WEIGHTED_MULTIVALUE_ENUM_ARG multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>

