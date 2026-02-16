const path = require('path');
const fs = require('fs');

function requireShoonyaSdk() {
  const localRestApi = path.join(__dirname, '..', 'shoonya-sdk', 'lib', 'RestApi.js');

  let exists = false;
  try {
    exists = fs.existsSync(localRestApi);
  } catch (_) {
    exists = false;
  }

  if (!exists) {
    throw new Error(
      `Shoonya SDK not found at:\n- ${localRestApi}\n` +
        `Fix by copying/cloning ShoonyaApi-js into ./shoonya-sdk (so that ./shoonya-sdk/lib/RestApi.js exists).`
    );
  }

  const requireTarget = localRestApi.replace(/\.js$/i, '');
  // eslint-disable-next-line import/no-dynamic-require, global-require
  return require(requireTarget);
}

function createShoonyaClient() {
  const Api = requireShoonyaSdk();
  return new Api({});
}

module.exports = {
  createShoonyaClient,
};
