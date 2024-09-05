import * as fs from 'fs';
import { publishVSIX, createVSIX } from '@vscode/vsce';

async function publish(): Promise<void> {
    
    await createVSIX();

    const rawJSON = await fs.promises.readFile("package.json", "utf8");
    const json = JSON.parse(rawJSON) as { name: string, version: string };

    const vsixFile = `${json.name}-${json.version}.vsix`;

    await publishVSIX(vsixFile, {
        pat: process.env.VSCE_PAT
    });
}

publish();
