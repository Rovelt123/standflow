import { useSyncExternalStore } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import LandingPage from './pages/landing/LandingPage.jsx'
import ApplicationPage from './pages/ansoegning/ApplicationPage.jsx'
import KontrolpanelPage from './pages/kontrolpanel/KontrolpanelPage.jsx'
import ExhibitorsPage from './pages/public/ExhibitorsPage.jsx'
import ContactPage from './pages/public/ContactPage.jsx'
import AccessPage from './pages/public/AccessPage.jsx'
import { getAuthToken, subscribeAuth } from './pages/public/authApi.js'
import PublicHeader from './components/layout/PublicHeader.jsx'
import PortalPage from './pages/portal/PortalPage.jsx'

function App() {
  const authenticated = Boolean(useSyncExternalStore(subscribeAuth, getAuthToken, () => null))
  return (
    <BrowserRouter>
      <PublicHeader authenticated={authenticated} />
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/stadeholdere" element={<ExhibitorsPage />} />
        <Route path="/kontakt" element={<ContactPage />} />
        <Route path="/login" element={<AccessPage key="login" />} />
        <Route path="/registrer" element={<AccessPage key="register" register />} />
        <Route path="/ansoegning" element={authenticated ? <ApplicationPage key="new" /> : <Navigate to="/login" replace />} />
        <Route path="/brugerportal" element={authenticated ? <PortalPage /> : <Navigate to="/login" replace />} />
        <Route path="/brugerportal/ansoegninger/:id/rediger" element={authenticated ? <ApplicationPage key="edit" /> : <Navigate to="/login" replace />} />
        <Route path="/kontrolpanel" element={<KontrolpanelPage />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
