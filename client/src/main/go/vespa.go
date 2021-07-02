package main

import (
    "bufio"
    "fmt"
    "net/http"
)

func main() {
    status()
}

func status() {
    // colorReset := "\033[0m"
    // colorRed := "\033[31m"
    // colorGreen := "\033[32m"
    colorYellow := "\033[33m"
    // colorBlue := "\033[34m"
    // colorPurple := "\033[35m"
    // colorCyan := "\033[36m"
    // colorWhite := "\033[37m"

    target := "http://127.0.0.1:19072"
    resp, err := http.Get(target + "/ApplicationStatus")
    if err != nil {
        fmt.Println("Could not connect to config server at", target)
        fmt.Println(colorYellow + err.Error())
        return
    }
    defer resp.Body.Close()

    fmt.Println("Response status: ", resp.Status)
    scanner := bufio.NewScanner(resp.Body)

    if err := scanner.Err(); err != nil {
        fmt.Println(err)
    } else {
        for i := 0; scanner.Scan() && i < 5; i++ {
            fmt.Println(scanner.Text())
        }
    }
}
