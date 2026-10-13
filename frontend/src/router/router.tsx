import { createBrowserRouter } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import CategoriesPage from '../pages/CategoriesPage'
import DashboardPage from '../pages/DashboardPage'
import LoginPage from '../pages/LoginPage'
import NotFoundPage from '../pages/NotFoundPage'
import ProductFormPage from '../pages/ProductFormPage'
import PosPage from '../pages/PosPage'
import ProductListPage from '../pages/ProductListPage'
import ReceiptPage from '../pages/ReceiptPage'
import SaleDetailPage from '../pages/SaleDetailPage'
import SalesHistoryPage from '../pages/SalesHistoryPage'
import { ProtectedRoute, RequireRole } from './guards'

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    // standalone print view: no sidebar or top bar to hide
    path: '/receipt/:saleNumber',
    element: (
      <ProtectedRoute>
        <ReceiptPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/',
    element: (
      <ProtectedRoute>
        <AppLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <DashboardPage /> },
      { path: 'pos', element: <PosPage /> },
      { path: 'sales', element: <SalesHistoryPage /> },
      { path: 'sales/:id', element: <SaleDetailPage /> },
      { path: 'products', element: <ProductListPage /> },
      {
        path: 'products/new',
        element: (
          <RequireRole min="MANAGER">
            <ProductFormPage />
          </RequireRole>
        ),
      },
      {
        path: 'products/:id/edit',
        element: (
          <RequireRole min="MANAGER">
            <ProductFormPage />
          </RequireRole>
        ),
      },
      { path: 'categories', element: <CategoriesPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
