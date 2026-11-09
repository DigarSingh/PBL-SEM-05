import type { ReactNode } from 'react';
import { PageHeader, type Crumb } from '@/components/ui/PageHeader';
import { useDocumentTitle } from '@/lib/hooks';

export function AdminPage({ title, description, actions, breadcrumbs, children }: {
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
  breadcrumbs?: Crumb[];
  children: ReactNode;
}) {
  useDocumentTitle(`${title} · Admin`);
  return (
    <div className="p-4 sm:p-6 lg:p-8">
      <PageHeader title={title} description={description} actions={actions} breadcrumbs={breadcrumbs} />
      {children}
    </div>
  );
}
