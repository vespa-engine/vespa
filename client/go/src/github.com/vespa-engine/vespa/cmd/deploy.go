// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa deploy command
// Author: bratseth

package cmd

import (
    "errors"
    "github.com/spf13/cobra"
    "github.com/vespa-engine/vespa/utils"
    "io/ioutil"
    "net/http"
    "net/url"
    "os"
    "strings"
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
    // (cd src/main/application && zip -r - .) | \
    //     curl --header Content-Type:application/zip --data-binary @- \
    //     localhost:19071/application/v2/tenant/default/prepareandactivate

    if ! strings.HasSuffix(application, ".zip") {
        // TODO: Zip it
    }

    applicationReader, applicationError := os.Open(application)
    if applicationError != nil {
        utils.Error("Could not open application package at " + application)
        utils.Detail(applicationError.Error())
    }

    url, _ := url.Parse("http://127.0.0.1:19071/application/v2/tenant/default/prepareandactivate")
    header := http.Header{}
    header.Add("Content-Type", "application/zip")
    request := &http.Request{
        URL: url,
        Method: "POST",
        Header: header,
        Body: ioutil.NopCloser(applicationReader),
    }
    serviceDescription := "Deploy service"
    response := utils.HttpDo(request, serviceDescription)
    if response.StatusCode == 200 {
        utils.Success("Success")
    } else if response.StatusCode % 100 == 4 {
        utils.Error("Invalid application package")
        // TODO: Output error in body
    } else {
        utils.Error("Error from", strings.ToLower(serviceDescription), "at", request.URL.Host)
        utils.Detail("Response status:", response.Status)
    }
}

