import React from 'react';
import OverlayTrigger from 'react-bootstrap/OverlayTrigger';
import Tooltip from 'react-bootstrap/Tooltip';
import ImageButton from './ImageButton';

export default function OverlayImageButton({
  onClick,
  children,
  className,
  id,
  image,
  height = '15',
  width = '15',
  style,
  tooltip,
}) {
  return (
    <OverlayTrigger
      placement="right"
      delay={{ show: 250, hide: 400 }}
      overlay={<Tooltip id="button-tooltip">{tooltip}</Tooltip>}
    >
      <span>
        <ImageButton
          id={id}
          className={className}
          image={image}
          height={height}
          width={width}
          style={style}
          onClick={onClick}
        >
          {children}
        </ImageButton>
      </span>
    </OverlayTrigger>
  );
}

//
