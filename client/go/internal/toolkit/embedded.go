package toolkit

import (
	"embed"
	_ "embed"
)

//go:embed dashboard/assets/*
var Assets embed.FS

//go:embed dashboard/index.html
var IndexHTML string
