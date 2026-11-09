import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { ChartTooltip } from './ChartTooltip';
import { axisTick, chartColors } from './theme';

/** Single-series counts per period: thin columns with a rounded data end, square at the baseline. */
export function ColumnChart({ data, xKey, yKey, name, formatX, formatTooltipX, formatY, height = 240 }: {
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
        <BarChart data={data} margin={{ top: 8, right: 12, bottom: 0, left: 4 }} barCategoryGap={2}>
          <CartesianGrid vertical={false} stroke={chartColors.grid} />
          <XAxis dataKey={xKey} tickFormatter={formatX} tickLine={false} axisLine={{ stroke: chartColors.axis }} tick={axisTick} minTickGap={32} />
          <YAxis tickFormatter={formatY} tickLine={false} axisLine={false} tick={axisTick} width={48} allowDecimals={false} />
          <Tooltip cursor={{ fill: chartColors.grid, fillOpacity: 0.5 }}
            content={(props) => <ChartTooltip {...props} formatLabel={formatTooltipX ?? formatX} formatValue={formatY} />} />
          <Bar dataKey={yKey} name={name} fill={chartColors.series} maxBarSize={24} radius={[4, 4, 0, 0]} isAnimationActive={false} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
