// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

// These are used by FAST Web Search for host name anchoring.

// NB! Should be changed to uppercase once the functionality is implemented!!

static const char *ANCHOR_START_OF_HOST = "StArThOsT";
static const char *ANCHOR_END_OF_HOST = "EnDhOsT";

// These are used in the query parser when parsing fields with parsemode
// 'boundaries'. Not used otherwise. Lowercased for performance reasons.

#define ANCHOR_LEFT_BOUNDARY "fastpbfast"
#define ANCHOR_RIGHT_BOUNDARY "fastpbfast"

