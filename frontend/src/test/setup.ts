// Setup global pour Vitest : on étend expect avec les matchers RTL et on nettoie
// l'état fetch entre chaque cas pour éviter les fuites entre tests.
import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

afterEach(() => {
  cleanup();
});
