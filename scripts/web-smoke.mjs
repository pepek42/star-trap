#!/usr/bin/env node
// Web (TeaVM) smoke test: serve the built webapp, open it in headless Firefox via
// WebDriver BiDi, wait, print console errors/logs and save a screenshot.
// Zero npm dependencies (Node >= 22 for the global WebSocket).
//
// Usage: node scripts/web-smoke.mjs [--dir=teavm/build/dist/js/webapp] [--wait=15]
//                                   [--out=/abs/path/web.png] [--query=...] [--click=x,y]
// Exit code 0 = page ran with no JS errors, 1 = JS errors / no canvas, 2 = harness failure.
import { spawn } from 'node:child_process';
import { createServer } from 'node:http';
import { readFile, writeFile, mkdtemp, rm } from 'node:fs/promises';
import { extname, join, resolve, normalize } from 'node:path';
import { tmpdir } from 'node:os';

const args = Object.fromEntries(process.argv.slice(2).map(a => {
    const [k, ...v] = a.replace(/^--/, '').split('=');
    return [k, v.length ? v.join('=') : 'true'];
}));
const dir = resolve(args.dir ?? 'teavm/build/dist/js/webapp');
const waitS = Number(args.wait ?? 15);
const out = resolve(args.out ?? join(tmpdir(), 'star-trap-web.png'));
const query = args.query ? `?${args.query}` : '';

const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.wasm': 'application/wasm',
    '.png': 'image/png', '.json': 'application/json', '.atlas': 'text/plain', '.txt': 'text/plain',
    '.css': 'text/css', '.ttf': 'font/ttf', '.glsl': 'text/plain', '.vert': 'text/plain', '.frag': 'text/plain' };

const server = createServer(async (req, res) => {
    const p = normalize(decodeURIComponent(new URL(req.url, 'http://x').pathname));
    const file = join(dir, p.endsWith('/') ? p + 'index.html' : p);
    if (!file.startsWith(dir)) { res.writeHead(403).end(); return; }
    try {
        const body = await readFile(file);
        res.writeHead(200, { 'Content-Type': MIME[extname(file)] ?? 'application/octet-stream' }).end(body);
    } catch { res.writeHead(404).end(); }
}).listen(0, '127.0.0.1');
await new Promise(r => server.once('listening', r));
const httpPort = server.address().port;

const profile = await mkdtemp(join(tmpdir(), 'st-ff-'));
const bidiPort = 9300 + Math.floor(Math.random() * 500);
const ff = spawn('firefox', ['--headless', '--no-remote', '--profile', profile,
    '--remote-debugging-port', String(bidiPort), '--window-size=1280,800'], { stdio: 'ignore' });

let exit = 2;
const logs = [];
try {
    let ws;
    for (let i = 0; i < 60 && !ws; i++) {
        await new Promise(r => setTimeout(r, 500));
        try {
            const s = new WebSocket(`ws://127.0.0.1:${bidiPort}/session`);
            await new Promise((ok, ko) => { s.onopen = ok; s.onerror = ko; });
            ws = s;
        } catch { /* firefox still starting */ }
    }
    if (!ws) throw new Error('Firefox BiDi did not come up');

    let id = 0;
    const pending = new Map();
    ws.onmessage = ev => {
        const m = JSON.parse(ev.data);
        if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return; }
        if (m.method === 'log.entryAdded') {
            const e = m.params;
            logs.push({ level: e.level, text: e.text ?? '', stack: e.stackTrace?.callFrames?.[0] });
        }
    };
    const send = (method, params = {}) => new Promise((ok, ko) => {
        const mid = ++id;
        pending.set(mid, m => m.type === 'error' ? ko(new Error(`${method}: ${m.error} ${m.message}`)) : ok(m.result));
        ws.send(JSON.stringify({ id: mid, method, params }));
    });

    await send('session.new', { capabilities: {} });
    await send('session.subscribe', { events: ['log.entryAdded'] });
    const { contexts } = await send('browsingContext.getTree', {});
    const context = contexts[0].context;
    await send('browsingContext.navigate', { context, url: `http://127.0.0.1:${httpPort}/index.html${query}`, wait: 'complete' });
    await new Promise(r => setTimeout(r, waitS * 1000));

    if (args.click) {
        const [x, y] = args.click.split(',').map(Number);
        await send('input.performActions', { context, actions: [{ type: 'pointer', id: 'm', actions: [
            { type: 'pointerMove', x, y }, { type: 'pointerDown', button: 0 }, { type: 'pointerUp', button: 0 }] }] });
        await new Promise(r => setTimeout(r, 3000));
    }

    const probe = await send('script.evaluate', { target: { context }, awaitPromise: false,
        expression: `(() => { const c = document.getElementById('canvas'); return c ? c.width + 'x' + c.height : 'none'; })()` });
    const canvas = probe.result?.value ?? 'none';
    const shot = await send('browsingContext.captureScreenshot', { context });
    await writeFile(out, Buffer.from(shot.data, 'base64'));

    // TeaVM reports uncaught Java exceptions at info level ("Fatal Error: ...", "Exception N: ...").
    const errors = logs.filter(l => l.level === 'error' || /Fatal Error|Exception \d+:|Uncaught/.test(l.text));
    for (const l of logs) console.log(`[${l.level}] ${l.text}${l.stack ? `  @${l.stack.url}:${l.stack.lineNumber}` : ''}`);
    console.log(`CANVAS: ${canvas}`);
    console.log(`SCREENSHOT: ${out}`);
    console.log(`RESULT: ${errors.length === 0 && canvas !== 'none' ? 'PASS' : 'FAIL'} (${errors.length} JS errors)`);
    exit = errors.length === 0 && canvas !== 'none' ? 0 : 1;
    try { await send('session.end'); } catch { /* ignore */ }
    ws.close();
} catch (e) {
    console.error(`HARNESS ERROR: ${e.message}`);
} finally {
    ff.kill('SIGTERM');
    server.close();
    await new Promise(r => setTimeout(r, 500));
    await rm(profile, { recursive: true, force: true });
    process.exit(exit);
}
