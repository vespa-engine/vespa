package main

import (
    "bufio"
    "fmt"
    "net/http"
    "strings"
    "time"
)

func main() {
    status()
}

func status() {
    host := "http://127.0.0.1:19071"
    path := "/ApplicationStatus"
    description := "Config server"
    //response := request(host, path, description)

    client := &http.Client{
	    Timeout: time.Second * 30,
    }
    resp, err := client.Get(host + path)
    if err != nil {
        error("Could not connect to", strings.ToLower(description), "at", host)
        detail(err.Error())
        return
    }
    defer resp.Body.Close()

    scanner := bufio.NewScanner(resp.Body)

    if err := scanner.Err(); err != nil {
        error("Error reading data from", strings.ToLower(description), "at", host)
        detail(err.Error())
    } else if resp.StatusCode != 200 {
        error(description, "at", host, "is not ready")
        detail("Response status:", resp.Status)
    } else {
        success(description, "at", host, "is ready")
    }
}

func request(host string, path string, description string) {
}

func error(messages ...string) {
    print("\033[31m", messages)
}

func success(messages ...string) {
    print("\033[32m", messages)
}

func detail(messages ...string) {
    print("\033[33m", messages)
}

func print(prefix string, messages []string) {
   fmt.Print(prefix)
    for i := 0; i < len(messages); i++ {
        fmt.Print(messages[i])
        fmt.Print(" ")
    }
    fmt.Println("")
}
