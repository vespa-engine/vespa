// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package mock

import (
	"os"
	"path/filepath"
	"testing"
)

// ApplicationPackageDir creates a mock application package directory using test helper t, returning the path to the
// "application" directory and the root directory where it was created. If java is true, create a file that indicates
// this package contains Java code. If cert is true, create an empty certificate file.
func ApplicationPackageDir(t *testing.T, java, cert bool) (string, string) {
	t.Helper()
	rootDir := t.TempDir()
	appDir := filepath.Join(rootDir, "src", "main", "application")
	if err := os.MkdirAll(appDir, 0755); err != nil {
		t.Fatal(err)
	}
	servicesXML := filepath.Join(appDir, "services.xml")
	if _, err := os.Create(servicesXML); err != nil {
		t.Fatal(err)
	}
	if java {
		if _, err := os.Create(filepath.Join(rootDir, "pom.xml")); err != nil {
			t.Fatal(err)
		}
	}
	if cert {
		securityDir := filepath.Join(appDir, "security")
		if err := os.MkdirAll(securityDir, 0755); err != nil {
			t.Fatal(err)
		}
		if _, err := os.Create(filepath.Join(securityDir, "clients.pem")); err != nil {
			t.Fatal(err)
		}
	}
	return appDir, rootDir
}
