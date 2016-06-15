// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2001-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once


/**
 * Simple class that may be used to base 64 encode a continuous data buffer.
 **/
class FastS_Base64Encoder
{
private:
    FastS_Base64Encoder(const FastS_Base64Encoder &);
    FastS_Base64Encoder& operator=(const FastS_Base64Encoder &);

    /**
     * The char used for padding in base 64 encoding.
     **/
    static char _base64Padding;

    /**
     * Table containing the 64 chars used to represent numbers from 0-63
     * in base 64 encoding.
     **/
    static char _base64Table[];


    const unsigned char  *_data;
    const unsigned char  *_dataPos;
    const unsigned char  *_dataEnd;

public:


    /**
     * Create a base 64 encoder object with the task of encoding the
     * given buffer.
     *
     * @param data the data to encode.
     * @param datalen the byte-count of the data to encode.
     **/
    FastS_Base64Encoder(const void *data, unsigned int datalen)
        : _data(static_cast<const unsigned char *>(data)),
          _dataPos(_data),
          _dataEnd(_data + datalen)
    {
    }


    /**
     * @return number of bytes left in the input buffer.
     **/
    unsigned int InputBytesLeft()
    {
        return (_dataEnd - _dataPos);
    }


    /**
     * This method determines how much output space is needed to encode
     * the rest of the input buffer referenced by this object.
     *
     * @return the space needed to encode the rest of the input.
     **/
    unsigned int OutputBytesNeeded()
    {
        unsigned int groups = (_dataEnd - _dataPos) / 3;
        if (((_dataEnd - _dataPos) % 3) != 0)
            groups++;

        return (groups << 2);
    }


    /**
     * Encode data from the buffer referenced by this object into the
     * buffer given to this method. NOTE: dstLen should be at least 4
     * since this method only encodes in complete groups.
     *
     * @return the number of bytes of output generated.
     * @param dst where to generate output.
     * @param dstLen maximum output to generate.
     **/
    unsigned int Encode(char *dst, unsigned int dstLen)
    {
        unsigned int groups = dstLen >> 2;

        char *dstPos = dst;
        for (;groups > 0 && InputBytesLeft() >= 3; groups--) {
            dstPos[0] = _base64Table[_dataPos[0] >> 2];
            dstPos[1] = _base64Table[((_dataPos[0] & 0x03) << 4) + (_dataPos[1] >> 4)];
            dstPos[2] = _base64Table[((_dataPos[1] & 0x0f) << 2) + (_dataPos[2] >> 6)];
            dstPos[3] = _base64Table[_dataPos[2] & 0x3f];
            dstPos   += 4;
            _dataPos += 3;
        }

        if (groups > 0 && InputBytesLeft() > 0) { // handle padding
            dstPos[0] = _base64Table[_dataPos[0] >> 2];

            if (InputBytesLeft() == 2) { // 2 bytes left
                dstPos[1] = _base64Table[((_dataPos[0] & 0x03) << 4) + (_dataPos[1] >> 4)];
                dstPos[2] = _base64Table[(_dataPos[1] & 0x0f) << 2];
                dstPos[3] = _base64Padding;

            } else {                     // 1 byte left
                dstPos[1] = _base64Table[(_dataPos[0] & 0x03) << 4];
                dstPos[2] = _base64Padding;
                dstPos[3] = _base64Padding;
            }
            dstPos     += 4;
            _dataPos    = _dataEnd;
        }

        return (dstPos - dst);
    }

};

