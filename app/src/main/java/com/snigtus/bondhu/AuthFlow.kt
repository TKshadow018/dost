package com.snigtus.dost

enum class AuthValidationError { INVALID_EMAIL, PASSWORD_REQUIRED, PASSWORD_TOO_SHORT }

fun isValidAuthEmail(email: String): Boolean =
    email.trim().matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))

fun authValidationError(email: String, password: String, creatingAccount: Boolean): AuthValidationError? {
    if (!isValidAuthEmail(email)) return AuthValidationError.INVALID_EMAIL
    if (password.isBlank()) return AuthValidationError.PASSWORD_REQUIRED
    if (creatingAccount && password.length < 6) return AuthValidationError.PASSWORD_TOO_SHORT
    return null
}

fun canUseLocalProfile(linkedAccountId: String, authenticatedAccountId: String): Boolean =
    linkedAccountId.isBlank() || linkedAccountId == authenticatedAccountId

fun authenticatedDestination(hasEligibleProfile: Boolean): AppScreen =
    if (hasEligibleProfile) AppScreen.HOME else AppScreen.REGISTER

fun localizedAuthText(language: AppLanguage, english: String, bangla: String, hindi: String): String = when (language) {
    AppLanguage.ENGLISH -> english
    AppLanguage.BANGLA -> bangla
    AppLanguage.HINDI -> hindi
}
