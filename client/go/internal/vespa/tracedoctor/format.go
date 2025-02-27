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
