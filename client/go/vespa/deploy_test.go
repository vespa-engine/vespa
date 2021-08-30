package vespa

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestApplicationFromString(t *testing.T) {
	app, err := ApplicationFromString("t1.a1.i1")
	assert.Nil(t, err)
	assert.Equal(t, ApplicationID{Tenant: "t1", Application: "a1", Instance: "i1"}, app)
	_, err = ApplicationFromString("foo")
	assert.NotNil(t, err)
}

func TestZoneFromString(t *testing.T) {
	zone, err := ZoneFromString("dev.us-north-1")
	assert.Nil(t, err)
	assert.Equal(t, ZoneID{Environment: "dev", Region: "us-north-1"}, zone)
	_, err = ZoneFromString("foo")
	assert.NotNil(t, err)
}

func TestApplicationPackageFrom(t *testing.T) {
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
	pkg, err := ApplicationPackageFrom(zipFile)
	assert.Nil(t, err)
	assert.Equal(t, zipFile, pkg.Path)

	for i, tt := range tests {
		writeFile(t, tt.in)
		pkg, err := ApplicationPackageFrom(dir)
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
