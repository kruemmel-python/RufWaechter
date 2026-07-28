package de.kruemmel.rufwaechter.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import android.os.Build
import de.kruemmel.rufwaechter.RufWaechterApplication
import de.kruemmel.rufwaechter.domain.CarrierVerification
import de.kruemmel.rufwaechter.domain.PhoneIdentity
import de.kruemmel.rufwaechter.domain.ScreeningAction
import de.kruemmel.rufwaechter.domain.ScreeningDecision
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class RufWaechterScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) return
        val responded = AtomicBoolean(false)
        val container = (application as RufWaechterApplication).container
        val handle = callDetails.handle
        val raw = handle?.schemeSpecificPart
        val identity = try {
            container.phoneNumberParser.parse(raw, handle?.scheme)
        } catch (_: Exception) {
            PhoneIdentity.UnknownNumber
        }
        val verification = if (Build.VERSION.SDK_INT >= 30) {
            callDetails.callerNumberVerificationStatus.toDomainVerification()
        } else {
            CarrierVerification.NOT_VERIFIED
        }
        val decision = SafeScreeningEvaluator().evaluateIncoming(
            identity,
            verification,
            container.snapshotStore.current(),
        ) { evaluatedIdentity, evaluatedVerification, snapshot ->
            container.screeningEngine.evaluate(evaluatedIdentity, evaluatedVerification, snapshot)
        }
        if (responded.compareAndSet(false, true)) {
            respondToCall(callDetails, decision.toCallResponse())
        }
        container.applicationScope.launch {
            runCatching {
                container.repository.recordDecision(
                    identity,
                    identity.displayValue(),
                    decision,
                    verification,
                )
                if (container.snapshotStore.current().settings.notificationsEnabled) {
                    DecisionNotifier(this@RufWaechterScreeningService).show(decision.action, identity.displayValue())
                }
            }
        }
    }

    private fun ScreeningDecision.toCallResponse(): CallResponse =
        CallResponse.Builder()
            .setDisallowCall(action == ScreeningAction.BLOCK)
            .setRejectCall(action == ScreeningAction.BLOCK)
            .setSilenceCall(action == ScreeningAction.SILENCE)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

    private fun Int.toDomainVerification(): CarrierVerification = when (this) {
        Connection.VERIFICATION_STATUS_PASSED -> CarrierVerification.PASSED
        Connection.VERIFICATION_STATUS_FAILED -> CarrierVerification.FAILED
        else -> CarrierVerification.NOT_VERIFIED
    }

    private fun PhoneIdentity.displayValue(): String = when (this) {
        is PhoneIdentity.Number -> normalized.value
        PhoneIdentity.PrivateNumber -> "Privat"
        PhoneIdentity.UnknownNumber -> "Unbekannt"
        is PhoneIdentity.UnsupportedHandle -> "Nicht unterstützter Anruf"
    }
}
