// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <exception>
#include <string>
#include <vector>
#include <jni.h>

namespace filedistribution {

class JNIString {
    struct Repr {
        JNIEnv* _env;
        jbyteArray _str;
        jint _size;
        char* _repr;

        Repr(jbyteArray str, JNIEnv* env)
            :_env(env),
             _str(str),
             _size(_env->GetArrayLength(str)),
             _repr(reinterpret_cast<char*>(_env->GetPrimitiveArrayCritical(str, 0))) {

            if (!_repr)
                throw std::bad_alloc();
        }

        ~Repr() {
            _env->ReleasePrimitiveArrayCritical(_str, _repr, 0);
            _env->DeleteLocalRef(_str);
        }
    };
public:
    typedef jbyteArray JavaValue;
    typedef std::string Value;
    Value _value;

    JNIString(jbyteArray str, JNIEnv* env) {
        Repr repr(str, env);
        Value result(repr._repr, repr._repr + repr._size);
        _value.swap(result);
    }
};

class JNIUtf8String {
public:
    typedef jstring JavaValue;
    typedef std::string Value;
    std::string _value;

    JNIUtf8String(jstring str, JNIEnv* env)
        :_value(env->GetStringUTFLength(str), 0)
    {
        env->GetStringUTFRegion(str, 0, _value.begin() - _value.end(), &*_value.begin());
        env->DeleteLocalRef(str);
    }
};

template <class JNITYPE>
class JNIArray {
public:
    std::vector<typename JNITYPE::Value> _value;

    JNIArray(jobjectArray array, JNIEnv* env) {
        jsize length = env->GetArrayLength(array);
        _value.reserve(length);

        for (jsize i=0; i<length; ++i) {
            jobject element = env->GetObjectArrayElement(array,  i);
            JNITYPE elementValue(static_cast<typename JNITYPE::JavaValue>(element), env);
            _value.push_back(elementValue._value);
        }
        env->DeleteLocalRef(array);
    }
};

} //namespace filedistribution

