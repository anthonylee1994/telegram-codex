use chrono::{DateTime, FixedOffset};

pub const MIN_TRANSCRIPT_SIZE_FOR_COMPACT: usize = 4;
pub const PROCESSED_UPDATE_PRUNE_INTERVAL_MS: i64 = 6 * 60 * 60 * 1000;
pub const PROCESSED_UPDATE_RETENTION_MS: i64 = 30 * 24 * 60 * 60 * 1000;

const HONG_KONG_UTC_OFFSET_SECONDS: i32 = 8 * 3600;

/// Matches the `sv-SE` / `Asia/Hong_Kong` output the TypeScript version used,
/// e.g. `2026-09-08 16:03:45 GMT+8`. Hong Kong has no daylight saving, so a
/// fixed offset is sufficient.
pub fn format_conversation_time(epoch_millis: i64) -> String {
    let offset = FixedOffset::east_opt(HONG_KONG_UTC_OFFSET_SECONDS).expect("valid fixed offset");
    match DateTime::from_timestamp_millis(epoch_millis) {
        Some(timestamp) => format!("{} GMT+8", timestamp.with_timezone(&offset).format("%Y-%m-%d %H:%M:%S")),
        None => String::new(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn formats_hong_kong_time() {
        assert_eq!(format_conversation_time(0), "1970-01-01 08:00:00 GMT+8");
    }
}
