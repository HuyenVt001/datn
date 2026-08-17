import { Button, Result } from 'antd';
import { createBrowserRouter, RouterProvider, useRouteError } from 'react-router-dom';
import { RequireAuth } from './auth/RequireAuth';
import { AdminLayout } from './layouts/AdminLayout';
import { DashboardPage } from './pages/DashboardPage';
import { FramesPage } from './pages/FramesPage';
import { GachaHistoryPage } from './pages/GachaHistoryPage';
import { GachaItemsPage } from './pages/GachaItemsPage';
import { LoginPage } from './pages/LoginPage';
import { AiVerificationsPage } from './pages/AiVerificationsPage';
import { LogsPage } from './pages/LogsPage';
import { MomentsPage } from './pages/MomentsPage';
import { TopupHistoryPage } from './pages/TopupHistoryPage';
import { TopupPackagesPage } from './pages/TopupPackagesPage';
import { UsersPage } from './pages/UsersPage';

/** Man hinh loi chung — tranh "trang trang" khi 1 trang render loi (errorElement). */
function RouteErrorPage() {
  const error = useRouteError();
  const message = error instanceof Error ? error.message : 'Đã có lỗi không mong muốn.';
  return (
    <Result
      status="error"
      title="Trang gặp lỗi khi hiển thị"
      subTitle={message}
      extra={
        <Button type="primary" onClick={() => window.location.assign('/')}>
          Về trang Tổng quan
        </Button>
      }
    />
  );
}

/** Router: /login public; các trang còn lại nằm sau RequireAuth + AdminLayout. */
const router = createBrowserRouter([
  { path: '/login', element: <LoginPage />, errorElement: <RouteErrorPage /> },
  {
    element: <RequireAuth />,
    errorElement: <RouteErrorPage />,
    children: [
      {
        path: '/',
        element: <AdminLayout />,
        children: [
          { index: true, element: <DashboardPage /> },
          { path: 'users', element: <UsersPage /> },
          { path: 'moments', element: <MomentsPage /> },
          { path: 'frames', element: <FramesPage /> },
          { path: 'gacha', element: <GachaItemsPage /> },
          { path: 'gacha-history', element: <GachaHistoryPage /> },
          { path: 'topup', element: <TopupPackagesPage /> },
          { path: 'topup-history', element: <TopupHistoryPage /> },
          { path: 'ai-verifications', element: <AiVerificationsPage /> },
          { path: 'logs', element: <LogsPage /> },
        ],
      },
    ],
  },
]);

export default function App() {
  return <RouterProvider router={router} />;
}
