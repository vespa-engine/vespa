import React from 'react';
import transform from 'app/pages/querybuilder/TransformVespaTrace';

export default function DownloadJSONButton({ children, response }) {
  const handleClick = () => {
    let content;
    try {
      content = JSON.stringify(transform(JSON.parse(response), null, 4));
    } catch (error) {
      alert(`Failed to transform response to Jaeger format: ${error}`); // TODO: Change to toast
      return;
    }

    // copied from safakeskin´s answer on SO, link: https://stackoverflow.com/questions/55613438/reactwrite-to-json-file-or-export-download-no-server
    const blob = new Blob([content], { type: 'application/json' });
    const href = URL.createObjectURL(blob);

    // create "a" HTML element with href to file
    const link = document.createElement('a');
    link.href = href;
    link.download = 'vespa-response.json';
    document.body.appendChild(link);
    link.click();

    // clean up "a" element & remove ObjectURL
    document.body.removeChild(link);
    URL.revokeObjectURL(href);
  };

  return <button onClick={handleClick}>{children}</button>;
}
