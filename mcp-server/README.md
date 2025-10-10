# Vespa MCP Server

This directory contains code for a Vespa MCP server, which allows interaction with a Vespa application through an MCP client.

### Tools
- **`executeQuery`**: Build and execute Vespa queries against the Vespa application.
- **`getSchemas`**: Retrieve the schemas of the Vespa application.
- **`searchDocumentation`**: Search the [Vespa documentation](https://docs.vespa.ai/) for relevant information based on a user input.

### Resources
- **`queryExamples`**: Provides query examples to the MCP client for guidance on how to use the `executeQuery` tool.

### Prompts
- **`listTools`**: Prompt to list the tools and their descriptions of the MCP server.

## How to use
1. Create your Vespa application like you would normally.
2. Add the following to your `services.xml` to enable the MCP server:
```xml
<handler id="ai.vespa.mcp.McpJdiscHandler" bundle="mcp-server">
    <binding>http://*/mcp/*</binding>
</handler>
```
3. Deploy your application.
4. Connect to the MCP server using an MCP client, e.g. [Claude Desktop](https://claude.ai/download):
```json
{
  "mcpServers": {
    "VespaMCP-java": {
      "command": "npx",
      "args": [
        "mcp-remote",
        "http://localhost:8080/mcp/",
        "--transport",
        "http-first"
      ]
    }
  }
}
```
> [!NOTE]
> Replace `http://localhost:8080/mcp/` with the URL of your deployed Vespa application, and add
> ```
> "--header",
> "Authorization: Bearer <YOUR-TOKEN>"
> ```
> to the `args` array if your Application is running in Vespa Cloud.

