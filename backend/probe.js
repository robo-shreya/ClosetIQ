/**
 * Hit the real YouCam API once and print exactly what comes back.
 *
 * The published docs do not pin down every response field, so this is how we find out
 * for certain — with one call, on one image, instead of discovering it inside the app.
 *
 *   node probe.js skin  ./selfie.jpg
 *   node probe.js tryon ./person.jpg ./garment.jpg
 *
 * Requires YOUCAM_API_KEY in backend/.env. Each run costs real credits, so it does
 * exactly one task and stops.
 */

process.loadEnvFile();
process.env.YOUCAM_DEBUG = process.env.YOUCAM_DEBUG || "1";

const fs = require("node:fs");
const path = require("node:path");

const provider = require("./providers/youCamProvider");

const POLL_INTERVAL_MS = 2000;
const TIMEOUT_MS = 120000;

function loadImage(filePath) {
    const resolved = path.resolve(filePath);

    if (!fs.existsSync(resolved)) {
        throw new Error(`No such file: ${resolved}`);
    }

    const bytes = fs.readFileSync(resolved);
    const extension = path.extname(resolved).toLowerCase();
    const mimeType = extension === ".png" ? "image/png" : "image/jpeg";

    console.log(`  ${path.basename(resolved)} — ${(bytes.length / 1024).toFixed(0)} KB, ${mimeType}`);

    if (bytes.length > 10 * 1024 * 1024) {
        throw new Error("Image is over YouCam's 10 MB limit");
    }

    return { base64: bytes.toString("base64"), mimeType };
}

async function main() {
    const [kindArg, ...files] = process.argv.slice(2);

    if (!kindArg || files.length === 0) {
        console.error("usage: node probe.js skin <selfie.jpg>");
        console.error("       node probe.js tryon <person.jpg> <garment.jpg>");
        process.exit(1);
    }

    if (!process.env.YOUCAM_API_KEY) {
        console.error("YOUCAM_API_KEY is not set in backend/.env");
        console.error("Get one at https://yce.makeupar.com/api-console/en/api-keys/");
        process.exit(1);
    }

    let input;

    if (kindArg === "skin") {
        console.log("\n== SKIN_ANALYSIS ==");
        input = { kind: "SKIN_ANALYSIS", personImage: loadImage(files[0]) };
    } else if (kindArg === "tryon") {
        if (files.length < 2) {
            console.error("tryon needs two images: person then garment");
            process.exit(1);
        }
        console.log("\n== TRY_ON ==");
        input = {
            kind: "TRY_ON",
            personImage: loadImage(files[0]),
            garmentImage: loadImage(files[1]),
            renderTarget: process.env.PROBE_TARGET || "UPPER_BODY"
        };
    } else {
        console.error(`Unknown kind: ${kindArg}. Use "skin" or "tryon".`);
        process.exit(1);
    }

    console.log("\n-- creating task --");
    const created = await provider.createTask(input);
    console.log(`task ${created.taskId}`);

    console.log("\n-- polling --");
    const startedAt = Date.now();

    while (Date.now() - startedAt < TIMEOUT_MS) {
        await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));

        const task = await provider.getTask(created.taskId);
        const seconds = ((Date.now() - startedAt) / 1000).toFixed(0);

        if (task.status === "processing") {
            process.stdout.write(`  ${seconds}s still running\n`);
            continue;
        }

        if (task.status === "failed") {
            console.error(`\nFAILED after ${seconds}s: ${task.error}`);
            process.exit(1);
        }

        console.log(`\nSUCCESS after ${seconds}s`);
        console.log("\n-- what the app will receive --");
        console.log(JSON.stringify(task.result, null, 2));

        if (task.result.skin) {
            console.log(
                "\nSanity check: these are 0..1 where HIGHER MEANS MORE of the concern.\n" +
                "If your skin is visibly clear and redness came back high, the scale is\n" +
                "inverted — set YOUCAM_INVERT_SKIN_SCORES=0 in backend/.env and re-run."
            );
        }

        return;
    }

    console.error(`\nTimed out after ${TIMEOUT_MS / 1000}s`);
    process.exit(1);
}

main().catch((error) => {
    console.error(`\nERROR: ${error.message}`);
    process.exit(1);
});
