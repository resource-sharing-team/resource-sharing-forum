import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import AppLayout from './components/AppLayout';
import AuthPage from './pages/AuthPage';
import DemandDetailPage from './pages/DemandDetailPage';
import DemandsPage from './pages/DemandsPage';
import HomePage from './pages/HomePage';
import NotFoundPage from './pages/NotFoundPage';
import ProfilePage from './pages/ProfilePage';
import PublishDemandPage from './pages/PublishDemandPage';
import PublishResourcePage from './pages/PublishResourcePage';
import ResourceDetailPage from './pages/ResourceDetailPage';
import ResourcesPage from './pages/ResourcesPage';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<AuthPage mode="login" />} />
        <Route path="/register" element={<AuthPage mode="register" />} />
        <Route path="/forgot-password" element={<AuthPage mode="forgot" />} />
        <Route element={<AppLayout />}>
          <Route index element={<HomePage />} />
          <Route path="resources" element={<ResourcesPage />} />
          <Route path="resources/:id" element={<ResourceDetailPage />} />
          <Route path="demands" element={<DemandsPage />} />
          <Route path="demands/:id" element={<DemandDetailPage />} />
          <Route path="publish-resource" element={<PublishResourcePage />} />
          <Route path="publish-demand" element={<PublishDemandPage />} />
          <Route path="profile" element={<ProfilePage />} />
          <Route path="user-center" element={<Navigate to="/profile" replace />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
