// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * Author: Michael Susæg
 */

#pragma once



class Fast_URL
{
public:
    void decode(const char *encodedURL, char *unencodedURL, int bufsize);
    /* bufsize is the length of the unencodedURL buffer */

    /* Both methods return the number of chars replaced */
    int DecodeQueryString(char *queryString);
};
