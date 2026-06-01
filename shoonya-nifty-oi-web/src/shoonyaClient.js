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
      `Noren API SDK not found at:\n- ${localRestApi}\n` +
        `Clone https://github.com/kambalatech/NorenApi-js into ./shoonya-sdk (lib/RestApi.js must exist).`
    );
  }

  const requireTarget = localRestApi.replace(/\.js$/i, '');
  // eslint-disable-next-line import/no-dynamic-require, global-require
  return require(requireTarget);
}

function createShoonyaClient(overrides = {}) {
  const Api = requireShoonyaSdk();
  return new Api({
    Access_token: overrides.access_token || process.env.SHOONYA_ACCESS_TOKEN || '',
    UID: overrides.uid || process.env.SHOONYA_USERID || '',
    AID: overrides.account_id || process.env.SHOONYA_ACCOUNT_ID || process.env.SHOONYA_USERID || '',
    oauth_url: overrides.oauth_url || process.env.SHOONYA_OAUTH_URL || 'https://api.shoonya.com/OAuthlogin',
    client_id: overrides.client_id || process.env.SHOONYA_CLIENT_ID || '',
    Secret_Code: overrides.secret_code || process.env.SHOONYA_SECRET_CODE || '',
  });
}

module.exports = {
  createShoonyaClient,
};
