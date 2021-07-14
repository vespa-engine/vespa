// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package utils

import (
    "bufio"
    "net/http"
    "strings"
    "time"
)

func HttpRequest(host string, path string, description string) (response *http.Response) {
    client := &http.Client{
	    Timeout: time.Second * 10,
    }
    response, error := client.Get(host + path)
    if error != nil {
        Error("Could not connect to", strings.ToLower(description), "at", host)
        Detail(error.Error())
        return
    }
    defer response.Body.Close()

    scanner := bufio.NewScanner(response.Body)

    if error := scanner.Err(); error != nil {
        Error("Error reading data from", strings.ToLower(description), "at", host)
        Detail(error.Error())
        return nil
    } else {
        return response
    }
}
