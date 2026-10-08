import { useSyncExternalStore } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import LandingPage from './pages/landing/LandingPage.jsx'
import ApplicationPage from './pages/ansoegning/ApplicationPage.jsx'
import KontrolpanelPage from './pages/kontrolpanel/KontrolpanelPage.jsx'
import StadeholderePage from './pages/kontrolpanel/StadeholderePage.jsx'
import ExhibitorsPage from './pages/public/ExhibitorsPage.jsx'
import AccessPage from './pages/public/AccessPage.jsx'
import { getAuthToken, subscribeAuth } from './pages/public/authApi.js'
import PublicHeader from './components/layout/PublicHeader.jsx'
import PortalPage from './pages/portal/PortalPage.jsx'
import MessagesPage from './pages/kontrolpanel/MessagesPage.jsx'
import NewsletterPage from './pages/kontrolpanel/NewsletterPage.jsx'
import UnsubscribePage from './pages/public/UnsubscribePage.jsx'
import RequireAdmin from './components/admin/RequireAdmin.jsx'

function App() {
  const authenticated = Boolean(useSyncExternalStore(subscribeAuth, getAuthToken, () => null))
  return (
    <BrowserRouter>
      <PublicHeader authenticated={authenticated} />
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/stadeholdere" element={<ExhibitorsPage />} />
        <Route path="/kontakt" element={<Navigate to={authenticated ? '/brugerportal?tab=messages' : '/login'} replace />} />
        <Route path="/unsubscribe" element={<UnsubscribePage />} />
        <Route path="/login" element={<AccessPage key="login" />} />
        <Route path="/registrer" element={<AccessPage key="register" register />} />
        <Route path="/ansoegning" element={authenticated ? <ApplicationPage key="new" /> : <Navigate to="/login" replace />} />
        <Route path="/brugerportal" element={ <PortalPage />} />
        <Route path="/brugerportal/ansoegninger/:id/rediger" element={authenticated ? <ApplicationPage key="edit" /> : <Navigate to="/login" replace />} />
        <Route path="/kontrolpanel" element={authenticated ? (<RequireAdmin> <KontrolpanelPage /> </RequireAdmin>) : (<Navigate to="/login" replace />)}/>
        <Route path="/kontrolpanel/stadeholdere" element={authenticated ? (<RequireAdmin> <StadeholderePage /> </RequireAdmin>) : (<Navigate to="/login" replace />)}/>
        <Route path="/kontrolpanel/beskeder" element={authenticated ? (<RequireAdmin> <MessagesPage /> </RequireAdmin>) : (<Navigate to="/login" replace />)}/>
        <Route path="/kontrolpanel/nyhedsbrev" element={authenticated ? (<RequireAdmin> <NewsletterPage /> </RequireAdmin>) : (<Navigate to="/login" replace />)}/>
     </Routes>
    </BrowserRouter>
  )
}

export default App
