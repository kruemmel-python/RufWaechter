package de.kruemmel.rufwaechter.domain

class PhoneNumberParser(
    private val defaultCountryCallingCode: String = "49",
) {
    fun parse(raw: String?, scheme: String? = "tel"): PhoneIdentity {
        if (scheme != null && !scheme.equals("tel", ignoreCase = true)) {
            return PhoneIdentity.UnsupportedHandle(scheme)
        }
        if (raw == null) return PhoneIdentity.UnknownNumber
        val trimmed = raw.trim().removePrefixIgnoreCase("tel:").trim()
        if (trimmed.isEmpty()) return PhoneIdentity.UnknownNumber
        if (trimmed.equals("private", true) || trimmed.equals("anonymous", true) ||
            trimmed.equals("withheld", true) || trimmed == "-1"
        ) {
            return PhoneIdentity.PrivateNumber
        }
        if (trimmed.length > MAX_RAW_LENGTH) return PhoneIdentity.UnknownNumber
        if (trimmed.any { it.isLetter() }) return PhoneIdentity.UnknownNumber
        if (trimmed.count { it == '+' } > 1 || trimmed.drop(1).contains('+')) return PhoneIdentity.UnknownNumber
        if (trimmed.any { !it.isDigit() && it !in " +-()./" }) return PhoneIdentity.UnknownNumber

        val hasLeadingPlus = trimmed.startsWith("+")
        val digits = trimmed.filter(Char::isDigit)
        if (digits.length !in MIN_DIGITS..MAX_DIGITS) return PhoneIdentity.UnknownNumber
        val canonical = when {
            hasLeadingPlus -> "+$digits"
            digits.startsWith("00") -> "+${digits.drop(2)}"
            digits.startsWith("0") -> "+$defaultCountryCallingCode${digits.drop(1)}"
            else -> "+$defaultCountryCallingCode$digits"
        }
        return NormalizedPhoneNumber.fromCanonical(canonical)
            ?.let(PhoneIdentity::Number)
            ?: PhoneIdentity.UnknownNumber
    }

    private fun String.removePrefixIgnoreCase(prefix: String): String =
        if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this

    companion object {
        const val MAX_RAW_LENGTH = 64
        const val MAX_DIGITS = 15
        const val MIN_DIGITS = 6
    }
}
