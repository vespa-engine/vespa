package ignore

import (
	"strings"
	"testing"
)

func TestRead(t *testing.T) {
	f := `
# files

foo
foob*
???.tmp

# directories

  foo/bar  
foo/*/baz
bar/
`
	list, err := Read(strings.NewReader(f))
	if err != nil {
		t.Fatal(err)
	}
	assertMatch(t, list, "", false)
	assertMatch(t, list, "\n", false)
	assertMatch(t, list, "foo1", false)
	assertMatch(t, list, "foo", true)
	assertMatch(t, list, "foobar", true)
	assertMatch(t, list, "foo/bar", true)
	assertMatch(t, list, "foo/bar/baz", true)
	assertMatch(t, list, "foo/bar/bax", false)
	assertMatch(t, list, "bar", false)
	assertMatch(t, list, "bar/", true)
	assertMatch(t, list, "bar/x", true)
	assertMatch(t, list, "foo.tmp", true)
	assertMatch(t, list, "fooo.tmp", false)

	_, err = Read(strings.NewReader("myfile["))
	if err == nil {
		t.Fatal("want error")
	}
}

func assertMatch(t *testing.T, list *List, name string, match bool) {
	if got := list.Match(name); got != match {
		t.Errorf("Match(%q) = %t, want %t", name, got, match)
	}
}
