import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as endpoints from './endpoints';
import type { ListParams, User } from '../types';

export function useMe(enabled = true) {
  return useQuery({ queryKey: ['me'], queryFn: endpoints.getMe, enabled });
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

export function useCategories() {
  return useQuery({ queryKey: ['categories'], queryFn: endpoints.getCategories });
}

export function useResourceTypes() {
  return useQuery({ queryKey: ['resource-types'], queryFn: endpoints.getResourceTypes });
}

export function useAnnouncements(params: { page?: number; pageSize?: number } = {}) {
  return useQuery({ queryKey: ['announcements', params], queryFn: () => endpoints.getAnnouncements(params) });
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
      queryClient.invalidateQueries({ queryKey: ['user-favorites'] });
      queryClient.invalidateQueries({ queryKey: ['user-likes'] });
    },
  });
}

export function useDownloadAttachment() {
  return useMutation({ mutationFn: (attachmentId: number) => endpoints.downloadAttachment(attachmentId) });
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

export function useCancelDemand() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => endpoints.cancelDemand(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['demands'] });
      queryClient.invalidateQueries({ queryKey: ['user-requests'] });
      queryClient.invalidateQueries({ queryKey: ['profile-summary'] });
    },
  });
}

export function useAddComment(kind: 'resources' | 'demands', id: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (values: { content: string; parentId?: number }) => endpoints.addComment(kind, id, values),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [kind === 'resources' ? 'resource' : 'demand', String(id)] });
      if (kind === 'demands') {
        queryClient.invalidateQueries({ queryKey: ['demands'] });
        queryClient.invalidateQueries({ queryKey: ['profile-summary'] });
      }
    },
  });
}

export function useDeleteComment(kind: 'resources' | 'demands', id: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (commentId: number) => endpoints.deleteComment(commentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [kind === 'resources' ? 'resource' : 'demand', String(id)] });
    },
  });
}

export function useReportContent() {
  return useMutation({ mutationFn: endpoints.reportContent });
}

export function useUserResources(enabled = true) {
  return useQuery({ queryKey: ['user-resources'], queryFn: () => endpoints.getUserResources({ page: 1, pageSize: 20 }), enabled });
}

export function useUserRequests(enabled = true) {
  return useQuery({ queryKey: ['user-requests'], queryFn: () => endpoints.getUserRequests({ page: 1, pageSize: 20 }), enabled });
}

export function useUserFavorites(enabled = true) {
  return useQuery({ queryKey: ['user-favorites'], queryFn: () => endpoints.getUserFavorites({ page: 1, pageSize: 20 }), enabled });
}

export function useUserLikes(enabled = true) {
  return useQuery({ queryKey: ['user-likes'], queryFn: () => endpoints.getUserLikes({ page: 1, pageSize: 20 }), enabled });
}

export function useUserLoginRecords(enabled = true) {
  return useQuery({ queryKey: ['user-login-records'], queryFn: () => endpoints.getUserLoginRecords({ page: 1, pageSize: 20 }), enabled });
}

export function useNotifications(enabled = true) {
  return useQuery({ queryKey: ['notifications'], queryFn: () => endpoints.getNotificationMessages({ page: 1, pageSize: 20 }), enabled });
}

export function useMarkNotificationRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: endpoints.markNotificationRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  });
}

export function useMarkAllNotificationsRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: endpoints.markAllNotificationsRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  });
}
