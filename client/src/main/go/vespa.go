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
    resp, err := http.Get("http://127.0.0.1:19071/ApplicationStatus")
    if err != nil {
        fmt.Println(err)
        return
    }
    defer resp.Body.Close()

    fmt.Println("Response status: ", resp.Status)
    scanner := bufio.NewScanner(resp.Body)

    if err := scanner.Err(); err != nil {
        fmt.Println(err)
    }
    else {
        for i := 0; scanner.Scan() && i < 5; i++ {
            fmt.Println(scanner.Text())
        }
    }
}
