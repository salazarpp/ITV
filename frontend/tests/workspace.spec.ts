import { expect, test, type Page } from '@playwright/test';

const pokemon = { id: 1, name: 'bulbasaur', sprite: null, category: 'Seed Pokemon', mass: 6.9, skills: ['overgrow', 'chlorophyll'] };
const detail = { ...pokemon, image: null, description: 'A strange seed was planted on its back at birth.', stats: [{ name: 'hp', value: 45 }], evolution: [{ id: 1, name: 'bulbasaur' }, { id: 2, name: 'ivysaur' }] };
type Saved = { id: string; pokeApiId: number; name: string; image: null; customName: string; region: string; internalClassification: string };

async function mockWorkspace(page: Page) {
  let saved: Saved | undefined;
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url());
    const method = route.request().method();
    const path = url.pathname;
    const respond = (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    if (path === '/api/v1/pokemon') return respond({ count: 1, results: [pokemon] });
    if (path === '/api/v1/pokemon/1') return respond(detail);
    if (path === '/api/v1/pokemon/2') return respond({ ...detail, id: 2, name: 'ivysaur' });
    expect(route.request().headers().authorization).toBe('Bearer browser-test-token');
    if (path === '/api/v1/local-pokemon' && method === 'GET') return respond({ count: saved ? 1 : 0, results: saved ? [saved] : [] });
    if (path === '/api/v1/local-pokemon' && method === 'POST') {
      saved = { id: '00000000-0000-0000-0000-000000000001', name: 'bulbasaur', image: null, ...route.request().postDataJSON() } as Saved;
      return respond(saved, 201);
    }
    if (path.endsWith('/00000000-0000-0000-0000-000000000001') && saved) {
      if (method === 'GET') return respond(saved);
      if (method === 'PUT') { saved = { ...saved, ...route.request().postDataJSON() } as Saved; return respond(saved); }
      if (method === 'DELETE') { saved = undefined; return route.fulfill({ status: 204 }); }
    }
    return respond({ message: 'Fixture endpoint not found.', requestId: 'fixture-404' }, 404);
  });
  await page.route('**/auth/**', async route => {
    const path = new URL(route.request().url()).pathname;
    expect(route.request().postDataJSON()).toMatchObject({ username: 'trainer', password: 'browser-fixture-password' });
    if (path === '/auth/register') return route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({ username: 'trainer', email: 'trainer@example.test' }) });
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ accessToken: 'browser-test-token', tokenType: 'Bearer', expiresIn: 3600 }) });
  });
}

test('browse, register, authenticate, synchronize, update and delete a Pokemon', async ({ page }) => {
  await mockWorkspace(page);
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Discover Pokemon' })).toBeVisible();
  await page.getByRole('button', { name: 'View bulbasaur' }).click();
  await expect(page.getByText(detail.description)).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Core statistics' })).toBeVisible();
  await page.getByRole('button', { name: 'Sign in to synchronize' }).click();
  await page.getByRole('button', { name: 'New here? Create an account' }).click();
  await page.getByLabel('Username').fill('trainer');
  await page.getByLabel('Email', { exact: true }).fill('trainer@example.test');
  await page.getByLabel('Password').fill('browser-fixture-password');
  await page.getByRole('button', { name: 'Create account', exact: true }).click();
  await expect(page.getByText('Account created. Sign in to start your collection.')).toBeVisible();
  await page.getByRole('button', { name: 'Sign in', exact: true }).last().click();
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible();
  await page.getByLabel('Custom name').fill('Leaf');
  await page.getByLabel('Region').fill('Kanto');
  await page.getByLabel('Classification').fill('Starter');
  await page.getByRole('button', { name: 'Synchronize Pokemon' }).click();
  await expect(page.getByRole('heading', { name: 'Local collection' })).toBeVisible();
  await page.getByRole('button', { name: 'Edit Leaf' }).click();
  await page.getByLabel('Custom name').fill('Leaf Prime');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByRole('button', { name: 'Edit Leaf Prime' })).toBeVisible();
  await page.getByRole('button', { name: 'Remove Pokemon', exact: true }).click();
  await page.getByRole('button', { name: 'Confirm removal' }).click();
  await expect(page.getByRole('heading', { name: 'Your collection starts here' })).toBeVisible();
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page.getByRole('heading', { name: 'Discover Pokemon' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test('shows an upstream error reference and recovers after retry', async ({ page }) => {
  let failed = true;
  await page.route('**/api/v1/pokemon?*', route => route.fulfill({
    status: failed ? 502 : 200,
    contentType: 'application/json',
    body: JSON.stringify(failed ? { message: 'Pokemon provider is temporarily unavailable.', requestId: 'support-reference-001' } : { count: 1, results: [pokemon] }),
  }));
  await page.goto('/');
  await expect(page.getByRole('alert')).toContainText('support-reference-001');
  failed = false;
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(page.getByRole('button', { name: 'View bulbasaur' })).toBeVisible();
  await expect(page.getByRole('alert')).toHaveCount(0);
});

test('requires sign in for collection and clears an invalid session', async ({ page }) => {
  await mockWorkspace(page);
  await page.goto('/');
  await page.getByRole('button', { name: 'Collection', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
  await page.getByLabel('Username').fill('trainer');
  await page.getByLabel('Password').fill('browser-fixture-password');
  await page.getByRole('button', { name: 'Sign in', exact: true }).last().click();
  await page.route('**/api/v1/local-pokemon?*', route => route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ message: 'Authentication required.', requestId: 'expired-session-001' }) }));
  await page.getByRole('button', { name: 'Collection', exact: true }).click();
  await expect(page.getByText('Your session is no longer valid. Sign in again to manage your collection.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Sign out' })).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'Discover Pokemon' })).toBeVisible();
});
