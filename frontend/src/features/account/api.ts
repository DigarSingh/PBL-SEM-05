import { useQuery } from '@tanstack/react-query';
import { useSession } from '@/features/auth/useSession';
import { apiDelete, apiGet, apiPatch, apiPost, apiPostEnvelope, apiPut } from '@/lib/api';
import type { Address, UserProfile } from '@/lib/types';

export interface AddressInput {
  label: string | null;
  fullName: string;
  phone: string;
  line1: string;
  line2: string | null;
  landmark: string | null;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  isDefault: boolean;
}

export const ADDRESSES_KEY = ['me', 'addresses'] as const;

export const accountApi = {
  profile: () => apiGet<UserProfile>('/users/me'),
  updateProfile: (input: { fullName: string; phone: string | null }) => apiPatch<UserProfile>('/users/me', input),
  changePassword: (input: { currentPassword: string; newPassword: string }) => apiPostEnvelope<void>('/users/me/password', input),
  addresses: () => apiGet<Address[]>('/users/me/addresses'),
  createAddress: (input: AddressInput) => apiPost<Address>('/users/me/addresses', input),
  updateAddress: (addressId: number, input: AddressInput) => apiPut<Address>(`/users/me/addresses/${addressId}`, input),
  makeDefault: (addressId: number) => apiPost<Address>(`/users/me/addresses/${addressId}/default`),
  deleteAddress: (addressId: number) => apiDelete<void>(`/users/me/addresses/${addressId}`),
};

export function useAddresses() {
  const { status } = useSession();
  return useQuery({ queryKey: ADDRESSES_KEY, queryFn: accountApi.addresses, enabled: status === 'authenticated' });
}
