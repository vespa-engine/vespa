// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributevector.h"

namespace search {

/**
 * Factory for creating attribute vector instances.
 **/
class AttributeFactory {
private:
    typedef attribute::Config Config;
    static AttributeVector::SP createArrayStd(const vespalib::string & baseFileName, const Config & cfg);
    static AttributeVector::SP createArrayFastSearch(const vespalib::string & baseFileName, const Config & cfg);
    static AttributeVector::SP createSetStd(const vespalib::string & baseFileName, const Config & cfg);
    static AttributeVector::SP createSetFastSearch(const vespalib::string & baseFileName, const Config & cfg);
    static AttributeVector::SP createSingleStd(const vespalib::string & baseFileName, const Config & cfg);
    static AttributeVector::SP createSingleFastSearch(const vespalib::string & baseFileName, const Config & cfg);
    static AttributeVector::SP createSingleFastAggregate(const vespalib::string & baseFileName, const Config & cfg);
    static AttributeVector::SP createArrayFastAggregate(const vespalib::string & baseFileName, const Config & cfg);
    static AttributeVector::SP createSetFastAggregate(const vespalib::string & baseFileName, const Config & cfg);

public:
    /**
     * Create an attribute vector with the given name based on the given config.
     **/
    static AttributeVector::SP createAttribute(const vespalib::string & baseFileName, const Config & cfg);
};

}

