The Language-server for Vespa schemas

Use the jar file to integration the language server into your favorite editor.

For Visual Studio Code and IntelliJ the language server should also be available in the marketplace for the editor.

# Schema Language Server in Neovim

## Requirements
Requires java to be excutable on the system.

Optional: [lspconfig](https://github.com/neovim/nvim-lspconfig) plugin for nvim.

## Installation
Download `schema-language-server-jar-with-dependencies.jar`.

### Using lspconfig
Register `.sd` and `.profile` as filetypes (in `init.lua`):
```lua
vim.filetype.add {
  extension = {
    profile = 'sd',
    sd = 'sd'
  }
}
```

Create a config for schema language server (in `init.lua`):
```lua
local lspconfig = require "lspconfig"
local configs = require "lspconfig.configs"

if not configs.schemals then
    configs.schemals = {
        default_config = {
            filetypes = { 'sd' },
            cmd = { 'java', '-jar', '/path/to/schema-language-server-jar-with-dependencies.jar' },
            root_dir = lspconfig.util.root_pattern('.')
        },
    }
end

lspconfig.schemals.setup{
    -- optional on_attach function for setting keybindings etc.
    on_attach = function(client, bufnr)
       	-- local opts = {buffer = bufnr, remap = false}
	    -- vim.keymap.set("n", "gd", function() vim.lsp.buf.definition() end, opts)
    end
}
```

### Manual Installation
If you don't want to use lspconfig you can refer to the [LSP documentation for Neovim](https://neovim.io/doc/user/lsp.html) for manually registering the server.