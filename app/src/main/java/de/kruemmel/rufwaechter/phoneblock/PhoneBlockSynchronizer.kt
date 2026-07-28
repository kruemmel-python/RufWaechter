package de.kruemmel.rufwaechter.phoneblock

import de.kruemmel.rufwaechter.data.AppRepository
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PhoneBlockSynchronizer(
    private val repository: AppRepository,
    private val credentialStore: PhoneBlockCredentialStore,
    private val client: PhoneBlockClient,
    private val onDataChanged: () -> Unit,
) {
    private val mutex = Mutex()

    suspend fun saveCredentials(credentials: PhoneBlockCredentials) = mutex.withLock {
        credentialStore.save(credentials)
    }

    suspend fun clearCredentials() = mutex.withLock {
        credentialStore.clear()
    }

    suspend fun synchronize(): PhoneBlockSyncResult = mutex.withLock {
        val credentials = credentialStore.load() ?: return PhoneBlockSyncResult.NotConfigured
        val now = System.currentTimeMillis()
        val reportsSent = uploadPendingReports(credentials)
        val state = repository.loadPhoneBlockSyncState()
        val fresh = state?.lastIncrementalSyncAt?.let { now - it < DAILY_SYNC_MS } == true
        if (fresh) {
            val personalDue = credentials.mode == PhoneBlockAuthMode.API_KEY &&
                (state?.lastPersonalSyncAt == null || now - state.lastPersonalSyncAt >= DAILY_SYNC_MS)
            if (personalDue) {
                val personal = synchronizePersonal(credentials, now)
                onDataChanged()
                return PhoneBlockSyncResult.Updated(
                    communityChanges = 0,
                    removed = 0,
                    personalEntries = personal.count,
                    reportsSent = reportsSent,
                    version = state?.version,
                    note = personal.note,
                )
            }
            if (reportsSent > 0) onDataChanged()
            return PhoneBlockSyncResult.UpToDate(reportsSent, state?.version)
        }

        try {
            val full = state?.version == null ||
                state.lastFullSyncAt == null ||
                now - state.lastFullSyncAt >= MONTHLY_FULL_SYNC_MS
            val payload = client.fetchCommunity(credentials, if (full) null else state.version, now)
            repository.applyPhoneBlockCommunity(
                entries = payload.entries,
                removedNumbers = payload.removals,
                full = full,
                version = payload.version,
                synchronizedAt = now,
            )

            var personalCount = 0
            var note: String? = null
            if (credentials.mode == PhoneBlockAuthMode.API_KEY) {
                val personal = synchronizePersonal(credentials, now)
                personalCount = personal.count
                note = personal.note
            } else {
                note = "Persönliche Listen benötigen laut PhoneBlock-API einen API-Schlüssel."
            }
            onDataChanged()
            PhoneBlockSyncResult.Updated(
                communityChanges = payload.entries.size,
                removed = payload.removals.size,
                personalEntries = personalCount,
                reportsSent = reportsSent,
                version = payload.version,
                note = note,
            )
        } catch (error: PhoneBlockHttpException) {
            PhoneBlockSyncResult.Failed(
                message = if (error.status == 401 || error.status == 403) {
                    "PhoneBlock hat die Zugangsdaten abgelehnt."
                } else {
                    "PhoneBlock antwortete mit HTTP ${error.status}."
                },
                retryable = error.retryable,
            )
        } catch (_: IOException) {
            PhoneBlockSyncResult.Failed("PhoneBlock ist derzeit nicht erreichbar.", retryable = true)
        } catch (error: Exception) {
            PhoneBlockSyncResult.Failed(
                error.message?.take(200) ?: "PhoneBlock-Daten konnten nicht verarbeitet werden.",
                retryable = false,
            )
        }
    }

    private suspend fun uploadPendingReports(credentials: PhoneBlockCredentials): Int {
        var sent = 0
        for (report in repository.pendingPhoneBlockReports()) {
            try {
                client.report(credentials, report)
                repository.markPhoneBlockReportSent(report.normalizedNumber)
                sent++
            } catch (error: PhoneBlockHttpException) {
                repository.markPhoneBlockReportFailed(report.normalizedNumber)
                if (error.retryable || error.status == 401 || error.status == 403) break
            } catch (_: IOException) {
                repository.markPhoneBlockReportFailed(report.normalizedNumber)
                break
            }
        }
        return sent
    }

    private suspend fun synchronizePersonal(
        credentials: PhoneBlockCredentials,
        synchronizedAt: Long,
    ): PersonalSyncOutcome = try {
        val blacklist = client.fetchPersonal(credentials, PhoneBlockListType.BLACKLIST, synchronizedAt)
        val whitelist = client.fetchPersonal(credentials, PhoneBlockListType.WHITELIST, synchronizedAt)
        repository.replacePhoneBlockPersonalEntries(blacklist, whitelist, synchronizedAt)
        PersonalSyncOutcome(blacklist.size + whitelist.size, null)
    } catch (error: PhoneBlockHttpException) {
        PersonalSyncOutcome(
            0,
            if (error.status == 401 || error.status == 403) {
                "Persönliche PhoneBlock-Listen konnten mit diesem Zugang nicht gelesen werden."
            } else {
                "Persönliche PhoneBlock-Listen antworteten mit HTTP ${error.status}."
            },
        )
    } catch (_: IOException) {
        PersonalSyncOutcome(0, "Persönliche PhoneBlock-Listen waren nicht erreichbar.")
    } catch (error: Exception) {
        PersonalSyncOutcome(0, error.message?.take(160) ?: "Persönliche PhoneBlock-Listen waren ungültig.")
    }

    private data class PersonalSyncOutcome(val count: Int, val note: String?)

    companion object {
        private const val DAILY_SYNC_MS = 24L * 60 * 60 * 1000
        private const val MONTHLY_FULL_SYNC_MS = 30L * DAILY_SYNC_MS
    }
}
