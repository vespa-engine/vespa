// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"fmt"
)

const (
	_ = 1 << (10 * iota)
	PowerOfTwo10
	PowerOfTwo20
	PowerOfTwo30
)

type AmountOfMemory struct {
	numBytes int64
}

func BytesOfMemory(v int64) AmountOfMemory {
	return AmountOfMemory{numBytes: v}
}
func KiloBytesOfMemory(v int64) AmountOfMemory {
	return BytesOfMemory(v * PowerOfTwo10)
}
func MegaBytesOfMemory(v int) AmountOfMemory {
	return BytesOfMemory(int64(v) * PowerOfTwo20)
}
func GigaBytesOfMemory(v int) AmountOfMemory {
	return BytesOfMemory(int64(v) * PowerOfTwo30)
}

func (v AmountOfMemory) ToBytes() int64 {
	return v.numBytes
}
func (v AmountOfMemory) ToKB() int64 {
	return v.numBytes / PowerOfTwo10
}
func (v AmountOfMemory) ToMB() int {
	return int(v.numBytes / PowerOfTwo20)
}
func (v AmountOfMemory) ToGB() int {
	return int(v.numBytes / PowerOfTwo30)
}
func (v AmountOfMemory) AsJvmSpec() string {
	val := v.ToKB()
	suffix := "k"
	if val%PowerOfTwo10 == 0 {
		val = val / PowerOfTwo10
		suffix = "m"
		if val%PowerOfTwo10 == 0 {
			val = val / PowerOfTwo10
			suffix = "g"
		}
	}
	return fmt.Sprintf("%d%s", val, suffix)
}

func ParseJvmMemorySpec(spec string) (result AmountOfMemory, err error) {
	result = BytesOfMemory(0)
	var n int
	var val int64
	var suffix rune
	n, err = fmt.Sscanf(spec, "%d%c", &val, &suffix)
	if n == 2 && err == nil {
		switch suffix {
		case 'k':
			result = KiloBytesOfMemory(val)
		case 'm':
			result = MegaBytesOfMemory(int(val))
		case 'g':
			result = GigaBytesOfMemory(int(val))
		default:
			err = fmt.Errorf("Unknown suffix in JVM memory spec '%s'", spec)
		}
	}
	return
}
