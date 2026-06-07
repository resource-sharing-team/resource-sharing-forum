import React from 'react';
import ReactDOM from 'react-dom/client';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import App from './App';
import './styles.css';

async function enableMocking() {
  const enableMocks = import.meta.env.VITE_ENABLE_MOCKS === 'true';
  if (!enableMocks) return;
  const { worker } = await import('./mocks/browser');
  await worker.start({ onUnhandledRequest: 'bypass', quiet: true });
}

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 1000 * 60,
    },
  },
});

enableMocking().finally(() => {
  ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
      <ConfigProvider
        locale={zhCN}
        theme={{
          token: {
            colorPrimary: '#2d6a4f',
            colorInfo: '#2f5b8f',
            colorSuccess: '#40916c',
            colorWarning: '#d97706',
            colorError: '#c2410c',
            borderRadius: 8,
            fontFamily: '"HarmonyOS Sans SC", "Microsoft YaHei", "Noto Sans CJK SC", sans-serif',
          },
          components: {
            Button: { borderRadius: 7 },
            Card: { borderRadiusLG: 8 },
            Layout: { bodyBg: '#f5f7f4' },
          },
        }}
      >
        <QueryClientProvider client={queryClient}>
          <App />
        </QueryClientProvider>
      </ConfigProvider>
    </React.StrictMode>,
  );
});
