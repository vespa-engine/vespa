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
    deployCmd.AddCommand(deployPrepareCmd)
    deployCmd.AddCommand(deployActivateCmd)
}

var deployCmd = &cobra.Command{
    Use:   "deploy",
    Short: "Deploys an application package",
    Long:  `TODO: Use prepare or deploy activate`,
    Run: func(cmd *cobra.Command, args []string) {
        utils.Error("Use either deploy prepare or deploy activate")
    },
}

var deployPrepareCmd = &cobra.Command{
    Use:   "prepare",
    Short: "Prepares an application for activation",
    Long:  `TODO:  prepare application-package-dir OR application.zip`,
    Args: func(cmd *cobra.Command, args []string) error {
        if len(args) > 1 {
          return errors.New("Expected an application package as the only argument")
        }
        return nil
    },
    Run: func(cmd *cobra.Command, args []string) {
        if len(args) == 0 {
            deploy(true, "src/main/application")
        } else {
            deploy(true, args[0])
        }
    },
}

var deployActivateCmd = &cobra.Command{
    Use:   "activate",
    Short: "Activates an application package. If no package argument, the previously prepared package is activated.",
    Long:  `TODO: activate  [application-package-dir OR application.zip]`,
    Args: func(cmd *cobra.Command, args []string) error {
        if len(args) > 1 {
          return errors.New("Expected an application package as the only argument")
        }
        return nil
    },
    Run: func(cmd *cobra.Command, args []string) {
        if len(args) == 0 {
            deploy(false, "")
        } else {
            deploy(false, args[0])
        }
    },
}

func deploy(prepare bool, application string) {
    // TODO: Support no application (activate)
    // TODO: Support application home as argument instead of src/main and
    //       - if target exists, use target/application.zip
    //       - else if src/main/application exists, use that
    //       - else if current dir has services.xml use that
    if filepath.Ext(application) != ".zip" {
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

    var deployUrl *url.URL
    if prepare {
        deployUrl, _ = url.Parse(getTarget(deployContext).deploy + "/application/v2/tenant/default/prepare")
    } else if application == "" {
        deployUrl, _ = url.Parse(getTarget(deployContext).deploy + "/application/v2/tenant/default/activate")
    } else {
        deployUrl, _ = url.Parse(getTarget(deployContext).deploy + "/application/v2/tenant/default/prepareandactivate")
    }

    header := http.Header{}
    header.Add("Content-Type", "application/zip")
    request := &http.Request{
        URL: deployUrl,
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
    } else if response.StatusCode / 100 == 4 {
        utils.Error("Invalid application package", "(" + response.Status + "):")
        utils.PrintReader(response.Body)
    } else {
        utils.Error("Error from", strings.ToLower(serviceDescription), "at", request.URL.Host, "(" + response.Status + "):")
        utils.PrintReader(response.Body)
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

