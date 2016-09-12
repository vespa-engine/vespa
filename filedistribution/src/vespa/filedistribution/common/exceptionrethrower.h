// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <mutex>
#include <boost/exception_ptr.hpp>
#include <boost/type_traits/is_polymorphic.hpp>

namespace filedistribution {

//used for rethrowing an exceptions in a different context
class ExceptionRethrower {
    boost::exception_ptr _exceptionPtr; //not a pod, default constructed to null value

    mutable std::mutex _exceptionMutex;
    typedef std::lock_guard<std::mutex> LockGuard;

public:
    void rethrow() const {
        LockGuard guard(_exceptionMutex);

        if (_exceptionPtr)
            boost::rethrow_exception(_exceptionPtr);
    }

    bool exceptionStored() const {
        LockGuard guard(_exceptionMutex);
        return _exceptionPtr;
    }

    template <class T>
    void store(const T& exception) {
        boost::exception_ptr exceptionPtr = boost::copy_exception(exception);
        store(exceptionPtr);
    }

    void store(const boost::exception_ptr exceptionPtr) {
        LockGuard guard(_exceptionMutex);

        if (!_exceptionPtr) //only store the first exception to be rethrowed.
            _exceptionPtr = exceptionPtr;
    }
};

} //namespace filedistribution


