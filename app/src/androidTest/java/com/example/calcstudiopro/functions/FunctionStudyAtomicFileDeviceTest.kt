package com.example.calcstudiopro.functions

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.calcstudiopro.domain.functions.MathExpressionEngine
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.domain.FunctionStudy
import com.example.calcstudiopro.functions.domain.FunctionStudyId
import com.example.calcstudiopro.functions.persistence.FunctionStudyStore
import com.example.calcstudiopro.functions.persistence.StudyStoreResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.file.Files
import java.util.Comparator
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FunctionStudyAtomicFileDeviceTest {
    @Test fun atomicallyReplacesAnExistingCspfFileInAppInternalStorage() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val directory = context.filesDir.toPath().resolve("f1-5-atomic-${UUID.randomUUID()}")
        try {
            val store = FunctionStudyStore(directory, MathExpressionEngine())
            val first = FunctionStudy.newWorkingSession(FunctionStudyId("android-atomic"))
                .withDraft(FunctionSlotId.F1, "x+1")
            assertTrue(store.saveWorkingSession(first) is StudyStoreResult.Success)
            val firstRevision = when (val loaded = store.loadWorkingSession()) {
                is StudyStoreResult.Success -> loaded.value!!.revision()
                is StudyStoreResult.Failure -> error("Initial Android read failed: ${loaded.error}")
            }

            val replacement = first.withDraft(FunctionSlotId.F1, "sin(")
                .withDraft(FunctionSlotId.F6, "a*x+")
            assertTrue(store.saveWorkingSession(replacement, firstRevision) is StudyStoreResult.Success)

            val final = when (val loaded = store.loadWorkingSession()) {
                is StudyStoreResult.Success -> loaded.value!!
                is StudyStoreResult.Failure -> error("Replacement Android read failed: ${loaded.error}")
            }
            assertEquals(replacement.revision(), final.revision())
            assertEquals("sin(", final.slot(FunctionSlotId.F1).draft)
            assertEquals("a*x+", final.slot(FunctionSlotId.F6).draft)
            assertFalse(Files.list(directory).use { files -> files.anyMatch { it.fileName.toString().endsWith(".tmp") } })
            assertTrue(Files.exists(directory.resolve("working.cspf")))
        } finally {
            if (Files.exists(directory)) {
                Files.walk(directory).use { files ->
                    files.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
                }
            }
        }
    }
}
