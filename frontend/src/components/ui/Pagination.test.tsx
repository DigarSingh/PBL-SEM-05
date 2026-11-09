import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Pagination } from './Pagination';

describe('Pagination', () => {
  it('renders nothing for a single page', () => {
    const { container } = render(<Pagination page={0} totalPages={1} onChange={vi.fn()} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('marks the current page and moves between pages', async () => {
    const onChange = vi.fn();
    render(<Pagination page={4} totalPages={10} onChange={onChange} />);
    expect(screen.getByRole('button', { name: '5' })).toHaveAttribute('aria-current', 'page');
    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));
    await userEvent.click(screen.getByRole('button', { name: '10' }));
    expect(onChange.mock.calls).toEqual([[5], [9]]);
    expect(screen.getAllByText('...')).toHaveLength(2);
  });
});
