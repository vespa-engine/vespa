// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#define ENUM_ATTRIBUTE(B) EnumAttribute<B>

#define MULTIVALUE_ARG(T) multivalue::Value<T>
#define MULTIVALUE_ENUM_ARG multivalue::Value<EnumStoreBase::Index>
#define WEIGHTED_MULTIVALUE_ARG(T) multivalue::WeightedValue<T>
#define WEIGHTED_MULTIVALUE_ENUM_ARG multivalue::WeightedValue<EnumStoreBase::Index>

