// Chart palette: one validated series hue (dataviz reference slot 1; passes band, chroma and
// 3:1 contrast on the white card surface) plus recessive chrome. Data marks never reuse status
// colours, and text always uses text colours rather than the series hue.
export const chartColors = {
  series: '#2a78d6',
  surface: '#ffffff',
  grid: '#e1e0d9',
  axis: '#c3c2b7',
  muted: '#898781',
} as const;

export const axisTick = { fill: chartColors.muted, fontSize: 12 } as const;
