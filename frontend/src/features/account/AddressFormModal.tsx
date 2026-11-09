import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Dialog';
import { InlineAlert } from '@/components/ui/Feedback';
import { Checkbox, FormField, Input, Select } from '@/components/ui/Field';
import { errorMessage } from '@/lib/errors';
import { applyServerErrors, emptyToNull, PHONE_PATTERN, PIN_CODE_PATTERN } from '@/lib/forms';
import { INDIAN_STATES } from '@/lib/india';
import type { Address } from '@/lib/types';
import { accountApi, ADDRESSES_KEY, type AddressInput } from './api';

const addressSchema = z.object({
  label: z.string().trim().max(30, 'Keep the label under 30 characters'),
  fullName: z.string().trim().min(2, "Enter the recipient's name").max(120, 'Name is too long'),
  phone: z.string().trim().regex(PHONE_PATTERN, 'Enter a valid phone number'),
  line1: z.string().trim().min(3, 'Enter the house number and street').max(200, 'Address line is too long'),
  line2: z.string().trim().max(200, 'Address line is too long'),
  landmark: z.string().trim().max(120, 'Landmark is too long'),
  city: z.string().trim().min(2, 'Enter the city').max(80, 'City is too long'),
  state: z.string().trim().min(2, 'Choose the state').max(80),
  postalCode: z.string().trim().regex(PIN_CODE_PATTERN, 'Enter a valid 6-digit PIN code'),
  isDefault: z.boolean(),
});
type AddressValues = z.output<typeof addressSchema>;

const FIELDS = ['label', 'fullName', 'phone', 'line1', 'line2', 'landmark', 'city', 'state', 'postalCode'];

export function AddressFormModal({ open, onClose, address, defaultName, defaultPhone, onSaved }: {
  open: boolean;
  onClose: () => void;
  address?: Address | null;
  defaultName?: string;
  defaultPhone?: string | null;
  onSaved?: (address: Address) => void;
}) {
  const queryClient = useQueryClient();
  const { register, handleSubmit, setError, formState: { errors } } = useForm({
    resolver: zodResolver(addressSchema),
    defaultValues: {
      label: address?.label ?? '',
      fullName: address?.fullName ?? defaultName ?? '',
      phone: address?.phone ?? defaultPhone ?? '',
      line1: address?.line1 ?? '',
      line2: address?.line2 ?? '',
      landmark: address?.landmark ?? '',
      city: address?.city ?? '',
      state: address?.state ?? '',
      postalCode: address?.postalCode ?? '',
      isDefault: address?.isDefault ?? false,
    },
  });

  const save = useMutation({
    mutationFn: (values: AddressValues) => {
      const input: AddressInput = {
        label: emptyToNull(values.label),
        fullName: values.fullName,
        phone: values.phone,
        line1: values.line1,
        line2: emptyToNull(values.line2),
        landmark: emptyToNull(values.landmark),
        city: values.city,
        state: values.state,
        postalCode: values.postalCode,
        country: 'IN',
        isDefault: values.isDefault,
      };
      return address ? accountApi.updateAddress(address.id, input) : accountApi.createAddress(input);
    },
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: ADDRESSES_KEY });
      toast.success(address ? 'Address updated' : 'Address saved');
      onSaved?.(saved);
      onClose();
    },
    onError: (error) => {
      applyServerErrors(error, setError, FIELDS);
    },
  });

  return (
    <Modal open={open} onClose={onClose} size="lg" title={address ? 'Edit address' : 'Add a delivery address'}
      footer={<>
        <Button variant="secondary" onClick={onClose} disabled={save.isPending}>Cancel</Button>
        <Button type="submit" form="address-form" loading={save.isPending}>{address ? 'Save address' : 'Add address'}</Button>
      </>}>
      <form id="address-form" noValidate onSubmit={handleSubmit((values) => save.mutate(values))} className="grid gap-4 sm:grid-cols-2">
        {save.isError && Object.keys(errors).length === 0 && (
          <InlineAlert tone="danger" className="sm:col-span-2">{errorMessage(save.error)}</InlineAlert>
        )}
        <FormField label="Full name" error={errors.fullName?.message} required>
          {(field) => <Input {...field} autoComplete="name" {...register('fullName')} />}
        </FormField>
        <FormField label="Mobile number" error={errors.phone?.message} required>
          {(field) => <Input {...field} type="tel" inputMode="tel" autoComplete="tel" {...register('phone')} />}
        </FormField>
        <FormField label="House no., building, street" error={errors.line1?.message} required className="sm:col-span-2">
          {(field) => <Input {...field} autoComplete="address-line1" {...register('line1')} />}
        </FormField>
        <FormField label="Area, locality" error={errors.line2?.message} className="sm:col-span-2">
          {(field) => <Input {...field} autoComplete="address-line2" {...register('line2')} />}
        </FormField>
        <FormField label="Landmark" error={errors.landmark?.message} hint="Optional">
          {(field) => <Input {...field} {...register('landmark')} />}
        </FormField>
        <FormField label="PIN code" error={errors.postalCode?.message} required>
          {(field) => <Input {...field} inputMode="numeric" maxLength={6} autoComplete="postal-code" {...register('postalCode')} />}
        </FormField>
        <FormField label="City" error={errors.city?.message} required>
          {(field) => <Input {...field} autoComplete="address-level2" {...register('city')} />}
        </FormField>
        <FormField label="State" error={errors.state?.message} required>
          {(field) => (
            <Select {...field} autoComplete="address-level1" {...register('state')}>
              <option value="">Select a state</option>
              {INDIAN_STATES.map((state) => <option key={state} value={state}>{state}</option>)}
            </Select>
          )}
        </FormField>
        <FormField label="Label" error={errors.label?.message} hint='e.g. "Home" or "Office"'>
          {(field) => <Input {...field} {...register('label')} />}
        </FormField>
        <div className="flex items-end pb-2">
          <Checkbox label="Use as my default address" {...register('isDefault')} />
        </div>
      </form>
    </Modal>
  );
}
