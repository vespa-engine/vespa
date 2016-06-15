// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <jni.h>

#include <vespa/filedistribution/common/exception.h>

namespace filedistribution {

struct BadFieldException : std::runtime_error {
    explicit BadFieldException(const std::string& name)
        : runtime_error("Could not lookup field '" + name + "'")
    {}

    BadFieldException(const BadFieldException& e)
        : runtime_error(e)
    {}
};

template <class T>
class LongField {
    jfieldID _fieldID;
public:
    LongField()
    {}

    LongField(jclass clazz, const char* fieldName, JNIEnv* env)
        :_fieldID(env->GetFieldID(clazz, fieldName, "J")) {

        if (!_fieldID)
            BOOST_THROW_EXCEPTION(BadFieldException(fieldName));
    }

    void set(jobject obj, T value, JNIEnv* env) {
        jlong longValue = reinterpret_cast<long>(value);
        env->SetLongField(obj, _fieldID, longValue);
    }

    T get(jobject obj, JNIEnv* env) {
        jlong longValue = env->GetLongField(obj, _fieldID);
        return reinterpret_cast<T>(longValue);
    }
};

} //namespace filedistribution

