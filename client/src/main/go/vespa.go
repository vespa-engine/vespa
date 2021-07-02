package main

import (
    "bufio"
    "fmt"
    "net/http"
    "strings"
)

func main() {
    status()
}

func status() {
    // See https://en.wikipedia.org/wiki/ANSI_escape_code#8-bit
    // colorReset := "\033[0m"
    colorRed := "\033[31m"
    colorGreen := "\033[32m"
    // colorBlue := "\033[34m"
    colorYellow := "\033[33m"

    target := "http://127.0.0.1:19071"
    targetName := "Config server"
    resp, err := http.Get(target + "/ApplicationStatus")
    if err != nil {
        fmt.Println(colorRed + "Could not connect to", strings.ToLower(targetName), "at", target)
        fmt.Println(colorYellow + err.Error())
        return
    }
    defer resp.Body.Close()

    scanner := bufio.NewScanner(resp.Body)

    if err := scanner.Err(); err != nil {
        fmt.Println(colorRed + "Error reading data from", strings.ToLower(targetName), "at", target)
        fmt.Println(colorYellow + err.Error())
    } else if resp.StatusCode != 200 {
        fmt.Println(colorRed + targetName, "at", target, " is not handling requests")
        fmt.Println(colorYellow + "Response status", resp.StatusCode)
    } else {
        fmt.Println(colorGreen + targetName, "at", target, "is UP")
    }
}
