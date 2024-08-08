import * as fs from 'fs';
import { publishVSIX, createVSIX } from '@vscode/vsce';
import * as actions from '@actions/core';

async function publish(): Promise<void> {
    const version = actions.getInput("version", { required: true });

    await createVSIX({
        version: version
    });

    actions.info("Created VSIX");

    const rawJSON = await fs.promises.readFile("package.json", "utf8");
    const json = JSON.parse(rawJSON) as { name: string, version: string };

    const vsixFile = `${json.name}-${json.version}.vsix`;

    actions.info(`Publishing VSIX file: ${vsixFile}`);

    await publishVSIX(vsixFile);
}

publish();
