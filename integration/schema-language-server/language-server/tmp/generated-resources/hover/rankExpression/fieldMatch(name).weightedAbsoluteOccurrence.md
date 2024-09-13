Returns a normalized measure of the number of occurrence of the terms of the query, taking weights into account so that occurrences of higher weighted query terms has more impact than lower weighted terms.

This is 1 if there are many occurrences of the highly weighted terms, and 0 if there are none.

This number is not relative to the field length, so it is suitable for uses of occurrence to denote relative importance between matched terms (i.e. fields containing keywords, not normal text).

Default: 0