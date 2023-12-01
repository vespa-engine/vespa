// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package vespa

import (
	"archive/zip"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/ioutil"
)

type ApplicationPackage struct {
	Path     string
	TestPath string
}

func (ap *ApplicationPackage) HasCertificate() bool { return ap.hasFile("security", "clients.pem") }

func (ap *ApplicationPackage) HasDeploymentSpec() bool { return ap.hasFile("deployment.xml", "") }

func (ap *ApplicationPackage) hasFile(pathSegment ...string) bool {
	if !ap.IsZip() {
		return ioutil.Exists(filepath.Join(append([]string{ap.Path}, pathSegment...)...))
	}
	zipName := filepath.Join(pathSegment...)
	return ap.hasZipEntry(func(name string) bool { return zipName == name })
}

func (ap *ApplicationPackage) hasZipEntry(matcher func(zipName string) bool) bool {
	r, err := zip.OpenReader(ap.Path)
	if err != nil {
		return false
	}
	defer r.Close()
	for _, f := range r.File {
		if matcher(f.Name) {
			return true
		}
	}
	return false
}

func (ap *ApplicationPackage) IsZip() bool { return isZip(ap.Path) }

func (ap *ApplicationPackage) IsJava() bool {
	if ap.IsZip() {
		return ap.hasZipEntry(func(name string) bool { return filepath.Ext(name) == ".jar" })
	}
	return ioutil.Exists(filepath.Join(ap.Path, "pom.xml"))
}

func (ap *ApplicationPackage) Validate() error {
	if !ap.IsZip() {
		return nil
	}
	invalidPath := ""
	invalid := ap.hasZipEntry(func(name string) bool {
		if !validPath(name) {
			invalidPath = name
			return true
		}
		return false
	})
	if invalid {
		return fmt.Errorf("found invalid path inside zip: %s", invalidPath)
	}
	return nil
}

func isZip(filename string) bool { return filepath.Ext(filename) == ".zip" }

func zipDir(dir string, destination string) error {
	if !ioutil.Exists(dir) {
		message := "'" + dir + "' should be an application package zip or dir, but does not exist"
		return errors.New(message)
	}
	if !ioutil.IsDir(dir) {
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
		if ignorePackageFile(filepath.Base(path)) {
			if info.IsDir() {
				return filepath.SkipDir
			}
			return nil
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

func ignorePackageFile(name string) bool {
	switch name {
	case ".DS_Store":
		return true
	}
	return false
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
		return nil, fmt.Errorf("could not open application package at '%s': %w", ap.Path, err)
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

func (ap *ApplicationPackage) HasTests() bool { return ap.TestPath != "" }

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

type PackageOptions struct {
	// If true, a Maven-based Vespa application package is required to be compiled
	Compiled bool

	// If true, only consider the source directores of the application package
	SourceOnly bool
}

// FindApplicationPackage finds the path to an application package from the zip file or directory zipOrDir. If
// requirePackaging is true, the application package is required to be packaged with mvn package.
//
// Package to use is preferred in this order:
// 1. Given path, if it's a zip
// 2. target/application
// 3. src/main/application
// 4. Given path, if it contains services.xml
func FindApplicationPackage(zipOrDir string, options PackageOptions) (ApplicationPackage, error) {
	pkg, err := findApplicationPackage(zipOrDir, options)
	if err != nil {
		return ApplicationPackage{}, err
	}
	if err := pkg.Validate(); err != nil {
		return ApplicationPackage{}, err
	}
	return pkg, nil
}

func findApplicationPackage(zipOrDir string, options PackageOptions) (ApplicationPackage, error) {
	if isZip(zipOrDir) {
		return ApplicationPackage{Path: zipOrDir}, nil
	}
	// Pre-packaged application. We prefer the uncompressed application because this allows us to add
	// security/clients.pem to the package on-demand
	hasPOM := ioutil.Exists(filepath.Join(zipOrDir, "pom.xml"))
	if hasPOM && !options.SourceOnly {
		path := filepath.Join(zipOrDir, "target", "application")
		if ioutil.Exists(path) {
			testPath := existingPath(filepath.Join(zipOrDir, "target", "application-test"))
			return ApplicationPackage{Path: path, TestPath: testPath}, nil
		}
		if options.Compiled {
			return ApplicationPackage{}, fmt.Errorf("found pom.xml, but %s does not exist: run 'mvn package' first", path)
		}
	}
	// Application with Maven directory structure, but with no POM or no hard requirement on packaging
	if path := filepath.Join(zipOrDir, "src", "main", "application"); ioutil.Exists(path) {
		testPath := existingPath(filepath.Join(zipOrDir, "src", "test", "application"))
		return ApplicationPackage{Path: path, TestPath: testPath}, nil
	}
	// Application without Java components
	if ioutil.Exists(filepath.Join(zipOrDir, "services.xml")) {
		testPath := ""
		if ioutil.Exists(filepath.Join(zipOrDir, "tests")) {
			testPath = zipOrDir
		}
		return ApplicationPackage{Path: zipOrDir, TestPath: testPath}, nil
	}
	return ApplicationPackage{}, fmt.Errorf("could not find an application package source in '%s'", zipOrDir)
}

func existingPath(path string) string {
	if ioutil.Exists(path) {
		return path
	}
	return ""
}
