import { expect, test, type Browser, type Page } from '@playwright/test';
import { keycloakLogin, openFirstShow, returningVisitor } from './helpers';

// Regenerates the README screenshots: docker compose run --rm web-e2e npx playwright test --project screenshots
// Needs the demo data (scripts/demo-data.mjs) and fresh hold limits (it reserves a few times as asha and ravi).
const DIR = 'screenshots';
const CHENNAI = { id: 5, name: 'Chennai' };
const PHONE = { width: 390, height: 844 };
const DESKTOP = { width: 1440, height: 900 };

async function visitor(browser: Browser, viewport = DESKTOP) {
  const context = await browser.newContext({ viewport, deviceScaleFactor: 1 });
  const page = await context.newPage();
  await returningVisitor(page, CHENNAI);
  return page;
}

async function login(page: Page, user: string) {
  await page.goto('/bookings');
  await keycloakLogin(page, user, user);
}

const shot = (page: Page, name: string, fullPage = false) => page.screenshot({ path: `${DIR}/${name}.png`, fullPage });

test('browse', async ({ browser }) => {
  for (const [suffix, viewport] of [['desktop', DESKTOP], ['phone', PHONE]] as const) {
    const page = await visitor(browser, viewport);
    await page.goto('/');
    await expect(page.locator('.movie-card img').first()).toBeVisible();
    await page.waitForTimeout(800);
    await shot(page, `movies-${suffix}`);
    await page.locator('.movie-card').first().click();
    await expect(page.locator('.time').first()).toBeVisible();
    if (suffix === 'desktop') await shot(page, 'movie-desktop');
    await page.close();
  }
});

test('two buyers want the same seats', async ({ browser }) => {
  const ravi = await visitor(browser);
  const asha = await visitor(browser, PHONE);
  await login(ravi, 'ravi');
  await login(asha, 'asha');
  await ravi.goto('/');
  await openFirstShow(ravi, 1);
  await asha.goto(ravi.url());

  // both want F9 and F10; ravi continues first
  for (const page of [ravi, asha]) await page.getByRole('button', { name: /^F9, .*available/ }).click();
  await ravi.waitForTimeout(300);
  await shot(ravi, 'seats-desktop');
  await ravi.getByRole('button', { name: 'Continue' }).click();
  await expect(ravi.getByRole('button', { name: /^Pay ₹/ })).toBeVisible();
  await ravi.waitForTimeout(500);
  await shot(ravi, 'checkout-desktop');

  await asha.getByRole('button', { name: 'Continue' }).click();
  await expect(asha.getByText(/just booked by someone else/)).toBeVisible();
  await shot(asha, 'seats-taken-phone');

  await asha.getByRole('button', { name: /^C5, .*available/ }).click();
  await asha.getByRole('button', { name: 'Continue' }).click();
  await expect(asha.getByRole('button', { name: /^Pay ₹/ })).toBeVisible();
  await asha.waitForTimeout(500);
  await shot(asha, 'checkout-phone', true);
  // leaving checkout gives the seats back
  await asha.getByRole('link', { name: 'Change seats' }).click();
  await ravi.getByRole('link', { name: 'Change seats' }).click();
});

test('tickets', async ({ browser }) => {
  for (const [suffix, viewport] of [['desktop', DESKTOP], ['phone', PHONE]] as const) {
    const page = await visitor(browser, viewport);
    await login(page, 'ravi');
    await expect(page.getByRole('heading', { name: 'My bookings' })).toBeVisible();
    await page.locator('.booking-row, .state').first().waitFor();
    const first = page.locator('.booking-row').first();
    if (!(await first.isVisible())) test.skip(true, 'ravi has no paid bookings yet: pay once on the site');
    if (suffix === 'desktop') await shot(page, 'my-bookings-desktop');
    await first.click();
    await expect(page.locator('.ticket')).toBeVisible();
    await page.waitForTimeout(500);
    await shot(page, `ticket-${suffix}`, suffix === 'phone');
    const cancel = page.getByRole('button', { name: 'Cancel booking' });
    if (suffix === 'desktop' && (await cancel.isVisible())) {
      await cancel.click();
      await shot(page, 'cancel-dialog');
      await page.getByRole('button', { name: 'Keep booking' }).click();
    }
  }
});

test('admin', async ({ browser }) => {
  const page = await visitor(browser);
  await login(page, 'admin');
  await page.goto('/admin/screens');
  await page.getByRole('textbox', { name: 'Cinema' }).fill('sathyam');
  await page.getByRole('button', { name: /Sathyam Cinemas/ }).click();
  await page.getByRole('button', { name: 'Standard' }).click();
  await page.waitForTimeout(400);
  await shot(page, 'admin-screen-builder');
  await page.goto('/admin/movies');
  await page.getByRole('button', { name: 'Add movie' }).click();
  await expect(page.locator('.field-error').first()).toHaveText('Required');
});
