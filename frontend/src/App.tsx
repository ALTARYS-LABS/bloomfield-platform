import { BrowserRouter, Routes, Route } from 'react-router-dom';
import LandingPage from './pages/LandingPage';
import Terminal from './pages/Terminal';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/terminal" element={<Terminal />} />
      </Routes>
    </BrowserRouter>
  );
}
