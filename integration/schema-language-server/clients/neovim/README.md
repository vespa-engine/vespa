# Schema Language Server in Neovim

## Requirements
Requires java to be excutable on the system.

Optional: [lspconfig](https://github.com/neovim/nvim-lspconfig) plugin for nvim.

## Installation
Download `schema-language-server-jar-with-dependencies.jar`.

### Using lspconfig
The language server is registered at `lspconfig` as `vespa_ls`. If you have `lspconfig` installed, all that needs to 
be done is to enable the language server.

Register `.sd`, `.profile` and `.yql` as filetypes (in `init.lua`):
```lua
vim.filetype.add {
  extension = {
    profile = 'sd',
    sd = 'sd',
    yql = 'yql'
  }
}
```

Create a config for schema language server (in `init.lua`):
```lua
vim.lsp.config('vespa_ls', {
    cmd = { 'java', '-jar', '/path/to/schema-language-server-jar-with-dependencies.jar' },
    -- on_attach = ...
})

vim.lsp.enable('vespa_ls')
```

### Manual Installation
If you don't want to use lspconfig you can refer to the [LSP documentation for Neovim](https://neovim.io/doc/user/lsp.html) for manually registering the server.
