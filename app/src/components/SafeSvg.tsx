import React from 'react';

interface SafeSvgProps {
  width?: number;
  height?: number;
  fill?: string;
  stroke?: string;
  children?: React.ReactNode;
  style?: any;
}

const SafeSvg: React.FC<SafeSvgProps> = ({ width = 24, height = 24, fill, stroke, children, style, ...props }) => {
  const defaultFill = fill || 'currentColor';
  const defaultStroke = stroke || 'currentColor';

  return (
    <svg
      width={width}
      height={height}
      viewBox="0 0 24 24"
      fill="none"
      stroke={defaultStroke}
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      style={style}
      {...props}
    >
      {children}
    </svg>
  );
};

export default SafeSvg;
