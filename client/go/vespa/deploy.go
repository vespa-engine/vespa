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

// Find application package relative given name, which is the path to a file or directory.
func FindApplicationPackage(name string) (ApplicationPackage, error) {
	if isZip(name) {
		return ApplicationPackage{Path: name}, nil
	}
	candidates := []string{
		filepath.Join(name, "target", "application.zip"),
		filepath.Join(name, "src", "main", "application", "services.xml"),
		filepath.Join(name, "services.xml"),
	}
	for _, path := range candidates {
		if !util.PathExists(path) {
			continue
		}
		if !isZip(path) {
			path = filepath.Dir(path)
		}
		return ApplicationPackage{Path: path}, nil
	}
	return ApplicationPackage{}, fmt.Errorf("no application package found in %s", name)
}

func (ap *ApplicationPackage) IsZip() bool { return isZip(ap.Path) }

func (ap *ApplicationPackage) HasCertificate() bool {
	if ap.IsZip() {
		return true // TODO: Consider looking inside zip to verify
	}
	return util.PathExists(filepath.Join(ap.Path, "security", "clients.pem"))
}

func isZip(filename string) bool { return filepath.Ext(filename) == ".zip" }

func Deploy(prepare bool, application string, target string) {
	pkg, noSourceError := FindApplicationPackage(application)
	if noSourceError != nil {
		util.Error(noSourceError.Error())
		return
	}

	zippedSource := pkg.Path
	if !pkg.IsZip() { // create zip
		tempZip, error := ioutil.TempFile("", "application.zip")
		if error != nil {
			util.Error("Could not create a temporary zip file for the application package")
			util.Detail(error.Error())
			return
		}

		error = zipDir(pkg.Path, tempZip.Name())
		if error != nil {
			util.Error(error.Error())
			return
		}
		defer os.Remove(tempZip.Name())
		zippedSource = tempZip.Name()
	}

	zipFileReader, zipFileError := os.Open(zippedSource)
	if zipFileError != nil {
		util.Error("Could not open application package at " + pkg.Path)
		util.Detail(zipFileError.Error())
		return
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
	response := util.HttpDo(request, time.Minute*10, serviceDescription)
	if response == nil {
		return
	}

	defer response.Body.Close()
	if response.StatusCode == 200 {
		util.Success("Deployed", pkg.Path)
	} else if response.StatusCode/100 == 4 {
		util.Error("Invalid application package", "("+response.Status+"):")
		util.PrintReader(response.Body)
	} else {
		util.Error("Error from", strings.ToLower(serviceDescription), "at", request.URL.Host, "("+response.Status+"):")
		util.PrintReader(response.Body)
	}
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
