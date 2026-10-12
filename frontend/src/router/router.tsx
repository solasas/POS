import { createBrowserRouter } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import CategoriesPage from '../pages/CategoriesPage'
import DashboardPage from '../pages/DashboardPage'
import LoginPage from '../pages/LoginPage'
import NotFoundPage from '../pages/NotFoundPage'
import ProductFormPage from '../pages/ProductFormPage'
import ProductListPage from '../pages/ProductListPage'
import { ProtectedRoute, RequireRole } from './guards'

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: (
      <ProtectedRoute>
        <AppLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <DashboardPage /> },
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
