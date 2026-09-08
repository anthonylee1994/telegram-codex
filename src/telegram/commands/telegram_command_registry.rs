use std::sync::LazyLock;

use regex::Regex;

use crate::telegram::shared::inbound_message::InboundMessage;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TelegramCommand {
    Start,
    NewSession,
    Help,
    Status,
    Session,
    Memory,
    Forget,
    Compact,
}

/// `/help` and `/help@SomeBot` both resolve to the same command.
static COMMANDS: LazyLock<Vec<(Regex, TelegramCommand)>> = LazyLock::new(|| {
    [
        ("start", TelegramCommand::Start),
        ("new", TelegramCommand::NewSession),
        ("help", TelegramCommand::Help),
        ("status", TelegramCommand::Status),
        ("session", TelegramCommand::Session),
        ("memory", TelegramCommand::Memory),
        ("forget", TelegramCommand::Forget),
        ("compact", TelegramCommand::Compact),
    ]
    .into_iter()
    .map(|(name, command)| (Regex::new(&format!(r"^/{name}(?:@[\w_]+)?$")).unwrap(), command))
    .collect()
});

#[derive(Default)]
pub struct TelegramCommandRegistry;

impl TelegramCommandRegistry {
    pub fn new() -> Self {
        Self
    }

    pub fn resolve(&self, message: &InboundMessage) -> Option<TelegramCommand> {
        let text = message.text_or_empty();
        COMMANDS.iter().find(|(pattern, _)| pattern.is_match(text)).map(|(_, command)| *command)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::telegram::shared::inbound_message::InboundMessageParams;

    fn message(text: &str) -> InboundMessage {
        InboundMessage::new(InboundMessageParams {
            chat_id: "3".to_owned(),
            message_id: 1,
            text: Some(text.to_owned()),
            user_id: "5".to_owned(),
            update_id: 1,
            ..Default::default()
        })
    }

    #[test]
    fn resolves_plain_and_mentioned_commands() {
        let registry = TelegramCommandRegistry::new();
        assert_eq!(registry.resolve(&message("/help")), Some(TelegramCommand::Help));
        assert_eq!(registry.resolve(&message("/compact@On99AppBot")), Some(TelegramCommand::Compact));
        assert_eq!(registry.resolve(&message("/new")), Some(TelegramCommand::NewSession));
    }

    #[test]
    fn ignores_non_commands() {
        let registry = TelegramCommandRegistry::new();
        assert_eq!(registry.resolve(&message("hello")), None);
        assert_eq!(registry.resolve(&message("/help me")), None);
    }
}
