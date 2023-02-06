package logfmt

import (
	"bufio"
	"os"
	"testing"
)

// tests: func isInternal(componentName string) bool

func TestIsInternal(t *testing.T) {
	f, err := os.Open("internal_names.txt")
	if err != nil {
		t.Fatal("could not read test data")
	}
	defer f.Close()
	for input := bufio.NewScanner(f); input.Scan(); {
		if name := input.Text(); !isInternal(name) {
			t.Logf("name '%s' should be internal but was not recognized", name)
			t.Fail()
		}
	}
	f, err = os.Open("internal_notnames.txt")
	if err != nil {
		t.Fatal("could not read test data")
	}
	defer f.Close()
	for input := bufio.NewScanner(f); input.Scan(); {
		if name := input.Text(); isInternal(name) {
			t.Logf("name '%s' should not be internal but was recognized", name)
			t.Fail()
		}
	}
}
