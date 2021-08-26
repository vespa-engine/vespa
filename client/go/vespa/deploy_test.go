package vespa

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestFindApplicationPackage(t *testing.T) {
	dir := t.TempDir()
	var tests = []struct {
		in   string
		out  string
		fail bool
	}{
		{filepath.Join(dir, "foo"), "", true},
		{filepath.Join(dir, "services.xml"), dir, false},
		{filepath.Join(dir, "src", "main", "application", "services.xml"), filepath.Join(dir, "src", "main", "application"), false},
	}

	zipFile := filepath.Join(dir, "application.zip")
	writeFile(t, zipFile)
	pkg, err := FindApplicationPackage(zipFile)
	assert.Nil(t, err)
	assert.Equal(t, zipFile, pkg.Path)

	for i, tt := range tests {
		writeFile(t, tt.in)
		pkg, err := FindApplicationPackage(dir)
		if tt.fail {
			assert.NotNil(t, err)
		} else if pkg.Path != tt.out {
			t.Errorf("#%d: FindApplicationPackage(%q) = (%q, %s), want (%q, nil)", i, dir, pkg.Path, err, tt.out)
		}
	}
}

func writeFile(t *testing.T, filename string) {
	err := os.MkdirAll(filepath.Dir(filename), 0755)
	assert.Nil(t, err)
	err = os.WriteFile(filename, []byte{0}, 0644)
	assert.Nil(t, err)
}
