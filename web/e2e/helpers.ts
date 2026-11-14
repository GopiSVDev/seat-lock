import { expect, type Page } from '@playwright/test';

/** Signs in on Keycloak's page (the site sent us there) and waits to be back on the site. */
export async function keycloakLogin(page: Page, username: string, password: string) {
  await page.waitForURL(/\/realms\/seatlock\//);
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('#kc-login').click();
  await page.waitForURL((url) => !url.pathname.includes('/realms/') && !url.pathname.startsWith('/auth/callback'));
}

/** Starts with a city already chosen, as a returning visitor would. */
export async function returningVisitor(page: Page, city: { id: number; name: string } | null = null) {
  if (city) await page.addInitScript((c) => localStorage.setItem('seatlock.city', JSON.stringify(c)), city);
}

/** Opens the first upcoming show of the nth film on the home page. */
export async function openFirstShow(page: Page, film = 0) {
  await page.locator('.movie-card').nth(film).click();
  const later = page.getByRole('button', { name: /^See shows on / });
  await expect(page.locator('.time').first().or(later)).toBeVisible();
  if (await later.isVisible()) await later.click();
  await page.locator('.time').first().click();
  await expect(page).toHaveURL(/\/shows\/\d+/);
  await expect(page.locator('button.seat').first()).toBeVisible();
}
