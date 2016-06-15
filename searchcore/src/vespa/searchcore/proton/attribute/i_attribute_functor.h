// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search { class AttributeVector; }

namespace proton {

/*
 * Interface class for access attribute in correct attribute write
 * thread as async callback from asyncForEachAttribute() call on
 * attribute manager.
 */
class IAttributeFunctor
{
public:
    virtual void operator()(const search::AttributeVector &attributeVector) = 0;
    virtual ~IAttributeFunctor() { }
};

} // namespace proton

