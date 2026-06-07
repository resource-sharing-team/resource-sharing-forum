import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as endpoints from './endpoints';
import type { ListParams, User } from '../types';

export function useMe() {
  return useQuery({ queryKey: ['me'], queryFn: endpoints.getMe });
}

export function useProfileSummary() {
  return useQuery({ queryKey: ['profile-summary'], queryFn: endpoints.getProfileSummary });
}

export function useUpdateMe() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (values: Partial<User>) => endpoints.updateMe(values),
    onSuccess: (user) => {
      queryClient.setQueryData(['me'], user);
      queryClient.invalidateQueries({ queryKey: ['profile-summary'] });
    },
  });
}

export function useChangePassword() {
  return useMutation({ mutationFn: endpoints.changePassword });
}

export function useBindEmail() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: endpoints.bindEmail,
    onSuccess: (user) => {
      queryClient.setQueryData(['me'], user);
      queryClient.invalidateQueries({ queryKey: ['profile-summary'] });
    },
  });
}

export function useResources(params: ListParams) {
  return useQuery({ queryKey: ['resources', params], queryFn: () => endpoints.getResources(params) });
}

export function useResource(id?: string) {
  return useQuery({
    queryKey: ['resource', id],
    queryFn: () => endpoints.getResource(id as string),
    enabled: Boolean(id),
  });
}

export function usePublishResource() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: endpoints.publishResource,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['resources'] }),
  });
}

export function useResourceAction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, action, attachmentId }: { id: number; action: 'like' | 'favorite' | 'download'; attachmentId?: number }) =>
      endpoints.toggleResourceAction(id, action, attachmentId),
    onSuccess: (resource) => {
      queryClient.invalidateQueries({ queryKey: ['resources'] });
      queryClient.invalidateQueries({ queryKey: ['profile-summary'] });
      queryClient.invalidateQueries({ queryKey: ['resource', String(resource.id)] });
    },
  });
}

export function useRateResource() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, score }: { id: number; score: number }) => endpoints.rateResource(id, score),
    onSuccess: (resource) => {
      queryClient.invalidateQueries({ queryKey: ['resources'] });
      queryClient.invalidateQueries({ queryKey: ['resource', String(resource.id)] });
    },
  });
}

export function useDemands(params: ListParams) {
  return useQuery({ queryKey: ['demands', params], queryFn: () => endpoints.getDemands(params) });
}

export function useDemand(id?: string) {
  return useQuery({
    queryKey: ['demand', id],
    queryFn: () => endpoints.getDemand(id as string),
    enabled: Boolean(id),
  });
}

export function usePublishDemand() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: endpoints.publishDemand,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['demands'] }),
  });
}

export function useAddComment(kind: 'resources' | 'demands', id: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (content: string) => endpoints.addComment(kind, id, content),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [kind === 'resources' ? 'resource' : 'demand', String(id)] });
    },
  });
}

export function useReportContent() {
  return useMutation({ mutationFn: endpoints.reportContent });
}
