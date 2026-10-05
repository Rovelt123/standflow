import { useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import LandingPage from './pages/landing/LandingPage.jsx'
import ApplicationPage from './pages/ansoegning/ApplicationPage.jsx'
import KontrolpanelPage from './pages/kontrolpanel/KontrolpanelPage.jsx'
import ExhibitorsPage from './pages/public/ExhibitorsPage.jsx'
import ContactPage from './pages/public/ContactPage.jsx'
import AccessPage from './pages/public/AccessPage.jsx'

function App() {
  // Frontend demo only; real authentication must be enforced by the backend.
  const [demoAccess, setDemoAccess] = useState(false)
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/stadeholdere" element={<ExhibitorsPage />} />
        <Route path="/kontakt" element={<ContactPage />} />
        <Route path="/login" element={<AccessPage key="login" onContinue={() => setDemoAccess(true)} />} />
        <Route path="/registrer" element={<AccessPage key="register" register onContinue={() => setDemoAccess(true)} />} />
        <Route path="/ansoegning" element={demoAccess ? <ApplicationPage /> : <Navigate to="/login" replace />} />
        <Route path="/kontrolpanel" element={<KontrolpanelPage />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
