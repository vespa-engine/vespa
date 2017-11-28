// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace vespalib::metrics {

/**
 * This is the API you need to implement in order to be used as a TimeSupplier.
 **/
struct TimeSupplier {
    /**
     * provide a type that can be used for time stamping, like a time_point:
     **/
    typedef int TimeStamp;

    /**
     * provide a method that can be called to get a time stamp for "now":
     **/
    TimeStamp now_stamp() const;

    /**
     * provide a method that can convert the time stamp (obtained from
     * above method) into seconds since 1970, as a double:
     **/
    double stamp_to_s(TimeStamp) const;

    /**
     * constructor will usually be trivial.
     **/
    TimeSupplier();
};

} // namespace vespalib::metrics
