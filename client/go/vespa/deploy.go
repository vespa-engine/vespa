// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa deploy API
// Author: bratseth

package vespa

import (
	"archive/zip"
	"errors"
	"fmt"
	"io"
	"io/ioutil"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/vespa-engine/vespa/util"
)

type ApplicationID struct {
	Tenant      string
	Application string
	Instance    string
}

type ZoneID struct {
	Environment string
	Region      string
}

type Deployment struct {
	ApplicationSource string
	TargetType        string
	TargetURL         string
	Application       ApplicationID
	Zone              ZoneID
	KeyPair           PemKeyPair
	APIKey            []byte
}

type ApplicationPackage struct {
	Path string
}

func (a ApplicationID) String() string {
	return fmt.Sprintf("%s.%s.%s", a.Tenant, a.Application, a.Instance)
}

func (d Deployment) String() string {
	return fmt.Sprintf("deployment of %s to %s target", d.Application, d.TargetType)
}

func (d *Deployment) IsCloud() bool { return d.TargetType == "cloud" }

func (ap *ApplicationPackage) IsZip() bool { return isZip(ap.Path) }

func (ap *ApplicationPackage) HasCertificate() bool {
	if ap.IsZip() {
		return true // TODO: Consider looking inside zip to verify
	}
	return util.PathExists(filepath.Join(ap.Path, "security", "clients.pem"))
}

func (ap *ApplicationPackage) zipReader() (io.ReadCloser, error) {
	zipFile := ap.Path
	if !ap.IsZip() {
		tempZip, error := ioutil.TempFile("", "application.zip")
		if error != nil {
			return nil, fmt.Errorf("Could not create a temporary zip file for the application package: %w", error)
		}
		if err := zipDir(ap.Path, tempZip.Name()); err != nil {
			return nil, err
		}
		defer os.Remove(tempZip.Name())
		zipFile = tempZip.Name()
	}
	r, err := os.Open(zipFile)
	if err != nil {
		return nil, fmt.Errorf("Could not open application package at %s: %w", ap.Path, err)
	}
	return r, nil
}

// Find an application package zip or directory below an application path
func ApplicationPackageFrom(application string) (ApplicationPackage, error) {
	if isZip(application) {
		return ApplicationPackage{Path: application}, nil
	}
	if util.PathExists(filepath.Join(application, "pom.xml")) {
		zip := filepath.Join(application, "target", "application.zip")
		if !util.PathExists(zip) {
			return ApplicationPackage{}, errors.New("pom.xml exists but no target/application.zip. Run mvn package first")
		} else {
			return ApplicationPackage{Path: zip}, nil
		}
	}
	if util.PathExists(filepath.Join(application, "src", "main", "application")) {
		return ApplicationPackage{Path: filepath.Join(application, "src", "main", "application")}, nil
	}
	if util.PathExists(filepath.Join(application, "services.xml")) {
		return ApplicationPackage{Path: application}, nil
	}
	return ApplicationPackage{}, errors.New("Could not find an application package source in '" + application + "'")
}

func ApplicationFromString(s string) (ApplicationID, error) {
	parts := strings.Split(s, ".")
	if len(parts) != 3 {
		return ApplicationID{}, fmt.Errorf("invalid application: %q", s)
	}
	return ApplicationID{Tenant: parts[0], Application: parts[1], Instance: parts[2]}, nil
}

func ZoneFromString(s string) (ZoneID, error) {
	parts := strings.Split(s, ".")
	if len(parts) != 2 {
		return ZoneID{}, fmt.Errorf("invalid zone: %q", s)
	}
	return ZoneID{Environment: parts[0], Region: parts[1]}, nil
}

func Prepare(deployment Deployment) (string, error) {
	if deployment.IsCloud() {
		return "", fmt.Errorf("%s: prepare is not supported", deployment)
	}
	// TODO: This doesn't work. A session ID must be explicitly created and then passed to the prepare call
	// https://docs.vespa.ai/en/cloudconfig/deploy-rest-api-v2.html
	u, err := url.Parse(deployment.TargetURL + "/application/v2/tenant/default/prepare")
	if err != nil {
		return "", err
	}
	return deploy(u, deployment.ApplicationSource)
}

func Activate(deployment Deployment) (string, error) {
	if deployment.IsCloud() {
		return "", fmt.Errorf("%s: activate is not supported", deployment)
	}
	// TODO: This doesn't work. A session ID must be explicitly created and then passed to the activate call
	// https://docs.vespa.ai/en/cloudconfig/deploy-rest-api-v2.html
	u, err := url.Parse(deployment.TargetURL + "/application/v2/tenant/default/activate")
	if err != nil {
		return "", err
	}
	return deploy(u, deployment.ApplicationSource)
}

func Deploy(deployment Deployment) (string, error) {
	path := "/application/v2/tenant/default/prepareandactivate"
	if deployment.IsCloud() {
		if deployment.APIKey == nil {
			return "", fmt.Errorf("%s: missing api key", deployment.String())
		}
		if deployment.KeyPair.Certificate == nil {
			return "", fmt.Errorf("%s: missing certificate", deployment)
		}
		if deployment.KeyPair.PrivateKey == nil {
			return "", fmt.Errorf("%s: missing private key", deployment)
		}
		if deployment.Zone.Environment == "" || deployment.Zone.Region == "" {
			return "", fmt.Errorf("%s: missing zone", deployment)
		}
		path = fmt.Sprintf("/application/v4/tenant/%s/application/%s/instance/%s/deploy/%s-%s",
			deployment.Application.Tenant,
			deployment.Application.Application,
			deployment.Application.Instance,
			deployment.Zone.Environment,
			deployment.Zone.Region)
		return "", fmt.Errorf("cloud deployment is not implemented")
	}
	u, err := url.Parse(deployment.TargetURL + path)
	if err != nil {
		return "", err
	}
	return deploy(u, deployment.ApplicationSource)
}

func deploy(url *url.URL, applicationSource string) (string, error) {
	pkg, err := ApplicationPackageFrom(applicationSource)
	if err != nil {
		return "", err
	}
	zipReader, err := pkg.zipReader()
	if err != nil {
		return "", err
	}
	if err := postApplicationPackage(url, zipReader); err != nil {
		return "", err
	}
	return pkg.Path, nil
}

func postApplicationPackage(url *url.URL, zipReader io.Reader) error {
	header := http.Header{}
	header.Add("Content-Type", "application/zip")
	request := &http.Request{
		URL:    url,
		Method: "POST",
		Header: header,
		Body:   io.NopCloser(zipReader),
	}
	serviceDescription := "Deploy service"
	response, err := util.HttpDo(request, time.Minute*10, serviceDescription)
	if err != nil {
		return err
	}
	defer response.Body.Close()

	if response.StatusCode/100 == 4 {
		return fmt.Errorf("Invalid application package (%s):\n%s", response.Status, util.ReaderToJSON(response.Body))
	} else if response.StatusCode != 200 {
		return fmt.Errorf("Error from %s at %s (%s):\n%s", strings.ToLower(serviceDescription), request.URL.Host, response.Status, util.ReaderToJSON(response.Body))
	}
	return nil
}

func isZip(filename string) bool { return filepath.Ext(filename) == ".zip" }

func zipDir(dir string, destination string) error {
	if filepath.IsAbs(dir) {
		message := "Path must be relative, but '" + dir + "'"
		return errors.New(message)
	}
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

		zippath := strings.TrimPrefix(path, dir)
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
