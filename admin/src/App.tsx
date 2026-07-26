import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { RequireAuth } from './auth/RequireAuth';
import { AdminLayout } from './layouts/AdminLayout';
import { DashboardPage } from './pages/DashboardPage';
import { FramesPage } from './pages/FramesPage';
import { LoginPage } from './pages/LoginPage';
import { UsersPage } from './pages/UsersPage';

/** Router: /login public; các trang còn lại nằm sau RequireAuth + AdminLayout. */
const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        path: '/',
        element: <AdminLayout />,
        children: [
          { index: true, element: <DashboardPage /> },
          { path: 'users', element: <UsersPage /> },
          { path: 'frames', element: <FramesPage /> },
        ],
      },
    ],
  },
]);

export default function App() {
  return <RouterProvider router={router} />;
}
