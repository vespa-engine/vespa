// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//template functions that must be included in the
//header file, since the full definition needs to be
//available to users.
//Can be moved when(if ever) the export functionality
//is implemented in the target compiler.

namespace frtstream {

template<class U>
struct FrtStreamConverter{
    typedef U type;
    typedef U serializationType;

    static const serializationType& convertTo(const U& u) {
        return u;
    }
    static const U& convertFrom(const serializationType& s) {
        return s;
    }

};

//signed conversion
#define _FRTSTREAM_CONVERTUNSIGNED_(signedType) \
template <> \
struct FrtStreamConverter< signedType > { \
    typedef u##signedType serializationType; \
\
    static serializationType convertTo(signedType u) { \
        return static_cast< serializationType >(u); \
    } \
    static signedType convertFrom(serializationType s) { \
        return static_cast< signedType >(s); \
    } \
};

_FRTSTREAM_CONVERTUNSIGNED_(int8_t);
_FRTSTREAM_CONVERTUNSIGNED_(int16_t);
_FRTSTREAM_CONVERTUNSIGNED_(int32_t);
_FRTSTREAM_CONVERTUNSIGNED_(int64_t);

#undef  _FRTSTREAM_CONVERTUNSIGNED_
//end signed conversion


template<class T>
struct FrtArray {
    T* _pt;
    uint32_t _len;
};


//ArrayTypeUtil

//Little ugly hack to avoid code duplication
//Needed since template based approach failed
//to work because of anonymous type.
#define _FRTSTREAM_FILLARRAY(arr, prefix) \
arr._pt = prefix._pt; \
arr._len = prefix._len;


void PleaseAddSpecializationForYourType();

template <class SerializationType>
struct ArrayTypeUtil {
    template <class Converter, class Cont>
    static void addArray(FRT_Values& value, const Cont& c) {
        frtstream::PleaseAddSpecializationForYourType();
    }

    static void getArray(FRT_Value& value) {
         frtstream::PleaseAddSpecializationForYourType();
    }
};

template <>
struct ArrayTypeUtil<std::string> {
    template<class Converter>
    struct AddString{
        FRT_StringValue* ptr;
        FRT_Values& values;

        AddString(FRT_StringValue* start, FRT_Values& val) :
            ptr(start), values(val) {}
        void operator()(const typename Converter::type& s) {
            values.SetString(ptr++, Converter::convertTo(s).c_str());
        }
    };


    template <class Converter, class Cont>
    static void addArray(FRT_Values& value, const Cont& c) {
        FRT_StringValue* start = value.AddStringArray(c.size());
        std::for_each(c.begin(), c.end(), AddString<Converter>(start, value));

    }
    static FrtArray<FRT_StringValue> getArray(FRT_Value& value) {
        FrtArray<FRT_StringValue> arr;
        _FRTSTREAM_FILLARRAY(arr, value._string_array);
        return arr;
    }

};

#define _FRTSTREAM_ARRAYTYPE_UTIL_FLOAT(floatType, floatTypeCapitalized) \
template <> \
struct ArrayTypeUtil<floatType> { \
    template <class Converter, class Cont> \
    static void addArray(FRT_Values& values, const Cont & cont) { \
        floatType* startPtr = values.Add##floatTypeCapitalized##Array(cont.size()); \
        std::transform(cont.begin(), cont.end(), startPtr, Converter::convertTo); \
    } \
    static FrtArray<floatType> getArray(FRT_Value& value) { \
        FrtArray<floatType> arr; \
        _FRTSTREAM_FILLARRAY(arr, value._##floatType##_array); \
        return arr; \
    } \
};

_FRTSTREAM_ARRAYTYPE_UTIL_FLOAT(float, Float);
_FRTSTREAM_ARRAYTYPE_UTIL_FLOAT(double, Double);
#undef _FRTSTREAM_ARRAYTYPE_UTIL_FLOAT

#define _FRTSTREAM_ARRAYTYPE_UTIL_INT(bits) \
template <> \
struct ArrayTypeUtil<uint##bits##_t> { \
    template <class Converter, class Cont> \
    static void addArray(FRT_Values& values, const Cont & cont) { \
        uint##bits##_t* startPtr = values.AddInt##bits##Array(cont.size()); \
        std::transform(cont.begin(), cont.end(), startPtr, \
                       Converter::convertTo); \
    } \
\
    static FrtArray<uint##bits##_t>  getArray(FRT_Value& value) { \
        FrtArray<uint##bits##_t> arr; \
        _FRTSTREAM_FILLARRAY(arr, value._int##bits##_array); \
        return arr; \
    } \
};

_FRTSTREAM_ARRAYTYPE_UTIL_INT(8);
_FRTSTREAM_ARRAYTYPE_UTIL_INT(16);
_FRTSTREAM_ARRAYTYPE_UTIL_INT(32);
_FRTSTREAM_ARRAYTYPE_UTIL_INT(64);
#undef _FRTSTREAM_ARRAYTYPE_UTIL_INT

#undef _FRTSTREAM_FILLARRAY
//End ArrayTypeUtil

//ArrayReader
template <class T>
struct ArrayReader {
    template<class Converter, class Iter, class ArrayImp>
    static void read(Iter dest, ArrayImp arr) {
        std::transform( arr._pt, arr._pt + arr._len, dest,
                       Converter::convertFrom );

    }
};

template <>
struct ArrayReader<std::string> {
    template<class Converter, class Iter, class ArrayImp>
    static void read(Iter dest, ArrayImp arr) {
        FRT_StringValue* ptr = arr._pt;
        for(uint32_t i = 0; i < arr._len;  i++ ) {
            *dest++ = Converter::convertFrom(ptr++ ->_str);
        }
    }
};



//End ArrayReader


template <template<typename, typename> class CONT, class T, class ALLOC>
FrtStream& FrtStream::operator<<( const CONT<T, ALLOC> & cont ) {
    typedef FrtStreamConverter<typename CONT<T, ALLOC>::value_type> Converter;
    typedef typename FrtStreamConverter<typename CONT<T, ALLOC>::value_type>::serializationType SerializationType;

    frtstream::ArrayTypeUtil<SerializationType>::template addArray<Converter>(in(), cont);

    return *this;
}



template <template<typename, typename> class CONT, class T, class ALLOC>
FrtStream& FrtStream::operator>>( CONT<T, ALLOC> & cont ) {
    typedef FrtStreamConverter<typename CONT<T, ALLOC>::value_type> Converter;
    typedef typename FrtStreamConverter<typename CONT<T, ALLOC>::value_type>::serializationType SerializationType;

    ArrayReader<SerializationType>::template read<Converter>( std::inserter(cont, cont.end()),
                         ArrayTypeUtil<SerializationType>::getArray(nextOut()) );

    return *this;
}

} //end namespace frtstream
