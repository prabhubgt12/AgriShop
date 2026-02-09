const path = require('path');

function requireShoonyaSdk() {
  // Provide the official SDK path via env var (recommended):
  //   SHOONYA_SDK_PATH=D:\\Shoonya\\ShoonyaApi-js
  // The code expects: <SHOONYA_SDK_PATH>\lib\RestApi.js
  const envPath = process.env.SHOONYA_SDK_PATH;

  const resolvedPath = envPath
    ? path.join(envPath, 'lib', 'RestApi')
    : path.join(__dirname, '..', 'shoonya-sdk', 'lib', 'RestApi');

  // eslint-disable-next-line import/no-dynamic-require, global-require
  return require(resolvedPath);
}

function createShoonyaClient() {
  const Api = requireShoonyaSdk();
  return new Api({});
}

module.exports = {
  createShoonyaClient,
};
