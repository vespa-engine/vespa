// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 *
 * @file url.h
 * @author Michael Susæg
 * @date Creation date:
 *
 * This file is the header file for the url class.
 *
 * Copyright (c)        : 1997-2000 Fast Search & Transfer ASA.
 *                        ALL RIGHTS RESERVED
 *
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
