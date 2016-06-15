// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#define ENUM_ATTRIBUTE(B) EnumAttribute<B>

#define MULTIVALUE_ARG(T, I) multivalue::MVMTemplateArg<multivalue::Value<T>, I>
#define MULTIVALUE_ENUM_ARG(I) multivalue::MVMTemplateArg<multivalue::Value<EnumStoreBase::Index>, I>
#define WEIGHTED_MULTIVALUE_ARG(T, I) multivalue::MVMTemplateArg<multivalue::WeightedValue<T>, I>
#define WEIGHTED_MULTIVALUE_ENUM_ARG(I) multivalue::MVMTemplateArg<multivalue::WeightedValue<EnumStoreBase::Index>, I>

