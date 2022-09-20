package vespa

import (
	"archive/zip"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"github.com/vespa-engine/vespa/client/go/util"
)

type ApplicationPackage struct {
	Path     string
	TestPath string
}

func (ap *ApplicationPackage) HasCertificate() bool {
	return ap.hasFile(filepath.Join("security", "clients.pem"), "security/clients.pem")
}

func (ap *ApplicationPackage) HasDeployment() bool { return ap.hasFile("deployment.xml", "") }

func (ap *ApplicationPackage) hasFile(filename, zipName string) bool {
	if zipName == "" {
		zipName = filename
	}
	if ap.IsZip() {
		r, err := zip.OpenReader(ap.Path)
		if err != nil {
			return false
		}
		defer r.Close()
		for _, f := range r.File {
			if f.Name == zipName {
				return true
			}
		}
		return false
	}
	return util.PathExists(filepath.Join(ap.Path, filename))
}

func (ap *ApplicationPackage) IsZip() bool { return isZip(ap.Path) }

func (ap *ApplicationPackage) IsJava() bool {
	if ap.IsZip() {
		r, err := zip.OpenReader(ap.Path)
		if err != nil {
			return false
		}
		defer r.Close()
		for _, f := range r.File {
			if filepath.Ext(f.Name) == ".jar" {
				return true
			}
		}
		return false
	}
	return util.PathExists(filepath.Join(ap.Path, "pom.xml"))
}

func isZip(filename string) bool { return filepath.Ext(filename) == ".zip" }

func zipDir(dir string, destination string) error {
	if !util.PathExists(dir) {
		message := "'" + dir + "' should be an application package zip or dir, but does not exist"
		return errors.New(message)
	}
	if !util.IsDirectory(dir) {
		message := "'" + dir + "' should be an application package dir, but is a (non-zip) file"
		return errors.New(message)
	}

	file, err := os.Create(destination)
	if err != nil {
		message := "Could not create a temporary zip file for the application package: " + err.Error()
		return errors.New(message)
	}
	defer file.Close()

	w := zip.NewWriter(file)
	defer w.Close()

	walker := func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}
		if info.IsDir() {
			return nil
		}
		file, err := os.Open(path)
		if err != nil {
			return err
		}
		defer file.Close()

		zippath, err := filepath.Rel(dir, path)
		if err != nil {
			return err
		}
		zipfile, err := w.Create(zippath)
		if err != nil {
			return err
		}

		_, err = io.Copy(zipfile, file)
		if err != nil {
			return err
		}
		return nil
	}
	return filepath.Walk(dir, walker)
}

func (ap *ApplicationPackage) zipReader(test bool) (io.ReadCloser, error) {
	zipFile := ap.Path
	if test {
		zipFile = ap.TestPath
	}
	if !ap.IsZip() {
		tempZip, err := os.CreateTemp("", "vespa")
		if err != nil {
			return nil, fmt.Errorf("could not create a temporary zip file for the application package: %w", err)
		}
		defer func() {
			tempZip.Close()
			os.Remove(tempZip.Name())
			// TODO: Caller must remove temporary file
		}()
		if err := zipDir(zipFile, tempZip.Name()); err != nil {
			return nil, err
		}
		zipFile = tempZip.Name()
	}
	f, err := os.Open(zipFile)
	if err != nil {
		return nil, fmt.Errorf("could not open application package at %s: %w", ap.Path, err)
	}
	return f, nil
}

func (ap *ApplicationPackage) Unzip(test bool) (string, error) {
	if !ap.IsZip() {
		return "", fmt.Errorf("can't unzip a package that is a directory structure")
	}
	cleanTemp := true
	tmp, err := os.MkdirTemp(os.TempDir(), "vespa-test-pkg")
	if err != nil {
		return "", err
	}
	defer func() {
		if cleanTemp {
			os.RemoveAll(tmp)
		}
	}()
	path := ap.Path
	if test {
		path = ap.TestPath
	}
	f, err := zip.OpenReader(path)
	if err != nil {
		return "", err
	}
	defer f.Close()
	for _, f := range f.File {
		if !validPath(f.Name) {
			return "", fmt.Errorf("found invalid path inside zip: %s", f.Name)
		}
		dst := filepath.Join(tmp, f.Name)
		if f.FileInfo().IsDir() {
			if err := os.Mkdir(dst, f.FileInfo().Mode()); err != nil {
				return "", err
			}
			continue
		}
		if err := copyFile(f, dst); err != nil {
			return "", fmt.Errorf("copying %s to %s failed: %w", f.Name, dst, err)
		}
	}
	cleanTemp = false
	return tmp, nil
}

func validPath(path string) bool {
	path = strings.TrimSuffix(path, "/")
	if filepath.Clean(path) != path {
		return false
	}
	for _, part := range strings.Split(path, "/") {
		if part == ".." {
			return false
		}
	}
	return true
}

func copyFile(src *zip.File, dst string) error {
	from, err := src.Open()
	if err != nil {
		return err
	}
	defer from.Close()
	to, err := os.OpenFile(dst, os.O_CREATE|os.O_WRONLY, src.FileInfo().Mode())
	if err != nil {
		return err
	}
	defer to.Close()
	_, err = io.Copy(to, from)
	return err
}

// FindApplicationPackage finds the path to an application package from the zip file or directory zipOrDir. If
// requirePackaging is true, the application package is required to be packaged with mvn package.
func FindApplicationPackage(zipOrDir string, requirePackaging bool) (ApplicationPackage, error) {
	if isZip(zipOrDir) {
		return ApplicationPackage{Path: zipOrDir}, nil
	}
	if util.PathExists(filepath.Join(zipOrDir, "pom.xml")) {
		zip := filepath.Join(zipOrDir, "target", "application.zip")
		if util.PathExists(zip) {
			if testZip := filepath.Join(zipOrDir, "target", "application-test.zip"); util.PathExists(testZip) {
				return ApplicationPackage{Path: zip, TestPath: testZip}, nil
			}
			return ApplicationPackage{Path: zip}, nil
		}
		if requirePackaging {
			return ApplicationPackage{}, errors.New("found pom.xml, but target/application.zip does not exist: run 'mvn package' first")
		}
	}
	if path := filepath.Join(zipOrDir, "src", "main", "application"); util.PathExists(path) {
		testPath := ""
		if d := filepath.Join(zipOrDir, "src", "test", "application"); util.PathExists(d) {
			testPath = d
		}
		return ApplicationPackage{Path: path, TestPath: testPath}, nil
	}
	if util.PathExists(filepath.Join(zipOrDir, "services.xml")) {
		testPath := ""
		if util.PathExists(filepath.Join(zipOrDir, "tests")) {
			testPath = zipOrDir
		}
		return ApplicationPackage{Path: zipOrDir, TestPath: testPath}, nil
	}
	return ApplicationPackage{}, fmt.Errorf("could not find an application package source in '%s'", zipOrDir)
}
