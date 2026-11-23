/**
 * Rezona Web Ads Configuration (Website ONLY — Android APK stays 100% ad-free!)
 *
 * Cara aktifin duit iklan di web:
 * 1) Adsterra (paling gampang & langsung approve buat domain .vercel.app):
 *    - Daftar Publisher di https://publishers.adsterra.com
 *    - Buat unit "Native Banner" atau "Banner 320x50" -> copy URL script-nya
 *    - Paste URL script ke `adsterraBannerScriptUrl` (dan/atau `adsterraSocialBarScriptUrl`)
 *
 * 2) Monetag (alternatif cepat approve untuk .vercel.app):
 *    - Paste script URL ke `customScriptUrl`
 *
 * 3) Google AdSense (kalau sudah punya domain custom & akun AdSense):
 *    - Isi `adsenseClient` ("ca-pub-XXXXXXXXXXXXXXXX") & `adsenseSlot` ("1234567890")
 */
window.REZONA_ADS_CONFIG = {
    // Option A: Google AdSense
    adsenseClient: "", // contoh: "ca-pub-1234567890123456"
    adsenseSlot: "",   // contoh: "9876543210"

    // Option B: Adsterra / Monetag banner script URL
    bannerScriptUrl: "", // contoh: "https://plXXXXXX.profitablegatecpm.com/.../invoke.js"
    bannerContainerId: "", // contoh: "container-xxxxxxxxxxxxxxxx" (kalau pakai Native Banner Adsterra)

    // Option C: Optional floating social bar / vignette script URL
    socialBarScriptUrl: ""
};
