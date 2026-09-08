#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ReplyResult {
    pub conversation_state: Option<String>,
    pub suggested_replies: Vec<String>,
    pub text: String,
}
