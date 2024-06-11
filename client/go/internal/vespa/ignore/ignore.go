package ignore

import (
	"bufio"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
)

// List is a list of ignore patterns.
type List struct{ patterns []string }

// Match returns whether path matches any pattern in this list.
func (l *List) Match(path string) bool {
	for _, pattern := range l.patterns {
		if ok, _ := filepath.Match(pattern, path); ok {
			return true
		}
		// A directory exclude applies to all subpaths
		if strings.HasSuffix(pattern, string(filepath.Separator)) && strings.HasPrefix(path, pattern) {
			return true
		}
	}
	return false
}

// Read reads an ignore list from reader r.
func Read(r io.Reader) (*List, error) {
	scanner := bufio.NewScanner(r)
	ignore := List{}
	line := 0
	for scanner.Scan() {
		line++
		pattern := strings.TrimSpace(scanner.Text())
		if pattern == "" || strings.HasPrefix(pattern, "#") {
			continue
		}
		if _, err := filepath.Match(pattern, ""); err != nil {
			return nil, fmt.Errorf("line %d: bad pattern: %s: %w", line, pattern, err)
		}
		ignore.patterns = append(ignore.patterns, pattern)
	}
	if err := scanner.Err(); err != nil {
		return nil, err
	}
	return &ignore, nil
}

// ReadFile reads an ignore list from the named file. Reading a non-existent file returns an empty list, and no error.
func ReadFile(name string) (*List, error) {
	f, err := os.Open(name)
	if err != nil {
		if os.IsNotExist(err) {
			return &List{}, nil
		}
		return nil, err
	}
	defer f.Close()
	return Read(f)
}
