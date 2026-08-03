//Both providers should expose:
// createTryOnTask(input)
//  getTryOnTask(taskId)

async function createTryOnTask(input) {
    throw new Error("YouCam provider is not configured yet");
}

async function getTryOnTask(taskId) {
    throw new Error("YouCam provider is not configured yet");
}

module.exports = {
    createTryOnTask,
    getTryOnTask
};