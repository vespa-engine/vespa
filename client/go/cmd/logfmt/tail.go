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
	Lines   chan Line
	lineBuf []byte
	curFile *os.File
	fn      string
	reader  *bufio.Reader
}

func FollowFile(fn string) (res Tail) {
	res.fn = fn
	res.lineBuf = make([]byte, lastLinesSize)
	res.openTail()
	res.Lines = make(chan Line, 20)
	res.lineBuf = res.lineBuf[:0]
	go runTailWith(&res)
	return res
}

func (t *Tail) setFile(f *os.File) {
	if t.curFile != nil {
		t.curFile.Close()
	}
	t.curFile = f
	if f != nil {
		t.reader = bufio.NewReaderSize(f, 1024*1024)
	} else {
		t.reader = nil
	}
}

// open log file and seek to the start of a line near the end, if possible.
func (t *Tail) openTail() {
	file, err := os.Open(t.fn)
	if err != nil {
		return
	}
	sz, err := file.Seek(0, os.SEEK_END)
	if err != nil {
		return
	}
	if sz < lastLinesSize {
		sz, err = file.Seek(0, os.SEEK_SET)
		if err == nil {
			// just read from start of file, all OK
			t.setFile(file)
		}
		return
	}
	sz, _ = file.Seek(-lastLinesSize, os.SEEK_END)
	n, err := file.Read(t.lineBuf)
	if err != nil {
		return
	}
	for i := 0; i < n; i++ {
		if t.lineBuf[i] == '\n' {
			sz, err = file.Seek(sz+int64(i+1), os.SEEK_SET)
			if err == nil {
				t.setFile(file)
			}
			return
		}
	}
}

func (t *Tail) reopen(oldSize int64) {
	for cnt := 0; cnt < 100; cnt++ {
		file, r := os.Open(t.fn)
		if r != nil {
			t.setFile(nil)
			if cnt == 0 {
				fmt.Fprintf(os.Stderr, "%v (waiting for log file to appear)\n", r)
			}
			time.Sleep(1000 * time.Millisecond)
			continue
		}
		sz, _ := file.Seek(0, os.SEEK_END)
		if sz != oldSize {
			file.Seek(0, os.SEEK_SET)
			if t.curFile != nil {
				t.curFile.Close()
			}
			t.setFile(file)
		} else {
			// same size, same file (probably), continue following it
			file.Close()
		}
		return
	}
}

// runs as a goroutine
func runTailWith(t *Tail) {
	defer t.setFile(nil)
loop:
	for {
		for t.curFile == nil {
			t.reopen(-1)
		}
		bytes, err := t.reader.ReadSlice('\n')
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
						// truncation case
						pos = 0
					}
					t.curFile.Seek(pos, os.SEEK_SET)
					continue loop
				}
			}
			// no change in file size, try reopening
			t.reopen(pos)
		} else {
			fmt.Fprintf(os.Stderr, "error tailing '%s': %v\n", t.fn, err)
			close(t.Lines)
			return
		}
	}
}
