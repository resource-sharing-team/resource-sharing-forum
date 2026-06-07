import { demandComments, demands, profileSummary, resourceComments, resources, currentUser } from '../data/mockRecords';
import type { Demand, Resource, User } from '../types';

export const db = {
  user: { ...currentUser } as User,
  resources: resources.map((item) => ({ ...item })) as Resource[],
  demands: demands.map((item) => ({ ...item })) as Demand[],
  resourceComments: resourceComments.map((item) => ({ ...item })),
  demandComments: demandComments.map((item) => ({ ...item })),
  profileSummary,
};
