// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
*****************************************************************************
* @author  Aleksander �hrn
* @date    Creation date: 2001-10-16
* @version $Id$
* @file
* Utility functions for base-64 encoding/decoding.
*****************************************************************************/

#pragma once

/**
*****************************************************************************
* Utility functions for base-64 encoding/decoding.
*
* @class   Fast_Base64
* @author  Aleksander �hrn
* @date    Creation date : 2001-10-16
* @version $Id$
*****************************************************************************/

class Fast_Base64 {
public:

    /**
     *****************************************************************************
     * Encodes source in base64 into destination.
     * Returns the number of data bytes stored in destination, or -1 on error.
     *
     * @param  source is the source buffer
     * @param  length is the length of the source string
     * @param  destination is the destination buffer
     * @return Length of the destination string, INCLUDING the trailing '\0' byte.
     *         Returns -1 on error.
     * @author Aleksander �hrn
     ***************************************************************************/
    static int Encode(const char *source,
                      unsigned int length,
                      char *destination);

    /**
     ***************************************************************************
     * Decodes the base64 encoded source into destination.
     * Returns the number of data bytes stored in destination, or -1 on error.
     * Note that there is no trailing '\0' byte automatically padded to the
     * destination buffer. So the length returned is the same as was originally
     * used for length the Encode() call.
     *
     * @param  source is the source buffer
     * @param  length is the length of the source string,
     *         NOT including the terminating '\0' byte
     * @param  destination is the destination buffer, identical to the buffer
     *         that originally went into Encode().
     * @return Length of the destination string.
     *         Returns -1 on error.
     * @author Aleksander �hrn
     **************************************************************************/
    static int Decode(const char *source,
                      unsigned int length,
                      char *destination);

};
