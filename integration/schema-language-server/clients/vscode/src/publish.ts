import * as fs from 'fs';
import { publishVSIX, createVSIX } from '@vscode/vsce';
import * as actions from '@actions/core';

async function publish(): Promise<void> {
    let version = actions.getInput("version", { required: false, trimWhitespace: true });

    if (version == "") {
        version = "patch"
    }


    await createVSIX({
        version: version
    });

    actions.info("Created VSIX");

    const rawJSON = await fs.promises.readFile("package.json", "utf8");
    const json = JSON.parse(rawJSON) as { name: string, version: string };

    const vsixFile = `${json.name}-${json.version}.vsix`;

    actions.info(`Publishing VSIX file: ${vsixFile}`);

    await publishVSIX(vsixFile, {
        pat: process.env.VSCE_PAT
    });
}

publish();
