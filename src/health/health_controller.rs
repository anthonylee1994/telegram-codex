use axum::Json;
use serde::Serialize;

#[derive(Clone, Copy, Debug, Eq, PartialEq, Serialize)]
pub struct ApiStatusResponse {
    pub ok: bool,
}

/// `GET /health`
pub async fn show() -> Json<ApiStatusResponse> {
    Json(ApiStatusResponse { ok: true })
}
