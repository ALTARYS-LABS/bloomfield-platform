import { useNavigate } from 'react-router-dom';

const techs = [
  { name: 'Spring Boot 4', desc: 'Backend Java 25' },
  { name: 'React 19', desc: 'Frontend TypeScript' },
  { name: 'WebSocket STOMP', desc: 'Temps réel' },
  { name: 'TradingView Charts', desc: 'Chandeliers' },
  { name: 'Docker', desc: 'Conteneurisation' },
  { name: 'Tailwind CSS 4', desc: 'Design System' },
];

const features = [
  { title: 'Cotations temps réel', desc: 'Flux WebSocket avec mise à jour automatique des prix BRVM' },
  { title: 'Graphiques interactifs', desc: 'Chandeliers japonais avec historique 30 jours et volume' },
  { title: 'Carnet d\'ordres', desc: 'Profondeur de marché bid/ask en temps réel' },
  { title: 'Widgets modulaires', desc: 'Interface drag & drop personnalisable' },
];

export default function LandingPage() {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen bg-landing-bg text-text-primary">
      {/* Header */}
      <header className="flex items-center justify-between px-4 md:px-8 py-3 md:py-4 border-b border-border">
        <img
          src="/bloomfield-logo.png"
          alt="Bloomfield"
          className="h-8 md:h-10"
        />
        <span className="text-[10px] md:text-xs text-text-secondary font-mono tracking-wider bg-altarys-orange/20 text-altarys-orange px-2 md:px-3 py-1 rounded-full">
          PROTOTYPE
        </span>
      </header>

      {/* Hero */}
      <section className="flex flex-col items-center justify-center py-16 md:py-32 px-4">
        <div className="inline-block px-3 md:px-4 py-1 mb-4 md:mb-6 rounded-full border border-accent/30 bg-accent/10 text-accent text-xs md:text-sm font-medium text-center">
          Module 1 — Opérations Boursières
        </div>
        <h1 className="text-3xl sm:text-4xl md:text-6xl font-bold tracking-tight mb-3 md:mb-4 bg-gradient-to-r from-text-primary to-text-secondary bg-clip-text text-transparent text-center">
          BLOOMFIELD TERMINAL
        </h1>
        <p className="text-sm md:text-xl text-text-secondary max-w-2xl text-center mb-6 md:mb-8 px-2">
          Plateforme de suivi des opérations boursières BRVM en temps réel.
          Données de marché, graphiques interactifs et carnet d'ordres.
        </p>
        <button
          onClick={() => navigate('/terminal')}
          className="px-6 md:px-8 py-2.5 md:py-3 bg-accent hover:bg-accent/80 text-white font-semibold rounded-lg transition-colors text-base md:text-lg cursor-pointer"
        >
          Accéder au Terminal &rarr;
        </button>
      </section>

      {/* Technologies */}
      <section className="max-w-5xl mx-auto px-4 py-10 md:py-16">
        <h2 className="text-xl md:text-2xl font-semibold text-center mb-6 md:mb-10">Stack Technique</h2>
        <div className="grid grid-cols-2 md:grid-cols-3 gap-3 md:gap-4">
          {techs.map(t => (
            <div key={t.name} className="bg-bg-widget border border-border rounded-lg p-3 md:p-5 text-center">
              <div className="text-text-primary font-semibold text-sm md:text-base">{t.name}</div>
              <div className="text-text-secondary text-xs md:text-sm mt-1">{t.desc}</div>
            </div>
          ))}
        </div>
      </section>

      {/* Features */}
      <section className="max-w-5xl mx-auto px-4 py-10 md:py-16">
        <h2 className="text-xl md:text-2xl font-semibold text-center mb-6 md:mb-10">Fonctionnalités</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 md:gap-6">
          {features.map(f => (
            <div key={f.title} className="bg-bg-widget border border-border rounded-lg p-4 md:p-6">
              <h3 className="text-base md:text-lg font-semibold text-accent mb-1 md:mb-2">{f.title}</h3>
              <p className="text-text-secondary text-xs md:text-sm">{f.desc}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-border py-6 md:py-8 text-center px-4">
        <p className="text-text-secondary text-xs md:text-sm mb-2">
          Groupement <span className="text-text-primary font-semibold">IBEMS</span> — <span className="text-altarys-orange font-semibold">ALTARYS LABS</span>
        </p>
        <p className="text-text-secondary text-[10px] md:text-xs">
          AO_BI_2026_001 — Prototype de démonstration — Données simulées
        </p>
      </footer>
    </div>
  );
}
