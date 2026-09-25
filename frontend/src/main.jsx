import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Route, Routes } from 'react-router'
import './index.css'
import App from './App.jsx'
import KontrolpanelPage from './pages/kontrolpanel/KontrolpanelPage.jsx'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<App />} />
        <Route path="/kontrolpanel" element={<KontrolpanelPage />} />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
)
