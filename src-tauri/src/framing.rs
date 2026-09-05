//! Splits a TCP byte stream into logical frames according to the session's framing rule.

use crate::config::{Framing, FramingMode};

pub struct Framer {
    cfg: Framing,
    delimiter: Vec<u8>,
    buf: Vec<u8>,
}

impl Framer {
    pub fn new(cfg: &Framing) -> Self {
        let delimiter = crate::codec::parse_hex(&cfg.delimiter_hex).unwrap_or_else(|_| vec![b'\n']);
        Self { cfg: cfg.clone(), delimiter, buf: Vec::new() }
    }

    pub fn mode(&self) -> FramingMode {
        self.cfg.mode
    }

    /// Feed a chunk and get back complete frames. For `None` and `Timeout` the chunk is
    /// buffered or returned as-is; the caller handles the timeout flush.
    pub fn feed(&mut self, chunk: &[u8]) -> Vec<Vec<u8>> {
        match self.cfg.mode {
            FramingMode::None => vec![chunk.to_vec()],
            FramingMode::Timeout => {
                self.buf.extend_from_slice(chunk);
                vec![]
            }
            FramingMode::Delimiter => {
                self.buf.extend_from_slice(chunk);
                let mut out = vec![];
                if self.delimiter.is_empty() {
                    return vec![std::mem::take(&mut self.buf)];
                }
                loop {
                    match find(&self.buf, &self.delimiter) {
                        Some(pos) => {
                            let end = pos + self.delimiter.len();
                            let frame: Vec<u8> = self.buf.drain(..end).collect();
                            out.push(frame);
                        }
                        None => break,
                    }
                }
                out
            }
            FramingMode::Fixed => {
                self.buf.extend_from_slice(chunk);
                let n = (self.cfg.fixed_len as usize).max(1);
                let mut out = vec![];
                while self.buf.len() >= n {
                    out.push(self.buf.drain(..n).collect());
                }
                out
            }
            FramingMode::LengthPrefix => {
                self.buf.extend_from_slice(chunk);
                let off = self.cfg.len_offset as usize;
                let size = (self.cfg.len_size as usize).clamp(1, 4);
                let header = off + size;
                let mut out = vec![];
                loop {
                    if self.buf.len() < header {
                        break;
                    }
                    let field = &self.buf[off..off + size];
                    let mut len: usize = 0;
                    if self.cfg.big_endian {
                        for b in field { len = len << 8 | *b as usize; }
                    } else {
                        for b in field.iter().rev() { len = len << 8 | *b as usize; }
                    }
                    let total = if self.cfg.len_includes_header { len } else { header + len };
                    if total < header {
                        // corrupt length, flush everything as one frame to resync
                        out.push(std::mem::take(&mut self.buf));
                        break;
                    }
                    if self.buf.len() < total {
                        break;
                    }
                    out.push(self.buf.drain(..total).collect());
                }
                out
            }
        }
    }

    /// Flush whatever is buffered (timeout mode, or on disconnect).
    pub fn flush(&mut self) -> Option<Vec<u8>> {
        if self.buf.is_empty() { None } else { Some(std::mem::take(&mut self.buf)) }
    }

    pub fn has_pending(&self) -> bool {
        !self.buf.is_empty()
    }

    pub fn timeout_ms(&self) -> u64 {
        self.cfg.timeout_ms.max(1) as u64
    }
}

fn find(hay: &[u8], needle: &[u8]) -> Option<usize> {
    if needle.len() > hay.len() { return None; }
    hay.windows(needle.len()).position(|w| w == needle)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn f(mode: FramingMode) -> Framing {
        Framing { mode, ..Default::default() }
    }

    #[test]
    fn delimiter_splits() {
        let mut fr = Framer::new(&f(FramingMode::Delimiter));
        assert_eq!(fr.feed(b"ab\ncd"), vec![b"ab\n".to_vec()]);
        assert_eq!(fr.feed(b"\nef"), vec![b"cd\n".to_vec()]);
        assert_eq!(fr.flush(), Some(b"ef".to_vec()));
    }

    #[test]
    fn length_prefix_big_endian() {
        let cfg = Framing { mode: FramingMode::LengthPrefix, len_offset: 1, len_size: 2, big_endian: true, ..Default::default() };
        let mut fr = Framer::new(&cfg);
        let frames = fr.feed(&[0xAA, 0x00, 0x02, 1, 2, 0xAA, 0x00, 0x01]);
        assert_eq!(frames, vec![vec![0xAA, 0, 2, 1, 2]]);
        assert_eq!(fr.feed(&[9]), vec![vec![0xAA, 0, 1, 9]]);
    }

    #[test]
    fn fixed_len() {
        let cfg = Framing { mode: FramingMode::Fixed, fixed_len: 3, ..Default::default() };
        let mut fr = Framer::new(&cfg);
        assert_eq!(fr.feed(b"abcdefg"), vec![b"abc".to_vec(), b"def".to_vec()]);
        assert!(fr.has_pending());
    }
}
