use std::sync::Arc;

use anyhow::Result;

use crate::conversation::session::session_service::SessionService;
use crate::telegram::commands::compact_command_executor::CompactCommandExecutor;
use crate::telegram::commands::telegram_command_registry::{TelegramCommand, TelegramCommandRegistry};
use crate::telegram::commands::telegram_command_responder::TelegramCommandResponder;
use crate::telegram::commands::telegram_status_message_builder::TelegramStatusMessageBuilder;
use crate::telegram::shared::inbound_message::InboundMessage;
use crate::telegram::shared::message_constants;

pub struct TelegramCommandHandler {
    command_registry: Arc<TelegramCommandRegistry>,
    session_service: Arc<SessionService>,
    message_builder: Arc<TelegramStatusMessageBuilder>,
    responder: Arc<TelegramCommandResponder>,
    compact_command_executor: Arc<CompactCommandExecutor>,
}

impl TelegramCommandHandler {
    pub fn new(
        command_registry: Arc<TelegramCommandRegistry>,
        session_service: Arc<SessionService>,
        message_builder: Arc<TelegramStatusMessageBuilder>,
        responder: Arc<TelegramCommandResponder>,
        compact_command_executor: Arc<CompactCommandExecutor>,
    ) -> Self {
        Self {
            command_registry,
            session_service,
            message_builder,
            responder,
            compact_command_executor,
        }
    }

    /// Returns `true` when the message was a command and has been answered.
    pub async fn handle(&self, message: &InboundMessage) -> Result<bool> {
        let Some(command) = self.command_registry.resolve(message) else {
            return Ok(false);
        };
        match command {
            TelegramCommand::Start => {
                self.session_service.reset(&message.chat_id).await?;
                self.responder.reply(message, &message_constants::START_MESSAGE).await?;
            }
            TelegramCommand::NewSession => {
                self.session_service.reset(&message.chat_id).await?;
                self.responder.reply(message, message_constants::NEW_SESSION_MESSAGE).await?;
            }
            TelegramCommand::Help => {
                self.responder.reply(message, &message_constants::HELP_MESSAGE).await?;
            }
            TelegramCommand::Status => {
                let text = self.message_builder.build_status_message(&message.chat_id).await?;
                self.responder.reply(message, &text).await?;
            }
            TelegramCommand::Session => {
                let text = self.message_builder.build_session_message(&message.chat_id).await?;
                self.responder.reply(message, &text).await?;
            }
            TelegramCommand::Memory => {
                let text = self.message_builder.build_memory_message(&message.chat_id).await?;
                self.responder.reply(message, &text).await?;
            }
            TelegramCommand::Forget => {
                self.session_service.reset_memory(&message.chat_id).await?;
                self.responder.reply(message, message_constants::RESET_MEMORY_MESSAGE).await?;
            }
            TelegramCommand::Compact => {
                self.compact_command_executor.execute(message).await?;
            }
        }
        Ok(true)
    }
}
