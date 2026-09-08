use std::sync::LazyLock;

use crate::telegram::shared::telegram_types::TelegramBotCommand;

pub static BOT_COMMANDS: LazyLock<Vec<TelegramBotCommand>> = LazyLock::new(|| {
    [
        ("status", "Bot 狀態"),
        ("session", "目前 session 狀態"),
        ("memory", "長期記憶狀態"),
        ("forget", "清除長期記憶"),
        ("compact", "壓縮目前對話 context"),
        ("new", "新 session"),
        ("help", "使用說明"),
    ]
    .into_iter()
    .map(|(command, description)| TelegramBotCommand {
        command: command.to_owned(),
        description: description.to_owned(),
    })
    .collect()
});
