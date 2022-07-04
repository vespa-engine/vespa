import React, { useContext, useState } from 'react';
import OverlayImageButton from './OverlayImageButton';

import copyImage from '../../assets/img/copy.svg';
import { ResponseContext } from '../Contexts/ResponseContext';
import { OverlayTrigger, Tooltip } from 'react-bootstrap';

export default function CopyResponseButton() {
  const { response } = useContext(ResponseContext);
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
      show={show}
      overlay={
        <Tooltip id="copy-tooltip">Response copied to clipboard</Tooltip>
      }
    >
      <span>
        <OverlayImageButton
          className="intro-copy"
          image={copyImage}
          height="30"
          width="30"
          tooltip="Copy"
          onClick={handleCopy}
        />
      </span>
    </OverlayTrigger>
  );
}
