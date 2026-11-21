const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');
const os = require('os');

const rootDir = __dirname;
const envFilePath = path.join(rootDir, '.env');
if (fs.existsSync(envFilePath)) {
    const lines = fs.readFileSync(envFilePath, 'utf8').split(/\r?\n/);
    for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed || trimmed.startsWith('#')) continue;
        const eqIdx = trimmed.indexOf('=');
        if (eqIdx > 0) {
            const k = trimmed.slice(0, eqIdx).trim();
            const v = trimmed.slice(eqIdx + 1).trim();
            if (k && !process.env[k]) process.env[k] = v;
        }
    }
}

const releaseApkPath = path.join(rootDir, 'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk');
const releaseUnsignedApkPath = path.join(rootDir, 'app', 'build', 'outputs', 'apk', 'release', 'app-release-unsigned.apk');
const logoIconPath = path.join(rootDir, 'app', 'src', 'main', 'res', 'mipmap-xxxhdpi', 'ic_launcher.png');
const fallbackLogoPath = path.join(rootDir, 'app', 'src', 'main', 'res', 'drawable', 'ic_rezona_logo.png');
const qrisImagePath = path.join(rootDir, 'qris.png');
const PORT = 8080;

// ── FILL YOUR REAL PAYMENT & DISCORD WEBHOOK DETAILS HERE (via .env or edit) ─
const SUPPORT_CONFIG = {
    creatorName: 'Nynzz. Store',
    danaNumber: '',         // e.g. '081234567890'
    danaAccountName: '',    // e.g. 'Ahmad'
    discordWebhookUrl: process.env.DISCORD_WEBHOOK_URL || ''
};

// Server-canonical food & drink menu (each item <= Rp 20.000)
const DONATE_MENU = [
    { id: 'coffee', emoji: '☕', name: 'Black Coffee', price: 5000 },
    { id: 'matcha', emoji: '🍵', name: 'Matcha Latte', price: 10000 },
    { id: 'noodles', emoji: '🍜', name: 'Mie Ayam / Ramen', price: 12000 },
    { id: 'boba', emoji: '🧋', name: 'Brown Sugar Boba', price: 15000 },
    { id: 'nasgor', emoji: '🍛', name: 'Nasi Goreng', price: 20000 },
    { id: 'cake', emoji: '🍰', name: 'Midnight Cheesecake', price: 20000 }
];

function getExistingReleaseApk() {
    if (fs.existsSync(releaseApkPath)) return releaseApkPath;
    if (fs.existsSync(releaseUnsignedApkPath)) return releaseUnsignedApkPath;
    return null;
}

function getLogoFile() {
    if (fs.existsSync(logoIconPath)) return logoIconPath;
    if (fs.existsSync(fallbackLogoPath)) return fallbackLogoPath;
    return null;
}

function formatSize(filePath) {
    if (!filePath || !fs.existsSync(filePath)) return 'Not built yet';
    const bytes = fs.statSync(filePath).size;
    return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
}

function formatRupiah(amount) {
    return 'Rp ' + Number(amount || 0).toLocaleString('id-ID');
}

function getLastModifiedLabel(filePath) {
    if (!filePath || !fs.existsSync(filePath)) return 'Unavailable';
    const mtime = fs.statSync(filePath).mtime;
    return mtime.toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric'
    });
}

function getLanIps() {
    const nets = os.networkInterfaces();
    const ips = [];
    for (const name of Object.keys(nets)) {
        for (const net of nets[name] || []) {
            if (net.family === 'IPv4' && !net.internal) {
                ips.push(net.address);
            }
        }
    }
    return ips;
}

function sendFile(res, filePath, contentType, downloadName, asAttachment = true) {
    if (!filePath || !fs.existsSync(filePath)) {
        res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
        return res.end('File not found.');
    }

    const stat = fs.statSync(filePath);
    const headers = {
        'Content-Type': contentType,
        'Content-Length': stat.size,
        'Cache-Control': asAttachment ? 'no-store' : 'public, max-age=300'
    };
    if (asAttachment && downloadName) {
        headers['Content-Disposition'] = `attachment; filename="${downloadName}"`;
    }
    res.writeHead(200, headers);

    const stream = fs.createReadStream(filePath);
    stream.on('error', () => {
        if (!res.headersSent) {
            res.writeHead(500, { 'Content-Type': 'text/plain' });
        }
        res.end('Stream error.');
    });
    stream.pipe(res);
}

function readJsonBody(req, maxBytes = 16384) {
    return new Promise((resolve, reject) => {
        let total = 0;
        const chunks = [];
        req.on('data', (chunk) => {
            total += chunk.length;
            if (total > maxBytes) {
                req.destroy();
                return reject(new Error('Payload too large'));
            }
            chunks.push(chunk);
        });
        req.on('end', () => {
            try {
                const raw = Buffer.concat(chunks).toString('utf8');
                resolve(raw ? JSON.parse(raw) : {});
            } catch (err) {
                reject(new Error('Invalid JSON payload'));
            }
        });
        req.on('error', reject);
    });
}

function postToDiscordWebhook(webhookUrl, embedPayload) {
    return new Promise((resolve, reject) => {
        try {
            const parsed = new URL(webhookUrl);
            const body = JSON.stringify(embedPayload);
            const req = https.request({
                hostname: parsed.hostname,
                port: parsed.port || 443,
                path: parsed.pathname + parsed.search,
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Content-Length': Buffer.byteLength(body)
                }
            }, (resp) => {
                const respChunks = [];
                resp.on('data', (d) => respChunks.push(d));
                resp.on('end', () => {
                    if (resp.statusCode >= 200 && resp.statusCode < 300) {
                        resolve({ sent: true, statusCode: resp.statusCode });
                    } else {
                        reject(new Error(`Discord returned HTTP ${resp.statusCode}`));
                    }
                });
            });
            req.on('error', reject);
            req.write(body);
            req.end();
        } catch (err) {
            reject(err);
        }
    });
}

const server = http.createServer(async (req, res) => {
    const reqPath = (req.url || '/').split('?')[0];

    if (reqPath === '/logo.png' || reqPath === '/favicon.ico') {
        return sendFile(res, getLogoFile(), 'image/png', null, false);
    }

    if (reqPath === '/qris.png') {
        return sendFile(res, qrisImagePath, 'image/png', null, false);
    }

    if (reqPath === '/download' || reqPath === '/app-release.apk' || reqPath === '/download-release') {
        const target = getExistingReleaseApk();
        if (!target) {
            res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
            return res.end('Release APK not found. Please build release first.');
        }
        return sendFile(res, target, 'application/vnd.android.package-archive', 'Rezona-release.apk', true);
    }

    if (reqPath === '/api/donate' && req.method === 'POST') {
        try {
            const body = await readJsonBody(req);
            const donorName = String(body.donorName || 'Anonymous').trim().slice(0, 48) || 'Anonymous';
            const message = String(body.message || '').trim().slice(0, 240);
            const rawItems = body.items && typeof body.items === 'object' ? body.items : {};

            const pickedLines = [];
            let totalAmount = 0;

            for (const item of DONATE_MENU) {
                const qty = Math.min(20, Math.max(0, parseInt(rawItems[item.id] || 0, 10) || 0));
                if (qty > 0) {
                    const subtotal = qty * item.price;
                    totalAmount += subtotal;
                    pickedLines.push(`${item.emoji} **${qty}x ${item.name}** (${formatRupiah(subtotal)})`);
                }
            }

            if (pickedLines.length === 0 || totalAmount <= 0) {
                res.writeHead(400, { 'Content-Type': 'application/json; charset=utf-8' });
                return res.end(JSON.stringify({ ok: false, error: 'Pick at least one snack or drink first.' }));
            }

            const webhookUrl = (SUPPORT_CONFIG.discordWebhookUrl || '').trim();
            if (!webhookUrl) {
                console.log(`[DONATE_LOG] ${donorName} | ${formatRupiah(totalAmount)} | ${pickedLines.join(', ')} | Msg: ${message || '-'}`);
                res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
                return res.end(JSON.stringify({
                    ok: true,
                    webhookSent: false,
                    totalFormatted: formatRupiah(totalAmount),
                    notice: 'Recorded on server! Add discordWebhookUrl in serve_apk.js to forward live alerts to Discord.'
                }));
            }

            const embedPayload = {
                username: 'Rezona Support',
                embeds: [{
                    title: `New Support Treat — ${formatRupiah(totalAmount)}`,
                    color: 0xFFFFFF,
                    fields: [
                        { name: 'Supporter', value: donorName, inline: true },
                        { name: 'Total', value: formatRupiah(totalAmount), inline: true },
                        { name: 'Order', value: pickedLines.join('\n'), inline: false },
                        { name: 'Message', value: message || '_No message left_', inline: false }
                    ],
                    footer: { text: 'Rezona Release Share · QRIS / DANA Confirmation' },
                    timestamp: new Date().toISOString()
                }]
            };

            await postToDiscordWebhook(webhookUrl, embedPayload);
            res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
            return res.end(JSON.stringify({
                ok: true,
                webhookSent: true,
                totalFormatted: formatRupiah(totalAmount),
                notice: 'Thank you! Support message sent straight to Discord.'
            }));
        } catch (err) {
            res.writeHead(500, { 'Content-Type': 'application/json; charset=utf-8' });
            return res.end(JSON.stringify({ ok: false, error: err.message || 'Server error' }));
        }
    }

    const releaseTarget = getExistingReleaseApk();
    const releaseAvailable = Boolean(releaseTarget);
    const releaseSize = formatSize(releaseTarget);
    const buildDate = getLastModifiedLabel(releaseTarget);
    const hasQrisFile = fs.existsSync(qrisImagePath);
    const lanIps = getLanIps();
    const shareUrl = lanIps.length > 0 ? `http://${lanIps[0]}:${PORT}/` : `http://localhost:${PORT}/`;

    const clientDataJson = JSON.stringify({
        menu: DONATE_MENU,
        hasQrisFile,
        danaNumber: SUPPORT_CONFIG.danaNumber,
        danaAccountName: SUPPORT_CONFIG.danaAccountName,
        webhookReady: Boolean(SUPPORT_CONFIG.discordWebhookUrl && SUPPORT_CONFIG.discordWebhookUrl.trim())
    }).replace(/</g, '\\u003c');

    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(`<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Rezona — Official Release APK</title>
    <link rel="icon" type="image/png" href="/logo.png">
    <style>
        :root {
            color-scheme: dark;
            --obsidian: #050608;
            --pure-white: #FFFFFF;
            --pearl: #F2F4F8;
            --soft-silver: #A4ACB9;
            --muted: #6C7484;
            --glass-bg: linear-gradient(155deg, rgba(255, 255, 255, 0.14) 0%, rgba(255, 255, 255, 0.04) 55%, rgba(255, 255, 255, 0.08) 100%);
            --glass-border: rgba(255, 255, 255, 0.24);
            --glass-specular: rgba(255, 255, 255, 0.45);
        }

        * {
            box-sizing: border-box;
        }

        html, body {
            margin: 0;
            padding: 0;
            min-height: 100%;
            background: var(--obsidian);
            color: var(--pearl);
            font-family: 'Satoshi', 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            overflow-x: hidden;
        }

        body {
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            min-height: 100vh;
            padding: 28px 18px;
            position: relative;
        }

        /* Ambient Animated Monochrome Liquid Backdrop */
        .ambient-stage {
            position: fixed;
            inset: 0;
            overflow: hidden;
            pointer-events: none;
            z-index: 0;
            background:
                radial-gradient(circle at 50% 0%, rgba(255, 255, 255, 0.09) 0%, transparent 55%),
                linear-gradient(180deg, #090B10 0%, #040507 100%);
        }

        .orb {
            position: absolute;
            border-radius: 50%;
            filter: blur(70px);
            opacity: 0.42;
            will-change: transform, opacity;
        }

        .orb-1 {
            width: 440px;
            height: 440px;
            top: -120px;
            left: -90px;
            background: radial-gradient(circle, rgba(255, 255, 255, 0.24) 0%, rgba(160, 175, 200, 0.08) 55%, transparent 72%);
        }

        .orb-2 {
            width: 520px;
            height: 520px;
            bottom: -160px;
            right: -110px;
            background: radial-gradient(circle, rgba(210, 222, 240, 0.18) 0%, rgba(120, 132, 160, 0.06) 55%, transparent 72%);
        }

        .orb-3 {
            width: 300px;
            height: 300px;
            top: 44%;
            left: 55%;
            background: radial-gradient(circle, rgba(255, 255, 255, 0.12) 0%, transparent 70%);
        }

        @media (prefers-reduced-motion: no-preference) {
            body:not(.motion-paused) .orb-1 {
                animation: driftOne 16s ease-in-out infinite alternate;
            }
            body:not(.motion-paused) .orb-2 {
                animation: driftTwo 20s ease-in-out infinite alternate;
            }
            body:not(.motion-paused) .orb-3 {
                animation: pulseGlow 11s ease-in-out infinite alternate;
            }
        }

        @keyframes driftOne {
            0% { transform: translate3d(0, 0, 0) scale(1); }
            100% { transform: translate3d(70px, 55px, 0) scale(1.12); }
        }

        @keyframes driftTwo {
            0% { transform: translate3d(0, 0, 0) scale(1); }
            100% { transform: translate3d(-80px, -60px, 0) scale(1.16); }
        }

        @keyframes pulseGlow {
            0% { transform: translate3d(-30px, 20px, 0) scale(0.92); opacity: 0.25; }
            100% { transform: translate3d(30px, -30px, 0) scale(1.15); opacity: 0.48; }
        }

        /* Subtle Top Controls */
        .top-bar {
            position: fixed;
            top: 16px;
            right: 16px;
            z-index: 3;
            display: flex;
            gap: 8px;
        }

        .micro-pill {
            appearance: none;
            border: 1px solid rgba(255, 255, 255, 0.18);
            background: rgba(255, 255, 255, 0.07);
            backdrop-filter: blur(18px);
            -webkit-backdrop-filter: blur(18px);
            color: var(--soft-silver);
            font-size: 12px;
            font-weight: 600;
            padding: 8px 13px;
            border-radius: 999px;
            cursor: pointer;
            transition: background-color 0.2s ease, color 0.2s ease, border-color 0.2s ease;
        }

        .micro-pill:hover {
            background: rgba(255, 255, 255, 0.14);
            color: var(--pure-white);
            border-color: rgba(255, 255, 255, 0.34);
        }

        /* Main Liquid Glass Card */
        .glass-shell {
            position: relative;
            z-index: 1;
            width: 100%;
            max-width: 440px;
            border-radius: 34px;
            padding: 34px 26px 28px;
            background: var(--glass-bg);
            backdrop-filter: blur(32px) saturate(140%);
            -webkit-backdrop-filter: blur(32px) saturate(140%);
            border: 1px solid var(--glass-border);
            box-shadow:
                inset 0 1px 0 var(--glass-specular),
                inset 0 -1px 0 rgba(255, 255, 255, 0.06),
                0 32px 80px rgba(0, 0, 0, 0.85);
            text-align: center;
            overflow: hidden;
        }

        .glass-sheen {
            position: absolute;
            inset: -1px;
            border-radius: inherit;
            pointer-events: none;
            background: radial-gradient(
                360px circle at var(--mx, 50%) var(--my, 15%),
                rgba(255, 255, 255, 0.16),
                transparent 60%
            );
            opacity: 0.7;
            transition: opacity 0.3s ease;
        }

        @media (prefers-reduced-motion: no-preference) {
            .reveal-stage {
                animation: cardEnter 0.75s cubic-bezier(0.22, 1, 0.36, 1) both;
            }
            .reveal-item-1 { animation: itemEnter 0.65s 0.08s cubic-bezier(0.22, 1, 0.36, 1) both; }
            .reveal-item-2 { animation: itemEnter 0.65s 0.16s cubic-bezier(0.22, 1, 0.36, 1) both; }
            .reveal-item-3 { animation: itemEnter 0.65s 0.24s cubic-bezier(0.22, 1, 0.36, 1) both; }
            .reveal-item-4 { animation: itemEnter 0.65s 0.32s cubic-bezier(0.22, 1, 0.36, 1) both; }
        }

        @keyframes cardEnter {
            from { opacity: 0; transform: translate3d(0, 22px, 0) scale(0.97); }
            to { opacity: 1; transform: translate3d(0, 0, 0) scale(1); }
        }

        @keyframes itemEnter {
            from { opacity: 0; transform: translate3d(0, 12px, 0); }
            to { opacity: 1; transform: translate3d(0, 0, 0); }
        }

        /* App Logo (PP) with Resonance Halo */
        .logo-stage {
            position: relative;
            width: 108px;
            height: 108px;
            margin: 0 auto 20px;
            display: grid;
            place-items: center;
        }

        .halo-ring {
            position: absolute;
            inset: -8px;
            border-radius: 32px;
            border: 1px solid rgba(255, 255, 255, 0.28);
            opacity: 0.35;
            pointer-events: none;
        }

        .halo-ring-outer {
            inset: -18px;
            border-radius: 38px;
            border-color: rgba(255, 255, 255, 0.12);
        }

        @media (prefers-reduced-motion: no-preference) {
            body:not(.motion-paused) .halo-ring {
                animation: haloBreathe 3.4s ease-in-out infinite;
            }
            body:not(.motion-paused) .halo-ring-outer {
                animation: haloBreathe 3.4s 0.9s ease-in-out infinite;
            }
        }

        @keyframes haloBreathe {
            0%, 100% { transform: scale(0.97); opacity: 0.22; }
            50% { transform: scale(1.05); opacity: 0.56; }
        }

        .app-logo {
            position: relative;
            width: 96px;
            height: 96px;
            border-radius: 26px;
            object-fit: cover;
            background: #000000;
            border: 1px solid rgba(255, 255, 255, 0.36);
            box-shadow:
                0 16px 36px rgba(0, 0, 0, 0.72),
                inset 0 1px 0 rgba(255, 255, 255, 0.35);
            transition: transform 0.3s cubic-bezier(0.22, 1, 0.36, 1);
        }

        .glass-shell:hover .app-logo {
            transform: scale(1.03);
        }

        .status-chip {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            padding: 6px 14px;
            border-radius: 999px;
            background: rgba(255, 255, 255, 0.08);
            border: 1px solid rgba(255, 255, 255, 0.2);
            color: var(--pearl);
            font-size: 11.5px;
            font-weight: 700;
            letter-spacing: 0.8px;
            margin-bottom: 14px;
        }

        .status-dot {
            width: 7px;
            height: 7px;
            border-radius: 50%;
            background: ${releaseAvailable ? '#FFFFFF' : '#7C8394'};
            box-shadow: ${releaseAvailable ? '0 0 10px rgba(255, 255, 255, 0.9)' : 'none'};
        }

        @media (prefers-reduced-motion: no-preference) {
            body:not(.motion-paused) .status-dot {
                animation: dotPulse 2.4s ease-in-out infinite;
            }
        }

        @keyframes dotPulse {
            0%, 100% { opacity: 0.55; transform: scale(0.92); }
            50% { opacity: 1; transform: scale(1.15); }
        }

        h1 {
            margin: 0;
            font-size: 32px;
            font-weight: 800;
            letter-spacing: 0.06em;
            color: var(--pure-white);
        }

        .tagline {
            margin: 8px auto 22px;
            max-width: 310px;
            font-size: 13.5px;
            line-height: 1.5;
            color: var(--soft-silver);
        }

        /* Spec Strip */
        .spec-strip {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 8px;
            margin-bottom: 22px;
        }

        .spec-cell {
            padding: 11px 8px;
            border-radius: 16px;
            background: rgba(255, 255, 255, 0.05);
            border: 1px solid rgba(255, 255, 255, 0.12);
        }

        .spec-label {
            font-size: 11px;
            color: var(--muted);
            margin-bottom: 4px;
        }

        .spec-value {
            font-size: 13px;
            font-weight: 700;
            color: var(--pure-white);
        }

        /* Buttons */
        .actions {
            display: flex;
            flex-direction: column;
            gap: 12px;
            position: relative;
            z-index: 2;
        }

        .btn {
            position: relative;
            display: flex;
            align-items: center;
            justify-content: space-between;
            width: 100%;
            min-height: 52px;
            padding: 14px 20px;
            border-radius: 999px;
            text-decoration: none;
            font-family: inherit;
            font-size: 14.5px;
            font-weight: 700;
            cursor: pointer;
            overflow: hidden;
            transition: transform 0.2s cubic-bezier(0.22, 1, 0.36, 1), box-shadow 0.25s ease, background-color 0.2s ease, border-color 0.2s ease;
        }

        .btn:active {
            transform: scale(0.975);
        }

        .btn-primary {
            background: linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(226, 232, 240, 0.92) 100%);
            color: #07080B;
            border: 1px solid rgba(255, 255, 255, 1);
            box-shadow: 0 12px 32px rgba(255, 255, 255, 0.16);
        }

        .btn-primary:hover {
            transform: translateY(-2px);
            box-shadow: 0 16px 38px rgba(255, 255, 255, 0.24);
        }

        .btn-primary::after {
            content: '';
            position: absolute;
            top: 0;
            left: -120%;
            width: 65%;
            height: 100%;
            background: linear-gradient(105deg, transparent 20%, rgba(255, 255, 255, 0.65) 50%, transparent 80%);
            pointer-events: none;
        }

        @media (prefers-reduced-motion: no-preference) {
            body:not(.motion-paused) .btn-primary::after {
                animation: sheenSweep 4.8s ease-in-out infinite;
            }
        }

        @keyframes sheenSweep {
            0%, 60% { transform: translateX(0); }
            100% { transform: translateX(460%); }
        }

        .btn-support {
            background: linear-gradient(180deg, rgba(255, 255, 255, 0.14) 0%, rgba(255, 255, 255, 0.05) 100%);
            color: var(--pure-white);
            border: 1px solid rgba(255, 255, 255, 0.26);
            backdrop-filter: blur(18px);
            -webkit-backdrop-filter: blur(18px);
        }

        .btn-support:hover {
            transform: translateY(-1px);
            background: linear-gradient(180deg, rgba(255, 255, 255, 0.20) 0%, rgba(255, 255, 255, 0.09) 100%);
            border-color: rgba(255, 255, 255, 0.42);
        }

        .btn-left {
            display: flex;
            align-items: center;
            gap: 11px;
        }

        .btn-meta {
            font-size: 12px;
            font-weight: 600;
            opacity: 0.78;
        }

        .btn-disabled {
            opacity: 0.52;
            pointer-events: none;
        }

        /* Animated waveform accent */
        .eq-bars {
            display: inline-flex;
            align-items: flex-end;
            gap: 2.5px;
            height: 14px;
        }

        .eq-bars span {
            width: 2.5px;
            height: 5px;
            border-radius: 2px;
            background: currentColor;
        }

        @media (prefers-reduced-motion: no-preference) {
            body:not(.motion-paused) .eq-bars span:nth-child(1) { animation: eqBounce 1.1s 0.0s ease-in-out infinite alternate; }
            body:not(.motion-paused) .eq-bars span:nth-child(2) { animation: eqBounce 0.9s 0.2s ease-in-out infinite alternate; }
            body:not(.motion-paused) .eq-bars span:nth-child(3) { animation: eqBounce 1.2s 0.4s ease-in-out infinite alternate; }
        }

        @keyframes eqBounce {
            0% { height: 4px; opacity: 0.55; }
            100% { height: 14px; opacity: 1; }
        }

        /* Share Strip */
        .share-row {
            margin-top: 20px;
            padding: 12px 14px;
            border-radius: 18px;
            background: rgba(255, 255, 255, 0.04);
            border: 1px solid rgba(255, 255, 255, 0.12);
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 10px;
            font-size: 12px;
            color: var(--soft-silver);
        }

        .share-url {
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            color: var(--pure-white);
            font-weight: 600;
        }

        .copy-btn {
            appearance: none;
            border: 1px solid rgba(255, 255, 255, 0.24);
            background: rgba(255, 255, 255, 0.1);
            color: var(--pure-white);
            border-radius: 999px;
            padding: 7px 13px;
            font-size: 11.5px;
            font-weight: 700;
            cursor: pointer;
            flex-shrink: 0;
            transition: background-color 0.2s ease;
        }

        .copy-btn:hover {
            background: rgba(255, 255, 255, 0.2);
        }

        .footer-note {
            margin-top: 18px;
            font-size: 11.5px;
            color: var(--muted);
        }

        /* Native <dialog> Top-Layer Liquid Glass Modal */
        dialog.support-dialog {
            margin: auto;
            padding: 0;
            width: min(94vw, 450px);
            max-height: 90vh;
            border-radius: 30px;
            border: 1px solid rgba(255, 255, 255, 0.28);
            background: linear-gradient(160deg, rgba(24, 28, 37, 0.92) 0%, rgba(10, 12, 17, 0.96) 100%);
            backdrop-filter: blur(32px) saturate(150%);
            -webkit-backdrop-filter: blur(32px) saturate(150%);
            color: var(--pearl);
            box-shadow:
                inset 0 1px 0 rgba(255, 255, 255, 0.36),
                0 32px 84px rgba(0, 0, 0, 0.88);
            opacity: 0;
            overflow-y: auto;
            transition-property: opacity, transform, display, overlay;
            transition-duration: 0.28s;
            transition-timing-function: cubic-bezier(0.22, 1, 0.36, 1);
            transition-behavior: allow-discrete;
        }

        @media (prefers-reduced-motion: no-preference) {
            dialog.support-dialog {
                transform: scale(0.92) translateY(10px);
            }
        }

        :is(dialog.support-dialog[open]):where(:not([data-closing])) {
            opacity: 1;
            @starting-style { opacity: 0; }
            @media (prefers-reduced-motion: no-preference) {
                transform: scale(1) translateY(0);
                @starting-style { transform: scale(0.92) translateY(10px); }
            }
        }

        dialog.support-dialog:modal {
            overscroll-behavior: contain;
        }

        dialog.support-dialog::backdrop {
            background-color: rgba(0, 0, 0, 0);
            backdrop-filter: blur(0px);
            overflow: hidden;
            overscroll-behavior: contain;
            transition-property: background-color, backdrop-filter, display, overlay;
            transition-duration: 0.28s;
            transition-timing-function: ease-out;
            transition-behavior: allow-discrete;
        }

        :is(dialog.support-dialog[open]):where(:not([data-closing]))::backdrop {
            background-color: rgba(2, 3, 6, 0.74);
            backdrop-filter: blur(8px);
            @starting-style {
                background-color: rgba(0, 0, 0, 0);
                backdrop-filter: blur(0px);
            }
        }

        @media (prefers-reduced-motion: reduce) {
            dialog.support-dialog,
            dialog.support-dialog::backdrop {
                transition-duration: 0.1s;
            }
        }

        .dialog-body {
            padding: 24px 22px 22px;
        }

        .dialog-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 12px;
            margin-bottom: 10px;
        }

        .dialog-title-wrap {
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .dialog-mini-logo {
            width: 38px;
            height: 38px;
            border-radius: 12px;
            border: 1px solid rgba(255, 255, 255, 0.28);
            object-fit: cover;
            background: #000000;
        }

        .dialog-header h2 {
            margin: 0;
            font-size: 19px;
            font-weight: 800;
            color: var(--pure-white);
        }

        .close-x {
            appearance: none;
            width: 36px;
            height: 36px;
            border-radius: 50%;
            border: 1px solid rgba(255, 255, 255, 0.22);
            background: rgba(255, 255, 255, 0.08);
            color: var(--pure-white);
            font-size: 18px;
            cursor: pointer;
            display: grid;
            place-items: center;
        }

        .dialog-desc {
            margin: 0 0 16px;
            font-size: 13px;
            line-height: 1.5;
            color: var(--soft-silver);
        }

        /* Treat Menu Rows */
        .menu-list {
            display: flex;
            flex-direction: column;
            gap: 8px;
            margin-bottom: 16px;
        }

        .menu-item {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 10px;
            padding: 10px 12px;
            border-radius: 16px;
            background: rgba(255, 255, 255, 0.05);
            border: 1px solid rgba(255, 255, 255, 0.14);
            transition: background-color 0.2s ease, border-color 0.2s ease;
        }

        .menu-item.has-qty {
            background: rgba(255, 255, 255, 0.11);
            border-color: rgba(255, 255, 255, 0.34);
        }

        .menu-info {
            display: flex;
            align-items: center;
            gap: 10px;
            text-align: left;
        }

        .menu-emoji {
            font-size: 20px;
            width: 30px;
            text-align: center;
        }

        .menu-name {
            font-size: 13.5px;
            font-weight: 700;
            color: var(--pure-white);
        }

        .menu-price {
            font-size: 12px;
            color: var(--soft-silver);
        }

        .stepper {
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .step-btn {
            appearance: none;
            width: 34px;
            height: 34px;
            border-radius: 50%;
            border: 1px solid rgba(255, 255, 255, 0.25);
            background: rgba(255, 255, 255, 0.09);
            color: var(--pure-white);
            font-size: 17px;
            font-weight: 700;
            cursor: pointer;
            display: grid;
            place-items: center;
            transition: background-color 0.15s ease, transform 0.15s ease;
        }

        .step-btn:active {
            transform: scale(0.9);
        }

        .step-btn:hover {
            background: rgba(255, 255, 255, 0.2);
        }

        .step-qty {
            min-width: 18px;
            text-align: center;
            font-size: 14px;
            font-weight: 700;
            color: var(--pure-white);
        }

        .field-group {
            display: flex;
            flex-direction: column;
            align-items: flex-start;
            gap: 6px;
            margin-bottom: 12px;
            text-align: left;
        }

        .field-group label {
            font-size: 12.5px;
            font-weight: 600;
            color: var(--pearl);
        }

        .field-input, .field-textarea {
            width: 100%;
            font: inherit;
            font-size: max(1rem, 1em);
            color: var(--pure-white);
            background: rgba(255, 255, 255, 0.06);
            border: 1px solid rgba(255, 255, 255, 0.2);
            border-radius: 14px;
            padding: 11px 13px;
            min-height: 46px;
            box-sizing: border-box;
        }

        .field-textarea {
            min-height: 76px;
            resize: vertical;
        }

        .field-input:focus-visible,
        .field-textarea:focus-visible,
        button:focus-visible,
        a:focus-visible {
            outline: 2px solid #FFFFFF;
            outline-offset: 2px;
        }

        .payment-box {
            padding: 16px;
            border-radius: 20px;
            background: rgba(255, 255, 255, 0.05);
            border: 1px solid rgba(255, 255, 255, 0.16);
            margin-bottom: 14px;
            text-align: center;
        }

        .qris-img {
            width: 210px;
            max-width: 100%;
            border-radius: 16px;
            border: 1px solid rgba(255, 255, 255, 0.25);
            background: #FFFFFF;
            padding: 8px;
            margin: 10px auto;
            display: block;
        }

        .dana-strip {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 10px;
            padding: 10px 12px;
            border-radius: 14px;
            background: rgba(255, 255, 255, 0.07);
            border: 1px solid rgba(255, 255, 255, 0.18);
            margin-top: 10px;
            font-size: 13px;
        }

        .dialog-status {
            margin: 10px 0 0;
            font-size: 13px;
            font-weight: 600;
            color: var(--pearl);
            min-height: 18px;
            text-align: center;
        }

        .step-actions {
            display: flex;
            gap: 10px;
            margin-top: 14px;
        }

        .step-actions .btn-back {
            width: auto;
            min-width: 90px;
            justify-content: center;
        }
    </style>
</head>
<body>
    <div class="ambient-stage" aria-hidden="true">
        <div class="orb orb-1"></div>
        <div class="orb orb-2"></div>
        <div class="orb orb-3"></div>
    </div>

    <div class="top-bar">
        <button type="button" class="micro-pill" id="motionToggleBtn">Pause motion</button>
    </div>

    <main class="glass-shell reveal-stage" id="glassCard">
        <div class="glass-sheen" aria-hidden="true"></div>

        <div class="logo-stage reveal-item-1">
            <div class="halo-ring" aria-hidden="true"></div>
            <div class="halo-ring halo-ring-outer" aria-hidden="true"></div>
            <img class="app-logo" src="/logo.png" alt="Rezona App Logo">
        </div>

        <div class="reveal-item-2">
            <div class="status-chip">
                <span class="status-dot" aria-hidden="true"></span>
                <span>${releaseAvailable ? 'Release APK Ready' : 'Release Build Pending'}</span>
            </div>
            <h1>REZONA</h1>
            <p class="tagline">Monochrome Liquid Glass music player powered by YouTube Music &amp; synced lyrics.</p>
        </div>

        <div class="spec-strip reveal-item-3">
            <div class="spec-cell">
                <div class="spec-label">Build</div>
                <div class="spec-value">Release R8</div>
            </div>
            <div class="spec-cell">
                <div class="spec-label">Size</div>
                <div class="spec-value">${releaseSize}</div>
            </div>
            <div class="spec-cell">
                <div class="spec-label">Updated</div>
                <div class="spec-value">${buildDate}</div>
            </div>
        </div>

        <div class="actions reveal-item-4">
            <a class="btn btn-primary ${releaseAvailable ? '' : 'btn-disabled'}" href="/download-release" id="downloadApkBtn">
                <span class="btn-left">
                    <span class="eq-bars" aria-hidden="true"><span></span><span></span><span></span></span>
                    <span id="downloadLabel">Download Release APK</span>
                </span>
                <span class="btn-meta">${releaseSize}</span>
            </a>

            <button
                type="button"
                class="btn btn-support"
                id="openSupportBtn"
                command="show-modal"
                commandfor="supportDialog"
            >
                <span class="btn-left">
                    <span aria-hidden="true">&#9829;</span>
                    <span>Support &amp; Donate</span>
                </span>
                <span class="btn-meta">Faster Updates</span>
            </button>
        </div>

        <div class="share-row reveal-item-4">
            <span class="share-url" id="shareUrlText">${shareUrl}</span>
            <button type="button" class="copy-btn" id="copyShareBtn">Copy Link</button>
        </div>

        <p class="footer-note">Android 8.0+ · Signed Release APK</p>
    </main>

    <dialog id="supportDialog" class="support-dialog" closedby="any" aria-labelledby="supportDialogTitle">
        <div class="dialog-body">
            <div class="dialog-header">
                <div class="dialog-title-wrap">
                    <img class="dialog-mini-logo" src="/logo.png" alt="">
                    <h2 id="supportDialogTitle">Treat Rezona Dev</h2>
                </div>
                <button type="button" class="close-x" id="closeSupportBtn" aria-label="Close dialog">&times;</button>
            </div>
            <p class="dialog-desc">
                Pick snacks or drinks (max Rp 20.000 each) &amp; leave a message. Alerts go directly to our Discord!
            </p>

            <form id="donateForm" action="/api/donate" method="POST">
                <div id="stepOne">
                    <div class="menu-list" id="menuContainer"></div>

                    <div class="field-group">
                        <label for="donorName">Your name (optional)</label>
                        <input class="field-input" type="text" id="donorName" name="donorName" maxlength="48" autocomplete="name" placeholder="Anonymous">
                    </div>

                    <div class="field-group">
                        <label for="donorMessage">Support message for Discord</label>
                        <textarea class="field-textarea" id="donorMessage" name="message" maxlength="240" placeholder="Keep updating Rezona faster!"></textarea>
                    </div>

                    <button type="button" class="btn btn-primary" id="toPaymentBtn">
                        <span class="btn-left">Proceed to Payment</span>
                        <span class="btn-meta" id="menuTotalLabel">Rp 0</span>
                    </button>
                </div>

                <div id="stepTwo" hidden>
                    <div class="payment-box" id="paymentSummaryBox"></div>

                    <div class="step-actions">
                        <button type="button" class="btn btn-support btn-back" id="backToMenuBtn">Back</button>
                        <button type="submit" class="btn btn-primary" id="submitDonateBtn">
                            <span>Confirm Paid</span>
                            <span class="btn-meta" id="confirmTotalLabel">Rp 0</span>
                        </button>
                    </div>
                </div>

                <p class="dialog-status" id="donateStatus" role="status" aria-live="polite"></p>
            </form>
        </div>
    </dialog>

    <script>
        (function () {
            const appData = ${clientDataJson};
            const quantities = {};
            appData.menu.forEach((m) => { quantities[m.id] = 0; });

            function formatRp(n) {
                return 'Rp ' + Number(n || 0).toLocaleString('id-ID');
            }

            // Interactive Liquid Glass specular sheen tracking
            const card = document.getElementById('glassCard');
            if (card) {
                card.addEventListener('pointermove', (e) => {
                    const rect = card.getBoundingClientRect();
                    const x = ((e.clientX - rect.left) / rect.width) * 100;
                    const y = ((e.clientY - rect.top) / rect.height) * 100;
                    card.style.setProperty('--mx', x.toFixed(1) + '%');
                    card.style.setProperty('--my', y.toFixed(1) + '%');
                });
            }

            // Pause/Resume Motion control (WCAG 2.2.2)
            const motionBtn = document.getElementById('motionToggleBtn');
            if (motionBtn) {
                motionBtn.addEventListener('click', () => {
                    const paused = document.body.classList.toggle('motion-paused');
                    motionBtn.textContent = paused ? 'Resume motion' : 'Pause motion';
                });
            }

            // Download button feedback
            const dlBtn = document.getElementById('downloadApkBtn');
            const dlLabel = document.getElementById('downloadLabel');
            if (dlBtn && dlLabel) {
                dlBtn.addEventListener('click', () => {
                    const orig = dlLabel.textContent;
                    dlLabel.textContent = 'Starting download...';
                    setTimeout(() => { dlLabel.textContent = orig; }, 3500);
                });
            }

            // Copy LAN link helper
            const copyBtn = document.getElementById('copyShareBtn');
            const shareText = document.getElementById('shareUrlText');
            if (copyBtn && shareText) {
                copyBtn.addEventListener('click', async () => {
                    try {
                        await navigator.clipboard.writeText(shareText.textContent.trim());
                        copyBtn.textContent = 'Copied!';
                    } catch (_) {
                        copyBtn.textContent = 'Ready';
                    }
                    setTimeout(() => { copyBtn.textContent = 'Copy Link'; }, 2000);
                });
            }

            // Donation Menu Stepper Logic
            const menuContainer = document.getElementById('menuContainer');
            const menuTotalLabel = document.getElementById('menuTotalLabel');
            const confirmTotalLabel = document.getElementById('confirmTotalLabel');
            const donateStatus = document.getElementById('donateStatus');
            const stepOne = document.getElementById('stepOne');
            const stepTwo = document.getElementById('stepTwo');
            const toPaymentBtn = document.getElementById('toPaymentBtn');
            const backToMenuBtn = document.getElementById('backToMenuBtn');
            const paymentSummaryBox = document.getElementById('paymentSummaryBox');
            const donateForm = document.getElementById('donateForm');
            const submitDonateBtn = document.getElementById('submitDonateBtn');

            function computeTotal() {
                return appData.menu.reduce((sum, item) => sum + (quantities[item.id] * item.price), 0);
            }

            function refreshTotals() {
                const total = computeTotal();
                if (menuTotalLabel) menuTotalLabel.textContent = formatRp(total);
                if (confirmTotalLabel) confirmTotalLabel.textContent = formatRp(total);
            }

            function renderMenu() {
                if (!menuContainer) return;
                menuContainer.innerHTML = '';
                appData.menu.forEach((item) => {
                    const row = document.createElement('div');
                    row.className = 'menu-item' + (quantities[item.id] > 0 ? ' has-qty' : '');

                    const info = document.createElement('div');
                    info.className = 'menu-info';
                    info.innerHTML = '<span class="menu-emoji">' + item.emoji + '</span><div><div class="menu-name">' + item.name + '</div><div class="menu-price">' + formatRp(item.price) + '</div></div>';

                    const stepper = document.createElement('div');
                    stepper.className = 'stepper';

                    const minus = document.createElement('button');
                    minus.type = 'button';
                    minus.className = 'step-btn';
                    minus.setAttribute('aria-label', 'Decrease ' + item.name);
                    minus.textContent = '−';

                    const qtySpan = document.createElement('span');
                    qtySpan.className = 'step-qty';
                    qtySpan.textContent = String(quantities[item.id]);

                    const plus = document.createElement('button');
                    plus.type = 'button';
                    plus.className = 'step-btn';
                    plus.setAttribute('aria-label', 'Add ' + item.name);
                    plus.textContent = '+';

                    minus.addEventListener('click', () => {
                        if (quantities[item.id] > 0) {
                            quantities[item.id] -= 1;
                            qtySpan.textContent = String(quantities[item.id]);
                            row.classList.toggle('has-qty', quantities[item.id] > 0);
                            refreshTotals();
                            if (donateStatus) donateStatus.textContent = '';
                        }
                    });

                    plus.addEventListener('click', () => {
                        if (quantities[item.id] < 20) {
                            quantities[item.id] += 1;
                            qtySpan.textContent = String(quantities[item.id]);
                            row.classList.add('has-qty');
                            refreshTotals();
                            if (donateStatus) donateStatus.textContent = '';
                        }
                    });

                    stepper.appendChild(minus);
                    stepper.appendChild(qtySpan);
                    stepper.appendChild(plus);
                    row.appendChild(info);
                    row.appendChild(stepper);
                    menuContainer.appendChild(row);
                });
                refreshTotals();
            }

            renderMenu();

            function buildPaymentSummary() {
                if (!paymentSummaryBox) return;
                const picked = appData.menu.filter((m) => quantities[m.id] > 0)
                    .map((m) => quantities[m.id] + 'x ' + m.emoji + ' ' + m.name)
                    .join(' · ');
                const total = formatRp(computeTotal());

                let html = '<div style="font-size:13px;color:#A4ACB9;margin-bottom:4px;">' + picked + '</div>';
                html += '<div style="font-size:22px;font-weight:800;color:#FFFFFF;margin-bottom:8px;">' + total + '</div>';

                if (appData.hasQrisFile) {
                    html += '<img src="/qris.png" alt="QRIS Payment Code" class="qris-img">';
                    html += '<div style="font-size:12px;color:#A4ACB9;">Scan QRIS using DANA, GoPay, OVO, ShopeePay, or any Mobile Banking app.</div>';
                } else {
                    html += '<div style="font-size:12px;color:#A4ACB9;padding:10px;border:1px dashed rgba(255,255,255,0.22);border-radius:12px;">Drop <code>qris.png</code> into the project folder to display your QRIS code automatically.</div>';
                }

                if (appData.danaNumber) {
                    html += '<div class="dana-strip"><span>DANA: <strong>' + appData.danaNumber + '</strong>' + (appData.danaAccountName ? ' (' + appData.danaAccountName + ')' : '') + '</span><button type="button" class="copy-btn" id="copyDanaBtn">Copy DANA</button></div>';
                }

                paymentSummaryBox.innerHTML = html;

                const copyDanaBtn = document.getElementById('copyDanaBtn');
                if (copyDanaBtn && appData.danaNumber) {
                    copyDanaBtn.addEventListener('click', async () => {
                        try {
                            await navigator.clipboard.writeText(appData.danaNumber);
                            copyDanaBtn.textContent = 'Copied!';
                        } catch (_) {}
                        setTimeout(() => { copyDanaBtn.textContent = 'Copy DANA'; }, 1800);
                    });
                }
            }

            if (toPaymentBtn) {
                toPaymentBtn.addEventListener('click', () => {
                    if (computeTotal() <= 0) {
                        if (donateStatus) donateStatus.textContent = 'Pick at least one snack or drink first.';
                        return;
                    }
                    if (donateStatus) donateStatus.textContent = '';
                    buildPaymentSummary();
                    if (stepOne) stepOne.hidden = true;
                    if (stepTwo) stepTwo.hidden = false;
                });
            }

            if (backToMenuBtn) {
                backToMenuBtn.addEventListener('click', () => {
                    if (stepTwo) stepTwo.hidden = true;
                    if (stepOne) stepOne.hidden = false;
                    if (donateStatus) donateStatus.textContent = '';
                });
            }

            if (donateForm) {
                donateForm.addEventListener('submit', async (e) => {
                    e.preventDefault();
                    if (computeTotal() <= 0) {
                        if (donateStatus) donateStatus.textContent = 'Pick at least one item first.';
                        return;
                    }
                    if (submitDonateBtn) submitDonateBtn.disabled = true;
                    if (donateStatus) donateStatus.textContent = 'Sending support alert...';

                    try {
                        const res = await fetch('/api/donate', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({
                                donorName: document.getElementById('donorName')?.value || '',
                                message: document.getElementById('donorMessage')?.value || '',
                                items: quantities
                            })
                        });
                        const data = await res.json();
                        if (!res.ok || !data.ok) {
                            throw new Error(data.error || 'Failed to submit support.');
                        }
                        if (donateStatus) donateStatus.textContent = data.notice || 'Thank you!';
                        Object.keys(quantities).forEach((k) => { quantities[k] = 0; });
                        renderMenu();
                        setTimeout(() => {
                            if (stepTwo) stepTwo.hidden = true;
                            if (stepOne) stepOne.hidden = false;
                        }, 1800);
                    } catch (err) {
                        if (donateStatus) donateStatus.textContent = err.message || 'Error sending alert.';
                    } finally {
                        if (submitDonateBtn) submitDonateBtn.disabled = false;
                    }
                });
            }

            // Native Top-layer Dialog fallback helpers
            const dialog = document.getElementById('supportDialog');
            const openBtn = document.getElementById('openSupportBtn');
            const closeBtn = document.getElementById('closeSupportBtn');

            let supportsDisplayTransition;
            function canTransitionDisplay() {
                if (supportsDisplayTransition !== undefined) return supportsDisplayTransition;
                if (!window.CSS || !CSS.supports || !CSS.supports('transition-behavior', 'allow-discrete') || !document.body) {
                    return false;
                }
                const probe = document.createElement('div');
                probe.style.cssText = 'transition: display 1s allow-discrete !important; display: block;';
                document.body.appendChild(probe);
                getComputedStyle(probe).display;
                probe.style.display = 'none';
                supportsDisplayTransition = getComputedStyle(probe).display === 'block';
                probe.remove();
                return supportsDisplayTransition;
            }

            function supportsTopLayerExit() {
                return Boolean(window.CSS && CSS.supports && CSS.supports('overlay', 'auto')) && canTransitionDisplay();
            }

            async function closeDialogSmooth(el) {
                if (!el || !el.open) return;
                if (!supportsTopLayerExit()) {
                    el.setAttribute('data-closing', '');
                    const animations = el.getAnimations({ subtree: true });
                    if (animations.length > 0) {
                        await Promise.race([
                            Promise.allSettled(animations.map((a) => a.finished)),
                            new Promise((r) => setTimeout(r, 1500))
                        ]);
                    }
                    if (!el.hasAttribute('data-closing')) return;
                    el.removeAttribute('data-closing');
                }
                el.close();
            }

            if (dialog) {
                if (openBtn && !('commandForElement' in HTMLButtonElement.prototype)) {
                    openBtn.addEventListener('click', () => {
                        if (!dialog.open) dialog.showModal();
                    });
                }
                if (closeBtn) {
                    closeBtn.addEventListener('click', () => closeDialogSmooth(dialog));
                }
                dialog.addEventListener('cancel', (event) => {
                    event.preventDefault();
                    closeDialogSmooth(dialog);
                });
                if (!('closedBy' in HTMLDialogElement.prototype)) {
                    const isOutside = (e) => {
                        const r = dialog.getBoundingClientRect();
                        return e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom;
                    };
                    let pressedOutside = false;
                    dialog.addEventListener('pointerdown', (e) => { pressedOutside = isOutside(e); });
                    dialog.addEventListener('pointerup', (e) => {
                        if (pressedOutside && isOutside(e)) closeDialogSmooth(dialog);
                    });
                }
            }
        })();
    </script>
</body>
</html>`);
});

server.listen(PORT, '0.0.0.0', () => {
    const ips = getLanIps();
    console.log(`Server running at http://0.0.0.0:${PORT}/`);
    if (ips.length > 0) {
        console.log(`LAN Share URL: http://${ips[0]}:${PORT}/`);
    }
});
