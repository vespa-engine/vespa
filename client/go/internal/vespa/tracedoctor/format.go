// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

func word(n int, singular, plural string) string {
	if n == 1 {
		return singular
	}
	return plural
}

func suffix(n int, s string) string {
	return word(n, "", s)
}
