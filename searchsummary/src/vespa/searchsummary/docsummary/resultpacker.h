// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "resultconfig.h"

namespace search::docsummary {
/**
 * An Object of this class may be used to create docsum blobs. A
 * single blob is created by first indicating what result class the
 * blob should conform to. After that, each docsum field is added with
 * an individual method call. The blob may then be extracted by a
 * final method call. Note that objects of this class may be re-used
 * to create multiple blobs each.
 **/
class ResultPacker
{
private:
    ResultPacker(const ResultPacker &);
    ResultPacker& operator=(const ResultPacker &);

    search::RawBuf        _buf;       // packing buffer
    search::RawBuf        _cbuf;      // compression buffer
    const ResultConfig   *_resConfig; // result config
    const ResultClass    *_resClass;  // result class of current blob
    uint32_t              _entryIdx;  // current field index of current blob
    const ResConfigEntry *_cfgEntry;  // current field of current blob
    bool                  _error;     // error flag for current blob

    static const char *GetResTypeName(ResType type)
    { return ResultConfig::GetResTypeName(type); }

    static bool IsBinaryCompatible(ResType a, ResType b)
    { return ResultConfig::IsBinaryCompatible(a, b); }

    void WarnType(ResType type) const;
    void SetFormatError(ResType type);

    bool CheckEntry(ResType type);

public:
    /**
     * Create a result packer based on the given result config. Note
     * that the result config object is NOT handed over; it is the
     * responsibility of the application to ensure that the lifetime of
     * the result config object is longer than the lifetime of the
     * created result packer object.
     *
     * @param resConfig result configuration.
     **/
    explicit ResultPacker(const ResultConfig *resConfig);
    ~ResultPacker();


    /**
     * Start creating new docsum blob without result class.
     * (Bypassing type-checks.)
     **/
    void InitPlain();

    /**
     * Start creating a new docsum blob of the given result class.
     *
     * @return true(ok)/false(error).
     * @param classID the id of the result class we want to create a
     *                docsum blob conforming to.
     **/
    bool Init(uint32_t classID);

    /**
     * Add empty field of appropriate type.
     *
     * @return true(ok)/false(error).
     **/
    bool AddEmpty();

    /**
     * Add a 'byte' field to the docsum blob we are currently
     * creating. Note that this method will fail if the type of the
     * added field is not compatible with the field type sequence
     * defined in the result class config. This method will also fail if
     * an error condition is already detected. The only way to clear the
     * error state is with another call to @ref Init.
     *
     * @return true(ok)/false(error).
     * @param value byte value of field to add.
     **/
    bool AddByte(uint8_t value);

    void AddByteForce(uint8_t value);

    /**
     * Add a 'short' field to the docsum blob we are currently
     * creating. Note that this method will fail if the type of the
     * added field is not compatible with the field type sequence
     * defined in the result class config. This method will also fail if
     * an error condition is already detected. The only way to clear the
     * error state is with another call to @ref Init.
     *
     * @return true(ok)/false(error).
     * @param value short value of field to add.
     **/
    bool AddShort(uint16_t value);

    void AddShortForce(uint16_t value);


    /**
     * Add a 'integer' field to the docsum blob we are currently
     * creating. Note that this method will fail if the type of the
     * added field is not compatible with the field type sequence
     * defined in the result class config. This method will also fail if
     * an error condition is already detected. The only way to clear the
     * error state is with another call to @ref Init.
     *
     * @return true(ok)/false(error).
     * @param value integer value of field to add.
     **/
    bool AddInteger(uint32_t value);

    void AddIntegerForce(uint32_t value);


    /**
     * Add a 'float' field to the docsum blob we are currently
     * creating. Note that this method will fail if the type of the
     * added field is not compatible with the field type sequence
     * defined in the result class config. This method will also fail if
     * an error condition is already detected. The only way to clear the
     * error state is with another call to @ref Init.
     *
     * @return true(ok)/false(error).
     * @param value float value of field to add.
     **/
    bool AddFloat(float value);


    /**
     * Add a 'double' field to the docsum blob we are currently
     * creating. Note that this method will fail if the type of the
     * added field is not compatible with the field type sequence
     * defined in the result class config. This method will also fail if
     * an error condition is already detected. The only way to clear the
     * error state is with another call to @ref Init.
     *
     * @return true(ok)/false(error).
     * @param value double value of field to add.
     **/
    bool AddDouble(double value);


    /**
     * Add a 'int64' field to the docsum blob we are currently
     * creating. Note that this method will fail if the type of the
     * added field is not compatible with the field type sequence
     * defined in the result class config. This method will also fail if
     * an error condition is already detected. The only way to clear the
     * error state is with another call to @ref Init.
     *
     * @return true(ok)/false(error).
     * @param value int64 value of field to add.
     **/
    bool AddInt64(uint64_t value);


    /**
     * Add a 'string' field to the docsum blob we are currently
     * creating. Note that this method will fail if the type of the
     * added field is not compatible with the field type sequence
     * defined in the result class config. This method will also fail if
     * an error condition is already detected. The only way to clear the
     * error state is with another call to @ref Init. The maximum length
     * of this field is 64kB.
     *
     * @return true(ok)/false(error).
     * @param str pointer to string to add.
     * @param slen length of string to add.
     **/
    bool AddString(const char *str, uint32_t slen);

    void AddStringForce(const char *str, uint32_t slen);

    /**
     * Add a 'data' field to the docsum blob we are currently
     * creating. Note that this method will fail if the type of the
     * added field is not compatible with the field type sequence
     * defined in the result class config. This method will also fail if
     * an error condition is already detected. The only way to clear the
     * error state is with another call to @ref Init. The maximum length
     * of this field is 64kB.
     *
     * @return true(ok)/false(error).
     * @param buf pointer to data to add.
     * @param buflen length of data to add.
     **/
    bool AddData(const char *buf, uint32_t buflen);


    /**
     * Add a 'longstring' field to the docsum blob we are currently
     * creating. Note that this method will fail if the type of the
     * added field is not compatible with the field type sequence
     * defined in the result class config. This method will also fail if
     * an error condition is already detected. The only way to clear the
     * error state is with another call to @ref Init. The maximum length
     * of this field is 2GB.
     *
     * @return true(ok)/false(error).
     * @param str pointer to string to add.
     * @param slen length of string to add.
     **/
    bool AddLongString(const char *str, uint32_t slen);


    /**
     * Add a 'longdata' field to the docsum blob we are currently
     * creating. Note that this method will fail if the type of the
     * added field is not compatible with the field type sequence
     * defined in the result class config. This method will also fail if
     * an error condition is already detected. The only way to clear the
     * error state is with another call to @ref Init. The maximum length
     * of this field is 2GB.
     *
     * @return true(ok)/false(error).
     * @param buf pointer to data to add.
     * @param buflen length of data to add.
     **/
    bool AddLongData(const char *buf, uint32_t buflen);

    /*
     * Add a 'tensor' field to the docsum blob we are currently creating.
     *
     * @return true(ok)/false(error).
     * @param buf pointer to serialized tensor to add.
     * @param buflen length of serialized tensor to add.
     **/
    bool AddSerializedTensor(const char *buf, uint32_t buflen);

    /**
     * Obtain a pointer to, and the length of, the created docsum
     * blob. This method will fail if an error was previously detected,
     * or if any docsum fields were missing (too few fields were
     * added). Note that calling the @ref Init method invalidates the
     * obtained docsum blob.
     *
     * @return true(ok)/false(error).
     * @param buf where to store the pointer to the docsum blob.
     * @param buflen where to store the length of the docsum blob.
     **/
    bool GetDocsumBlob(const char **buf, uint32_t *buflen);

};

}
