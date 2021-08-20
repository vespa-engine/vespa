// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa deploy command
// Author: bratseth

package cmd

import (
    "archive/zip"
    "errors"
    "github.com/spf13/cobra"
    "github.com/vespa-engine/vespa/utils"
    "io"
    "io/ioutil"
    "net/http"
    "net/url"
    "os"
    "path/filepath"
    "strings"
    "time"
)

func init() {
    rootCmd.AddCommand(deployCmd)
}

var deployCmd = &cobra.Command{
    Use:   "deploy application-package-dir OR application.zip",
    Short: "Deploys an application package",
    Long:  `TODO`,
    Args: func(cmd *cobra.Command, args []string) error {
        if len(args) > 1 {
          return errors.New("Expected an application as the only argument")
        }
        return nil
    },
    Run: func(cmd *cobra.Command, args []string) {
        if len(args) == 0 {
            deploy("src/main/application")
        } else {
            deploy(args[0])
        }
    },
}

func deploy(application string) {
    if ! strings.HasSuffix(application, ".zip") {
        tempZip, error := ioutil.TempFile("", "application.zip")
        if error != nil {
            utils.Error("Could not create a temporary zip file for the application package")
            utils.Detail(error.Error())
            return
        }

        error = zipDir(application, tempZip.Name())
        if (error != nil) {
            utils.Error(error.Error())
            return
        }
        defer os.Remove(tempZip.Name())
        application = tempZip.Name()
    }

    zipFileReader, zipFileError := os.Open(application)
    if zipFileError != nil {
        utils.Error("Could not open application package at " + application)
        utils.Detail(zipFileError.Error())
        return
    }

    url, _ := url.Parse(getTarget(deployContext).deploy + "/application/v2/tenant/default/prepareandactivate")
    header := http.Header{}
    header.Add("Content-Type", "application/zip")
    request := &http.Request{
        URL: url,
        Method: "POST",
        Header: header,
        Body: ioutil.NopCloser(zipFileReader),
    }
    serviceDescription := "Deploy service"
    response := utils.HttpDo(request, time.Minute * 10, serviceDescription)
    defer response.Body.Close()
    if (response == nil) {
        return
    } else if response.StatusCode == 200 {
        utils.Success("Success")
    } else if response.StatusCode % 100 == 4 {
        utils.Error("Invalid application package")
        // TODO: Output error in body
    } else {
        utils.Error("Error from", strings.ToLower(serviceDescription), "at", request.URL.Host)
        utils.Detail("Response status:", response.Status)
    }
}

func zipDir(dir string, destination string) error {
    if filepath.IsAbs(dir) {
        message := "Path must be relative, but '" + dir + "'"
        return errors.New(message)
    }
    if ! utils.PathExists(dir) {
        message := "'" + dir + "' should be an application package zip or dir, but does not exist"
        return errors.New(message)
    }
    if ! utils.IsDirectory(dir) {
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

