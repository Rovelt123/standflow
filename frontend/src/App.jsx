import { BrowserRouter, Route, Routes } from 'react-router-dom'
import LandingPage from './pages/landing/LandingPage.jsx'
import ApplicationPage from './pages/ansoegning/ApplicationPage.jsx'
import KontrolpanelPage from './pages/kontrolpanel/KontrolpanelPage.jsx'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/ansoegning" element={<ApplicationPage />} />
        <Route path="/kontrolpanel" element={<KontrolpanelPage />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
