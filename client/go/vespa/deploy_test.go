package vespa

import (
	"io/ioutil"
	"os"
	"path/filepath"
	"strings"
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

func TestFindApplicationPackage(t *testing.T) {
	dir := t.TempDir()
	assertFindApplicationPackage(t, dir, pkgFixture{
		expectedPath: dir,
		existingFile: filepath.Join(dir, "services.xml"),
	})
	assertFindApplicationPackage(t, dir, pkgFixture{
		expectedPath: filepath.Join(dir, "src", "main", "application"),
		existingFile: filepath.Join(dir, "src", "main", "application") + string(os.PathSeparator),
	})
	assertFindApplicationPackage(t, dir, pkgFixture{
		expectedPath: filepath.Join(dir, "src", "main", "application"),
		existingFile: filepath.Join(dir, "pom.xml"),
	})
	assertFindApplicationPackage(t, dir, pkgFixture{
		existingFile:     filepath.Join(dir, "pom.xml"),
		requirePackaging: true,
		fail:             true,
	})
	assertFindApplicationPackage(t, dir, pkgFixture{
		expectedPath:     filepath.Join(dir, "target", "application.zip"),
		existingFiles:    []string{filepath.Join(dir, "pom.xml"), filepath.Join(dir, "target", "application.zip")},
		requirePackaging: true,
	})
}

type pkgFixture struct {
	expectedPath     string
	existingFile     string
	existingFiles    []string
	requirePackaging bool
	fail             bool
}

func assertFindApplicationPackage(t *testing.T, zipOrDir string, fixture pkgFixture) {
	if fixture.existingFile != "" {
		writeFile(t, fixture.existingFile)
	}
	for _, f := range fixture.existingFiles {
		writeFile(t, f)
	}
	pkg, err := FindApplicationPackage(zipOrDir, fixture.requirePackaging)
	assert.Equal(t, err != nil, fixture.fail, "Expected error for "+zipOrDir)
	assert.Equal(t, fixture.expectedPath, pkg.Path)
}

func writeFile(t *testing.T, name string) {
	err := os.MkdirAll(filepath.Dir(name), 0755)
	assert.Nil(t, err)
	if !strings.HasSuffix(name, string(os.PathSeparator)) {
		err = ioutil.WriteFile(name, []byte{0}, 0644)
		assert.Nil(t, err)
	}
}
