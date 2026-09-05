export function fmtBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / 1024 / 1024).toFixed(2)} MB`;
}

export function fmtTime(ts: number, withMs = true): string {
  const d = new Date(ts);
  const p = (x: number, l = 2) => String(x).padStart(l, '0');
  const base = `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
  return withMs ? `${base}.${p(d.getMilliseconds(), 3)}` : base;
}

export function fmtDuration(ms: number): string {
  const s = Math.floor(ms / 1000);
  const p = (x: number) => String(x).padStart(2, '0');
  return `${p(Math.floor(s / 3600))}:${p(Math.floor((s % 3600) / 60))}:${p(s % 60)}`;
}

export function hexToBytes(hex: string): Uint8Array {
  const clean = hex.replace(/[^0-9a-fA-F]/g, '');
  const out = new Uint8Array(clean.length / 2);
  for (let i = 0; i < out.length; i++) out[i] = parseInt(clean.substr(i * 2, 2), 16);
  return out;
}

/** xxd-style dump rendered client-side. */
export function hexdump(hex: string): string {
  const bytes = hexToBytes(hex);
  const lines: string[] = [];
  for (let off = 0; off < bytes.length; off += 16) {
    const chunk = bytes.slice(off, off + 16);
    let h = '';
    for (let i = 0; i < 16; i++) {
      if (i === 8) h += ' ';
      h += i < chunk.length ? chunk[i].toString(16).padStart(2, '0').toUpperCase() + ' ' : '   ';
    }
    const ascii = Array.from(chunk, (b) => (b >= 0x20 && b < 0x7f ? String.fromCharCode(b) : '.')).join('');
    lines.push(`${off.toString(16).padStart(8, '0').toUpperCase()}  ${h} ${ascii}`);
  }
  return lines.join('\n');
}

export function isHex(s: string): boolean {
  const clean = s.replace(/0x/gi, '').replace(/[\s,]/g, '');
  return clean.length % 2 === 0 && /^[0-9a-fA-F]*$/.test(clean);
}
