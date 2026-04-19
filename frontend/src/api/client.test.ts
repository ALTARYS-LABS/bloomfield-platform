import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiFetch, configureApiClient, __resetApiClientForTests } from './client';

// On isole le comportement critique du client : sur 401 il tente un unique refresh
// puis rejoue la requête. Deux 401 consécutifs => la session est déclarée perdue.

describe('apiFetch', () => {
  beforeEach(() => {
    __resetApiClientForTests();
    vi.restoreAllMocks();
  });

  it('joint le header Authorization quand un token est disponible', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response('{}', { status: 200 }));

    configureApiClient({
      getAccessToken: () => 'abc',
      refreshAccessToken: vi.fn().mockResolvedValue('zzz'),
      onUnauthorized: vi.fn(),
    });

    await apiFetch('/admin/users');

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const headers = new Headers(init.headers);
    expect(headers.get('Authorization')).toBe('Bearer abc');
  });

  it('rafraîchit puis rejoue la requête sur 401', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response('', { status: 401 }))
      .mockResolvedValueOnce(new Response('{}', { status: 200 }));

    const refresh = vi.fn().mockResolvedValue('new-token');
    const onUnauthorized = vi.fn();
    let currentToken = 'old-token';
    configureApiClient({
      getAccessToken: () => currentToken,
      refreshAccessToken: async () => {
        currentToken = await refresh();
        return currentToken;
      },
      onUnauthorized,
    });

    const res = await apiFetch('/admin/users');

    expect(res.status).toBe(200);
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    // Le second appel doit utiliser le nouveau token.
    const secondInit = fetchMock.mock.calls[1][1] as RequestInit;
    const headers = new Headers(secondInit.headers);
    expect(headers.get('Authorization')).toBe('Bearer new-token');
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('déclenche onUnauthorized si le rejoue renvoie encore 401', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response('', { status: 401 }))
      .mockResolvedValueOnce(new Response('', { status: 401 }));

    const onUnauthorized = vi.fn();
    configureApiClient({
      getAccessToken: () => 'tok',
      refreshAccessToken: vi.fn().mockResolvedValue('tok2'),
      onUnauthorized,
    });

    const res = await apiFetch('/admin/users');
    expect(res.status).toBe(401);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it('déclenche onUnauthorized si le refresh lui-même échoue', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 401 }));
    const onUnauthorized = vi.fn();
    configureApiClient({
      getAccessToken: () => 'tok',
      refreshAccessToken: vi.fn().mockRejectedValue(new Error('refresh fail')),
      onUnauthorized,
    });

    const res = await apiFetch('/admin/users');
    expect(res.status).toBe(401);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });
});
