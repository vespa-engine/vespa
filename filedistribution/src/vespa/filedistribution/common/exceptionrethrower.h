// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <boost/thread/mutex.hpp>
#include <boost/thread/locks.hpp>
#include <boost/exception_ptr.hpp>
#include <boost/type_traits/is_polymorphic.hpp>

namespace filedistribution {

//used for rethrowing an exceptions in a different context
class ExceptionRethrower {
    boost::exception_ptr _exceptionPtr; //not a pod, default constructed to null value

    mutable boost::mutex _exceptionMutex;
    typedef boost::lock_guard<boost::mutex> LockGuard;

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


