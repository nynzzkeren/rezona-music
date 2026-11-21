const DONATE_MENU = [
    { id: 'coffee', emoji: '☕', name: 'Black Coffee', price: 5000 },
    { id: 'matcha', emoji: '🍵', name: 'Matcha Latte', price: 10000 },
    { id: 'noodles', emoji: '🍜', name: 'Mie Ayam / Ramen', price: 12000 },
    { id: 'boba', emoji: '🧋', name: 'Brown Sugar Boba', price: 15000 },
    { id: 'nasgor', emoji: '🍛', name: 'Nasi Goreng', price: 20000 },
    { id: 'cake', emoji: '🍰', name: 'Midnight Cheesecake', price: 20000 }
];

function formatRupiah(amount) {
    return 'Rp ' + Number(amount || 0).toLocaleString('id-ID');
}

module.exports = async function handler(req, res) {
    if (req.method !== 'POST') {
        res.setHeader('Allow', 'POST');
        return res.status(405).json({ ok: false, error: 'Method not allowed' });
    }

    try {
        const body = typeof req.body === 'string' ? JSON.parse(req.body || '{}') : (req.body || {});
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
            return res.status(400).json({ ok: false, error: 'Pick at least one snack or drink first.' });
        }

        const webhookUrl = (process.env.DISCORD_WEBHOOK_URL || '').trim();
        if (!webhookUrl) {
            return res.status(503).json({
                ok: false,
                error: 'DISCORD_WEBHOOK_URL environment variable is not configured on Vercel yet.'
            });
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
                footer: { text: 'Rezona Official Web · Nynzz. Store QRIS' },
                timestamp: new Date().toISOString()
            }]
        };

        const discordRes = await fetch(webhookUrl, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(embedPayload)
        });

        if (!discordRes.ok) {
            return res.status(502).json({
                ok: false,
                error: `Discord returned HTTP ${discordRes.status}`
            });
        }

        return res.status(200).json({
            ok: true,
            webhookSent: true,
            totalFormatted: formatRupiah(totalAmount),
            notice: 'Thank you! Support alert delivered straight to Discord.'
        });
    } catch (err) {
        return res.status(500).json({ ok: false, error: err.message || 'Server error' });
    }
};
