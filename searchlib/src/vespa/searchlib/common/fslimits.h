// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

// define min/max number of bits that may be used to
// encode partid/rowid into the partition path field.
// NB: MIN_ROWBITS == 0
// Constraint: MIN_PARTBITS >= 1
// Constraint: MIN_PARTBITS <= 6 <= MAX_PARTBITS

#define MIN_PARTBITS 1
#define MAX_PARTBITS 8

#define MAX_ROWBITS 8

// Currently, max word length and max number of indexes are limited by
// the layout of binary dictionaries; see class FastS_Pagedict.

#define MAX_WORD_LEN 1000
#define MAX_INDEXES    64

// max number of tiers in a multi-tier dataset.
// may currently not be greater than 16, due to the
// partition path encoding algorithm used.

#define MAX_TIERS 16

// max number of explicitly defined term rank limits
#define MAX_TERMRANKLIMITS 32

// Max number of fallthrough classes in Multi-tier fallthrough selector, just set a limit..
#define MAX_FALLTHROUGH_SELECTORS 32

#define SEARCHLIB_FEF_UNKNOWN_FIELD_LENGTH 1000000u

