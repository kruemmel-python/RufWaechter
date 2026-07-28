package de.kruemmel.rufwaechter.screening

import de.kruemmel.rufwaechter.domain.ScreeningSnapshot
import java.util.concurrent.atomic.AtomicReference

class ScreeningSnapshotStore {
    private val reference = AtomicReference(ScreeningSnapshot.empty())

    fun current(): ScreeningSnapshot = reference.get()

    fun install(snapshot: ScreeningSnapshot) {
        reference.set(snapshot)
    }
}
