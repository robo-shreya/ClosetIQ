/**
 * Derive undertone and Fitzpatrick type from a skin colour.
 *
 * YouCam's Skin Tone Analysis does not return either one — it returns hex colours
 * (skin, eye, lip, eyebrow, hair). Both values our app needs are computed from
 * `skin_color` here, so the contract the Android app sees stays identical whether the
 * provider is YouCam or the Gemma mock.
 */

function hexToRgb(hex) {
    const clean = String(hex || "").replace("#", "").trim();
    if (clean.length !== 6) return null;

    const value = Number.parseInt(clean, 16);
    if (!Number.isFinite(value)) return null;

    return {
        r: (value >> 16) & 0xff,
        g: (value >> 8) & 0xff,
        b: value & 0xff
    };
}

/** sRGB to CIELAB (D65). Same maths as ColorMath.kt on the Android side. */
function rgbToLab({ r, g, b }) {
    const linearise = (channel) => {
        const c = channel / 255;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    };

    const rl = linearise(r);
    const gl = linearise(g);
    const bl = linearise(b);

    const x = (rl * 0.4124564 + gl * 0.3575761 + bl * 0.1804375) / 0.95047;
    const y = rl * 0.2126729 + gl * 0.7151522 + bl * 0.0721750;
    const z = (rl * 0.0193339 + gl * 0.1191920 + bl * 0.9503041) / 1.08883;

    const f = (t) => (t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16 / 116);

    return {
        L: 116 * f(y) - 16,
        a: 500 * (f(x) - f(y)),
        b: 200 * (f(y) - f(z))
    };
}

/**
 * Individual Typology Angle — the standard dermatological way to place a skin colour
 * on the Fitzpatrick scale from a measured colour rather than a questionnaire.
 *
 *   ITA° = atan((L* - 50) / b*) in degrees
 *
 * Higher angle means lighter skin. Boundaries are the published ones.
 */
function itaToFitzpatrick(ita) {
    if (ita > 55) return 1;   // very light
    if (ita > 41) return 2;   // light
    if (ita > 28) return 3;   // intermediate
    if (ita > 10) return 4;   // tan
    if (ita > -30) return 5;  // brown
    return 6;                 // dark
}

/**
 * Undertone from the hue angle in Lab.
 *
 * Skin hues sit in a narrow band, roughly 40-60°. Within it, a higher angle leans
 * yellow/golden (warm) and a lower one leans red/pink (cool). The thresholds are
 * tunable because this is a judgement call, not a standard — override with
 * UNDERTONE_WARM_ABOVE / UNDERTONE_COOL_BELOW in .env if it misreads you.
 */
function hueToUndertone(hueDegrees) {
    const warmAbove = Number(process.env.UNDERTONE_WARM_ABOVE || 54);
    const coolBelow = Number(process.env.UNDERTONE_COOL_BELOW || 46);

    if (hueDegrees >= warmAbove) return "WARM";
    if (hueDegrees <= coolBelow) return "COOL";
    return "NEUTRAL";
}

/**
 * @param {string} skinHex e.g. "#b49176"
 * @returns {{undertone: string, fitzpatrick: number, lab: object, ita: number, hue: number}|null}
 */
function analyseSkinColor(skinHex) {
    const rgb = hexToRgb(skinHex);
    if (!rgb) return null;

    const lab = rgbToLab(rgb);

    // Real skin always has a clearly positive b*, but a greyscale or blown-out photo
    // can land near zero and send this to Infinity. Clamp rather than divide by ~0.
    const safeB = Math.max(Math.abs(lab.b), 0.5) * (lab.b < 0 ? -1 : 1);
    const ita = (Math.atan((lab.L - 50) / safeB) * 180) / Math.PI;

    let hue = (Math.atan2(lab.b, lab.a) * 180) / Math.PI;
    if (hue < 0) hue += 360;

    return {
        undertone: hueToUndertone(hue),
        fitzpatrick: itaToFitzpatrick(ita),
        lab: { L: round1(lab.L), a: round1(lab.a), b: round1(lab.b) },
        ita: round1(ita),
        hue: round1(hue)
    };
}

function round1(n) {
    return Math.round(n * 10) / 10;
}

module.exports = { analyseSkinColor, hexToRgb, rgbToLab, itaToFitzpatrick, hueToUndertone };
