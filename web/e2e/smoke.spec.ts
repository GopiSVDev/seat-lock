import { expect, test } from '@playwright/test';
import { keycloakLogin, openFirstShow } from './helpers';

// Needs the stack running with shows scheduled (scripts/demo-data.mjs adds some).
test('find a city, pick seats, sign in, check out until Razorpay opens', async ({ page }) => {
  // a typo still finds the city
  await page.goto('/city');
  await page.getByRole('searchbox', { name: 'Search for your city' }).fill('chenai');
  await page.getByRole('button', { name: /^Chennai/ }).first().click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Movies in Chennai');

  await openFirstShow(page);

  // two seats wanted: one tap selects a pair side by side
  await page.getByRole('radio', { name: '2' }).click();
  await page.locator('button.seat[data-state="free"]').nth(20).click();
  await expect(page.locator('button.seat[data-state="picked"]')).toHaveCount(2);

  // continuing needs a sign-in; the selection survives the trip to Keycloak and carries on to checkout
  await page.getByRole('button', { name: 'Continue' }).click();
  await keycloakLogin(page, 'asha', 'asha');
  await expect(page.getByRole('heading', { name: 'Review your booking' })).toBeVisible();
  await expect(page.getByRole('timer')).toContainText(/(9|10):\d\d/);
  await page.getByRole('button', { name: /^Pay ₹/ }).click();

  // Razorpay's own window takes over from here (test mode)
  await expect(page.locator('iframe.razorpay-checkout-frame')).toBeVisible({ timeout: 30_000 });
});
