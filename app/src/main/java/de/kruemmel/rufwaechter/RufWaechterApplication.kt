package de.kruemmel.rufwaechter

import android.app.Application
import androidx.room.Room
import de.kruemmel.rufwaechter.data.AppRepository
import de.kruemmel.rufwaechter.data.RufWaechterDatabase
import de.kruemmel.rufwaechter.domain.PhoneNumberParser
import de.kruemmel.rufwaechter.domain.ScreeningEngine
import de.kruemmel.rufwaechter.domain.ScreeningSnapshot
import de.kruemmel.rufwaechter.importexport.ImportExportManager
import de.kruemmel.rufwaechter.importexport.ReputationJsonCodec
import de.kruemmel.rufwaechter.screening.ScreeningSnapshotStore
import de.kruemmel.rufwaechter.settings.SettingsRepository
import de.kruemmel.rufwaechter.reputation.WorkScheduler
import de.kruemmel.rufwaechter.phoneblock.PhoneBlockClient
import de.kruemmel.rufwaechter.phoneblock.PhoneBlockCredentialStore
import de.kruemmel.rufwaechter.phoneblock.PhoneBlockJsonCodec
import de.kruemmel.rufwaechter.phoneblock.PhoneBlockSynchronizer
import de.kruemmel.rufwaechter.phoneblock.toScreeningRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RufWaechterApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }
}

class AppContainer(application: Application) {
    private val app = application
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database: RufWaechterDatabase = Room.databaseBuilder(
        application,
        RufWaechterDatabase::class.java,
        "rufwaechter.db",
    ).addMigrations(RufWaechterDatabase.MIGRATION_1_2).build()
    val repository = AppRepository(database)
    val settingsRepository = SettingsRepository(application)
    val phoneNumberParser = PhoneNumberParser()
    val screeningEngine = ScreeningEngine()
    val snapshotStore = ScreeningSnapshotStore()
    val phoneBlockCredentialStore = PhoneBlockCredentialStore(application)
    val phoneBlockSynchronizer = PhoneBlockSynchronizer(
        repository = repository,
        credentialStore = phoneBlockCredentialStore,
        client = PhoneBlockClient(PhoneBlockJsonCodec(phoneNumberParser)),
        onDataChanged = ::rebuildSnapshot,
    )
    val importExportManager = ImportExportManager(
        ReputationJsonCodec(phoneNumberParser),
        repository,
    )

    fun start() {
        applicationScope.launch {
            settingsRepository.settings.collectLatest { WorkScheduler(app).apply(it) }
        }
        applicationScope.launch {
            combine(
                repository.rules,
                repository.phoneBlockEntries,
                settingsRepository.settings,
            ) { rules, phoneBlockEntries, settings ->
                Triple(rules, phoneBlockEntries, settings)
            }
                .collectLatest { (rules, phoneBlockEntries, settings) ->
                    val now = System.currentTimeMillis()
                    val reputation = repository.loadReputation(now)
                    val externalRules = if (settings.phoneBlockEnabled) {
                        phoneBlockEntries.map { it.toScreeningRule() }
                    } else {
                        emptyList()
                    }
                    snapshotStore.install(
                        ScreeningSnapshot.compile(
                            rules = rules + externalRules,
                            reputation = reputation,
                            settings = settings,
                            version = now,
                        ),
                    )
                }
        }
    }

    fun rebuildSnapshot() {
        applicationScope.launch {
            val settings = settingsRepository.settings.first()
            val now = System.currentTimeMillis()
            val externalRules = if (settings.phoneBlockEnabled) {
                repository.loadPhoneBlockEntries().map { it.toScreeningRule() }
            } else {
                emptyList()
            }
            snapshotStore.install(
                ScreeningSnapshot.compile(
                    repository.loadRules() + externalRules,
                    repository.loadReputation(now),
                    settings,
                    now,
                ),
            )
        }
    }
}
