const fs = require('fs');
const path = require('path');

function storeTrade(trade) {
  const filePath = path.join(__dirname, '..', '.data', 'trades.json');
  let trades = [];
  if (fs.existsSync(filePath)) {
    try {
      trades = JSON.parse(fs.readFileSync(filePath, 'utf8'));
    } catch (_) {
      trades = [];
    }
  }
  trades.push(trade);
  fs.writeFileSync(filePath, JSON.stringify(trades, null, 2));
}

module.exports = { storeTrade };
