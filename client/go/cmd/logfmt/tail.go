// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa logfmt command
// Author: arnej

package logfmt

import (
	"bufio"
	"fmt"
	"io"
	"os"
	"time"
)

const lastLinesSize = 4 * 1024

type Line struct {
	Text string
}

type Tail struct {
	Lines     chan Line
	lineBuf   []byte
	lastError error
	curFile   *os.File
	fn        string
}

func FollowFile(fn string) (res Tail, err error) {
	file, err := os.Open(fn)
	if err != nil {
		return
	}
	sz, err := file.Seek(0, os.SEEK_END)
	if err != nil {
		return
	}
	res.lineBuf = make([]byte, lastLinesSize)
	if sz > lastLinesSize {
		sz, err = file.Seek(-lastLinesSize, os.SEEK_END)
		if err != nil {
			return
		}
		var n int
		n, err = file.Read(res.lineBuf)
		if err != nil {
			return
		}
		for i := 0; i < n; i++ {
			if res.lineBuf[i] == '\n' {
				sz, err = file.Seek(sz+int64(i)+1, os.SEEK_SET)
				if err != nil {
					return
				}
				break
			}
		}
	}
	res.fn = fn
	res.Lines = make(chan Line, 20)
	res.curFile = file
	res.lineBuf = res.lineBuf[:0]
	go runTailWith(&res)
	return
}

func runTailWith(t *Tail) {
	reader := bufio.NewReaderSize(t.curFile, 1024*1024)
loop:
	for {
		bytes, err := reader.ReadSlice('\n')
		t.lineBuf = append(t.lineBuf, bytes...)
		if err == bufio.ErrBufferFull {
			continue
		}
		if err == nil {
			ll := len(t.lineBuf) - 1
			t.Lines <- Line{Text: string(t.lineBuf[:ll])}
			t.lineBuf = t.lineBuf[:0]
			continue
		}
		if err == io.EOF {
			pos, _ := t.curFile.Seek(0, os.SEEK_CUR)
			for cnt := 0; cnt < 100; cnt++ {
				time.Sleep(10 * time.Millisecond)
				sz, _ := t.curFile.Seek(0, os.SEEK_END)
				if sz != pos {
					if sz < pos {
						pos = 0
					}
					t.curFile.Seek(pos, os.SEEK_SET)
					continue loop
				}
			}
			// try reopening
			file, err := os.Open(t.fn)
			if err == nil {
				sz, _ := file.Seek(0, os.SEEK_END)
				if sz != pos {
					file.Seek(0, os.SEEK_SET)
					t.curFile.Close()
					t.curFile = file
					reader = bufio.NewReaderSize(t.curFile, 1024*1024)
				} else {
					file.Close()
				}
			} else {
				fmt.Fprintf(os.Stderr, "reopen '%s': %v\n", t.fn, err)
			}
		} else {
			close(t.Lines)
			t.lastError = err
			return
		}
	}
}
