package net.twinte.mobile_experiments.core.auth

data class TwinteSession(
    val sessionId: String,
    val cookieName: String = "twinte_session",
)
