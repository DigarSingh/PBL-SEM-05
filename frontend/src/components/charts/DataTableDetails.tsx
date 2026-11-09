import type { ReactNode } from 'react';

/** "View as table": the accessible twin of a chart, collapsed by default. */
export function DataTableDetails({ caption, columns, rows }: {
  caption: string;
  columns: string[];
  rows: ReactNode[][];
}) {
  return (
    <details className="mt-3 text-sm">
      <summary className="cursor-pointer text-xs font-semibold text-slate-500 hover:text-slate-800">View as table</summary>
      <div className="mt-2 max-h-72 overflow-auto rounded-lg border border-stone-200">
        <table className="w-full text-left text-xs">
          <caption className="sr-only">{caption}</caption>
          <thead className="sticky top-0 bg-stone-50 text-slate-500">
            <tr>{columns.map((column) => <th key={column} scope="col" className="px-3 py-2 font-semibold">{column}</th>)}</tr>
          </thead>
          <tbody className="divide-y divide-stone-100 tabular-nums">
            {rows.map((row, index) => (
              <tr key={index}>{row.map((cell, cellIndex) => <td key={cellIndex} className="px-3 py-1.5 text-slate-700">{cell}</td>)}</tr>
            ))}
          </tbody>
        </table>
      </div>
    </details>
  );
}
