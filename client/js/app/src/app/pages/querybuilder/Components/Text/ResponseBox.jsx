import React, { useContext, useEffect, useState } from 'react';
import { ResponseContext } from '../Contexts/ResponseContext';

export default function ResponseBox() {
  const { response } = useContext(ResponseContext);

  return (
    <textarea
      id="responsetext"
      className="responsebox"
      readOnly
      cols="70"
      rows="25"
      value={response}
    />
  );
}
