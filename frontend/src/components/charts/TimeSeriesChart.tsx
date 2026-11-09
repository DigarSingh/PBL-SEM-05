import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { ChartTooltip } from './ChartTooltip';
import { axisTick, chartColors } from './theme';

/** Single-series trend over time: 2px line, 10% area wash, crosshair tooltip. */
export function TimeSeriesChart({ data, xKey, yKey, name, formatX, formatTooltipX, formatY, height = 260 }: {
  data: object[];
  xKey: string;
  yKey: string;
  name: string;
  formatX: (value: string) => string;
  formatTooltipX?: (value: string) => string;
  formatY: (value: number) => string;
  height?: number;
}) {
  return (
    <div style={{ height }} className="w-full">
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={data} margin={{ top: 8, right: 12, bottom: 0, left: 4 }}>
          <CartesianGrid vertical={false} stroke={chartColors.grid} />
          <XAxis dataKey={xKey} tickFormatter={formatX} tickLine={false} axisLine={{ stroke: chartColors.axis }} tick={axisTick} minTickGap={32} />
          <YAxis tickFormatter={formatY} tickLine={false} axisLine={false} tick={axisTick} width={72} allowDecimals={false} />
          <Tooltip cursor={{ stroke: chartColors.axis, strokeWidth: 1 }}
            content={(props) => <ChartTooltip {...props} formatLabel={formatTooltipX ?? formatX} formatValue={formatY} />} />
          <Area type="monotone" dataKey={yKey} name={name} stroke={chartColors.series} strokeWidth={2} fill={chartColors.series}
            fillOpacity={0.1} dot={false} isAnimationActive={false}
            activeDot={{ r: 5, fill: chartColors.series, stroke: chartColors.surface, strokeWidth: 2 }} />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
