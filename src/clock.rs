use std::time::{SystemTime, UNIX_EPOCH};

/// Equivalent of `Date.now()`.
pub fn now_ms() -> i64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|elapsed| elapsed.as_millis() as i64).unwrap_or(0)
}
