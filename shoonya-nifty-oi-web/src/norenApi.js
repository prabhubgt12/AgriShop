/**
 * Shoonya Noren list responses are not always JSON arrays.
 */

function isRecordRow(item) {
  if (!item || typeof item !== 'object' || Array.isArray(item)) return false;
  return Boolean(
    item.tsym ||
      item.norenordno ||
      item.orderno ||
      item.token ||
      item.netqty !== undefined ||
      item.avgprc !== undefined ||
      item.status
  );
}

function normalizeNorenList(reply) {
  if (!reply) return [];
  if (Array.isArray(reply)) return reply.filter(isRecordRow);
  if (typeof reply !== 'object') return [];
  if (reply.stat === 'Not_Ok' || reply.stat === 'NOT_OK') return [];
  if (Array.isArray(reply.values)) return reply.values.filter(isRecordRow);
  if (isRecordRow(reply)) return [reply];
  const skip = new Set(['stat', 'emsg', 'request_time', 'dmsg', 'uid', 'actid']);
  const rows = Object.keys(reply)
    .filter((k) => !skip.has(k) && /^\d+$/.test(k))
    .map((k) => reply[k])
    .filter(isRecordRow);
  if (rows.length) return rows;
  return Object.values(reply).filter(isRecordRow);
}

function matchOrderNo(row, orderno) {
  if (!row || orderno == null) return false;
  const id = String(orderno);
  return [row.norenordno, row.orderno, row.order_no].some((x) => x != null && String(x) === id);
}

function orderStatusUpper(order) {
  return String(order?.status || order?.ordstatus || '').toUpperCase();
}

async function confirmOrderFill(client, orderno, { waitMs = 2000 } = {}) {
  if (!orderno) return { filled: false, state: 'unknown', message: 'No order number.' };

  if (waitMs > 0) await new Promise((r) => setTimeout(r, waitMs));

  if (client?.get_orderbook) {
    const orders = normalizeNorenList(await client.get_orderbook());
    const order = orders.find((o) => matchOrderNo(o, orderno));
    if (order) {
      const st = orderStatusUpper(order);
      if (st === 'REJECTED' || st === 'CANCELLED') {
        return { filled: false, state: 'rejected', order, message: order.rejreason || order.emsg || st };
      }
      if (st === 'COMPLETE') {
        let entryPrice = parseFloat(order.avgprc || order.prc);
        if (client?.get_tradebook) {
          const trades = normalizeNorenList(await client.get_tradebook());
          const fill = trades.find((t) => matchOrderNo(t, orderno));
          if (fill?.avgprc != null) entryPrice = parseFloat(fill.avgprc);
        }
        return {
          filled: true,
          state: 'filled',
          order,
          entryPrice: Number.isFinite(entryPrice) ? entryPrice : null,
          message: 'Order complete.',
        };
      }
      return {
        filled: false,
        state: 'pending',
        order,
        message: 'Order open/pending — may fill when price trades through your limit.',
      };
    }
  }

  if (client?.get_tradebook) {
    const trades = normalizeNorenList(await client.get_tradebook());
    const fill = trades.find((t) => matchOrderNo(t, orderno));
    if (fill) {
      const entryPrice = fill.avgprc != null ? parseFloat(fill.avgprc) : null;
      return { filled: true, state: 'filled', fill, entryPrice, message: 'Fill in tradebook.' };
    }
  }

  return { filled: false, state: 'pending', message: 'Order placed; fill not confirmed yet.' };
}

module.exports = {
  normalizeNorenList,
  confirmOrderFill,
  matchOrderNo,
  orderStatusUpper,
};
