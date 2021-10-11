package vespa

import "fmt"

// WeakAnd represents the Vespa's WeakAnd function implementation
type WeakAnd struct {
	Hits    int
	Entries []WeakAndEntry
}

// WeakAndEntry represents a single entry for the WeakAnd function
type WeakAndEntry struct {
	Field string
	Value string
}

// NewWeakAnd creates a new *WeakAnd
func NewWeakAnd(hits int) *WeakAnd {
	return &WeakAnd{
		Hits: hits,
	}
}

// AddEntry adds an entry to the WeakAnd
func (wa *WeakAnd) AddEntry(field, value string) {
	wa.Entries = append(wa.Entries, WeakAndEntry{
		Field: field,
		Value: value,
	})
}

// Source outputs the YQL representation of the WeakAnd
func (wa *WeakAnd) Source() string {
	query := fmt.Sprintf(`[{"targetHits":%d}]`, wa.Hits)

	var entries string

	for _, v := range wa.Entries {
		if entries == "" {
			entries = fmt.Sprintf("%s contains %q", v.Field, v.Value)
			continue
		}
		entries = fmt.Sprintf("%s, %s contains %q", entries, v.Field, v.Value)
	}

	query = fmt.Sprintf("(%s weakAnd(%s))", query, entries)

	return query
}
