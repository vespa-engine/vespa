// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa deploy API
// Author: bratseth

package vespa

import (
	"archive/zip"
	"errors"
	"github.com/vespa-engine/vespa/util"
	"io"
	"io/ioutil"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"time"
)

func Deploy(prepare bool, application string, target string) {
	source, noSourceError := determineSource(application)
	if noSourceError != nil {
		util.Error(noSourceError.Error())
		return
	}

	var zippedSource string
	if filepath.Ext(source) == ".zip" {
		zippedSource = source
	} else { // create zip
		tempZip, error := ioutil.TempFile("", "application.zip")
		if error != nil {
			util.Error("Could not create a temporary zip file for the application package")
			util.Detail(error.Error())
			return
		}

		error = zipDir(source, tempZip.Name())
		if error != nil {
			util.Error(error.Error())
			return
		}
		defer os.Remove(tempZip.Name())
		zippedSource = tempZip.Name()
	}

	zipFileReader, zipFileError := os.Open(zippedSource)
	if zipFileError != nil {
		util.Error("Could not open application package at " + source)
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
		util.Success("Success")
	} else if response.StatusCode/100 == 4 {
		util.Error("Invalid application package", "("+response.Status+"):")
		util.PrintReader(response.Body)
	} else {
		util.Error("Error from", strings.ToLower(serviceDescription), "at", request.URL.Host, "("+response.Status+"):")
		util.PrintReader(response.Body)
	}
}

// Use heuristics to determine the source (directory or zip) of an application package deployment
func determineSource(application string) (string, error) {
	if filepath.Ext(application) == ".zip" {
		return application, nil
	}
	if util.PathExists(filepath.Join(application, "target")) {
		source := filepath.Join(application, "target", "application.zip")
		if !util.PathExists(source) {
			return "", errors.New("target/ exists but have no application.zip. Run mvn package first")
		} else {
			return source, nil
		}
	}
	if util.PathExists(filepath.Join(application, "src", "main", "application")) {
		return filepath.Join(application, "src", "main", "application"), nil
	}
	if util.PathExists(filepath.Join(application, "services.xml")) {
		return application, nil
	}
	return "", errors.New("Could not find an application package source in '" + application + "'")
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
