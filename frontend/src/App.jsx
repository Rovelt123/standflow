import { BrowserRouter, Route, Routes } from 'react-router-dom'
import LandingPage from './pages/landing/LandingPage.jsx'
import ApplicationPage from './pages/ansoegning/ApplicationPage.jsx'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/ansoegning" element={<ApplicationPage />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
