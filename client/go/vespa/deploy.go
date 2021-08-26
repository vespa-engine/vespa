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

type ApplicationPackage struct {
	Path string
}

// Find an application package zip or directory below an application path
func FindApplicationPackage(application string) (ApplicationPackage, error) {
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

func (ap *ApplicationPackage) IsZip() bool { return isZip(ap.Path) }

func (ap *ApplicationPackage) HasCertificate() bool {
	if ap.IsZip() {
		return true // TODO: Consider looking inside zip to verify
	}
	return util.PathExists(filepath.Join(ap.Path, "security", "clients.pem"))
}

func isZip(filename string) bool { return filepath.Ext(filename) == ".zip" }

func Deploy(prepare bool, application string, target string) (string, error) {
	pkg, noSourceError := FindApplicationPackage(application)
	if noSourceError != nil {
		return "", noSourceError
	}

	zippedSource := pkg.Path
	if !pkg.IsZip() { // create zip
		tempZip, error := ioutil.TempFile("", "application.zip")
		if error != nil {
			return "", fmt.Errorf("Could not create a temporary zip file for the application package: %w", error)
		}

		error = zipDir(pkg.Path, tempZip.Name())
		if error != nil {
			return "", error
		}
		defer os.Remove(tempZip.Name())
		zippedSource = tempZip.Name()
	}

	zipFileReader, zipFileError := os.Open(zippedSource)
	if zipFileError != nil {
		return "", fmt.Errorf("Could not open application package at %s: %w", pkg.Path, zipFileError)
	}

	var deployUrl *url.URL
	if prepare {
		deployUrl, _ = url.Parse(target + "/application/v2/tenant/default/prepare")
	} else if application == "" {
		deployUrl, _ = url.Parse(target + "/application/v2/tenant/default/activate")
	} else {
		deployUrl, _ = url.Parse(target + "/application/v2/tenant/default/prepareandactivate")
	}

	header := http.Header{}
	header.Add("Content-Type", "application/zip")
	request := &http.Request{
		URL:    deployUrl,
		Method: "POST",
		Header: header,
		Body:   ioutil.NopCloser(zipFileReader),
	}
	serviceDescription := "Deploy service"
	response, err := util.HttpDo(request, time.Minute*10, serviceDescription)
	if err != nil {
		return "", err
	}
	defer response.Body.Close()

	if response.StatusCode/100 == 4 {
		return "", fmt.Errorf("Invalid application package (%s):\n%s", response.Status, util.ReaderToJSON(response.Body))
	} else if response.StatusCode != 200 {
		return "", fmt.Errorf("Error from %s at %s (%s):\n%s", strings.ToLower(serviceDescription), request.URL.Host, response.Status, util.ReaderToJSON(response.Body))
	}
	return pkg.Path, nil
}

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
