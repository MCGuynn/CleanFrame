// Renders CleanFrame's white SVG marks to PNG for Roku (which can't draw SVG).
// Uses the same optical sizing as main.js markHtml(): node tools/marks.mjs <out dir>
import { chromium } from 'playwright';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
const root = new URL('../../app/assets/icons/', import.meta.url);
const metrics = JSON.parse(readFileSync(new URL('metrics.js', root), 'utf8').replace(/^[\s\S]*?=\s*/, '').replace(/;\s*$/, ''));
const ids = ['youtube', 'netflix', 'disneyplus', 'primevideo', 'appletv', 'hbomax', 'tubi', 'spotify', 'plex', 'crunchyroll', 'fubo', 'stremio', 'applemusic'];
const out = process.argv[2]; mkdirSync(out, { recursive: true });
const W = 240, H = 144, SIZE = 128; // drawn at 2x, shown at 120x72 like the Google TV dock
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: W, height: H } });
for (const id of ids) {
  const m = metrics[id], svg = readFileSync(new URL(id + '.svg', root), 'utf8');
  const weight = Math.min(1.25, Math.max(0.84, Math.pow(0.45 / m.fill, 0.3)));
  const k = Math.min(W / m.w, H / m.h, SIZE * weight / Math.sqrt(m.w * m.h)), s = m.n * k;
  const left = W / 2 - (m.x + m.w / 2) * k, top = H / 2 - (m.y + m.h / 2) * k;
  await page.setContent(`<html><body style="margin:0;background:transparent"><img style="position:absolute;left:${left}px;top:${top}px;width:${s}px;height:${s}px" src="data:image/svg+xml;base64,${Buffer.from(svg).toString('base64')}"></body></html>`);
  await page.waitForTimeout(50);
  writeFileSync(out + '/' + id + '.png', await page.screenshot({ omitBackground: true }));
}
await browser.close();
console.log('marks:', ids.length);
