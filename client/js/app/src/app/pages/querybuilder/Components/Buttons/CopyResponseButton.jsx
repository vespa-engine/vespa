import React, { useState } from 'react';
import { OverlayTrigger, Tooltip } from 'react-bootstrap';
import ImageButton from './ImageButton';
import { useQueryBuilderContext } from 'app/pages/querybuilder/context/query-builder-provider';
import copyImage from 'app/pages/querybuilder/assets/img/copy.svg';

export default function CopyResponseButton() {
  const response = useQueryBuilderContext((ctx) => ctx.http.response);
  const [show, setShow] = useState(false);

  const handleCopy = () => {
    setShow(true);
    navigator.clipboard.writeText(response);
    setTimeout(() => {
      setShow(false);
    }, 2000);
  };

  return (
    <OverlayTrigger
      placement="left-end"
      overlay={
        <Tooltip>{show ? 'Response copied to clipboard' : 'Copy'}</Tooltip>
      }
    >
      <span>
        <ImageButton
          className="intro-copy"
          image={copyImage}
          height={30}
          width={30}
          onClick={handleCopy}
        />
      </span>
    </OverlayTrigger>
  );
}
