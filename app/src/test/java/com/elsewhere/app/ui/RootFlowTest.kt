package com.elsewhere.app.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.elsewhere.app.Fakes
import com.elsewhere.app.Fx
import com.elsewhere.app.data.Home
import com.elsewhere.app.data.Profile
import com.elsewhere.app.data.Recording
import com.elsewhere.app.data.Units
import com.elsewhere.app.data.Visit
import com.elsewhere.app.ui.onboard.WaitingRoute
import com.elsewhere.app.ui.onboard.WaitingViewModel
import com.elsewhere.app.ui.record.Perm
import com.elsewhere.app.ui.record.PermRow
import com.elsewhere.app.ui.record.PermState
import com.elsewhere.app.ui.record.RecordActions
import com.elsewhere.app.ui.record.RecordStepScreen
import com.elsewhere.app.ui.record.RecordUi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import org.junit.Assert.assertEquals
import com.elsewhere.app.ingest.KeyVerifier
import com.elsewhere.app.ui.onboard.OnboardViewModel
import com.elsewhere.app.ui.onboard.OnboardingGraph
import com.elsewhere.app.ui.nav.DoorNav
import com.elsewhere.app.ui.shell.RangeStore
import com.elsewhere.app.ui.shell.RangeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.elsewhere.app.ui.shell.ShellViewModel
import com.elsewhere.app.ui.world.ElsewhereTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.elsewhere.app.data.CitiesLoader
import com.elsewhere.app.data.RecordRepository
import com.elsewhere.app.data.SecretBox
import com.elsewhere.app.data.SettingsStore
import com.elsewhere.app.data.db.ElsewhereDb
import com.elsewhere.app.data.db.SOURCE_SYNC
import com.elsewhere.app.data.db.VisitEntity
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class RootFlowTest {
    @get:Rule val rule = createComposeRule()

    private val start = Fx.utc(2024, 0, 1)
    private val record = Fx.record(
        visits = (0 until 40).map { d -> Fx.visit(start + d * Fx.D + 20 * Fx.H, start + (d + 1) * Fx.D + 8 * Fx.H, "boston", lat = 42.36, lng = -71.06) },
        places = mapOf("boston" to Fx.place(city = "Boston", cc = "US", lat = 42.36, lng = -71.06, name = "The flat")),
        range = start..Fx.utc(2024, 11, 31),
    )

    @Test fun `through the door, into the app, and back in through Change answers`() {
        val store = Fakes.Store(record); val maps = Fakes.Maps()
        // One onboarding view model for the whole activity, as Hilt scopes it.
        val control = Fakes.Control()
        val onboard = OnboardViewModel(store, maps, KeyVerifier(maps, Fakes.Checker()), Fakes.Namer(), Fakes.Geo(), control)
        val recordUi = RecordUi(Perm.entries.map { PermRow(it, PermState.ASK) }, false, Recording(), null, true, 1_800_000_000L)
        rule.setContent {
            val root = remember { RootViewModel(store, Fakes.Land, maps, Fakes.Account(), control) }
            val shell = remember { ShellViewModel(store, Fakes.Source(record), maps, Fakes.lookups(maps, CoroutineScope(Dispatchers.Main)), DoorNav(RangeStore(Fakes.Source(record), CoroutineScope(Dispatchers.Main)), CoroutineScope(Dispatchers.Main))) }
            val range = remember { RangeViewModel(RangeStore(Fakes.Source(record), CoroutineScope(Dispatchers.Main))) }
            ElsewhereTheme {
                Root(
                    vm = root,
                    door = { step, close -> OnboardingGraph(step, close, vm = onboard, record = { s, i -> RecordStepScreen(recordUi, RecordActions(onStart = s), onImport = i) }) },
                    app = { answers, key, _, _ -> AppScaffold(answers, key, {}, shell, range, content = { t, _, _ -> Placeholder(t) }) },
                )
            }
        }
        rule.onNodeWithText("ELSEWHERE RECORDS AS YOU LIVE.").assertIsDisplayed()
        rule.onNodeWithText("I HAVE A GOOGLE TIMELINE EXPORT INSTEAD →").performScrollTo().performClick()
        rule.onNodeWithText("TAP TO CHOOSE YOUR TIMELINE.JSON").assertIsDisplayed()
        onboard.import({ ByteArrayInputStream("{}".toByteArray()) }, "Timeline.json")
        rule.waitForIdle()

        rule.onNodeWithText("SKIP").performClick()                       // key
        rule.onNodeWithText("CONTINUE WITH LIMITED MAPS").assertIsNotEnabled()
        rule.onNode(isToggleable()).performClick()                       // "I understand" on limited maps
        rule.onNodeWithText("CONTINUE WITH LIMITED MAPS").performClick()
        rule.onNodeWithText("WHOSE RECORD IS THIS?").assertIsDisplayed()
        rule.onNodeWithText("NEXT").performClick()                       // name
        rule.onNode(isSelectable() and hasText("THE FLAT", substring = true)).performClick()
        rule.onNodeWithText("NEXT").performClick()                       // home
        rule.onNodeWithText("NEXT").performClick()                       // moves
        rule.onNodeWithText("SKIP").performClick()                       // work
        rule.onNodeWithText("PRINT MY RECORD").performClick()            // units
        rule.waitForIdle()

        rule.onNodeWithText("ELSEWHERE · YOUR RECORD").assertIsDisplayed()
        rule.onNodeWithContentDescription("Menu").performClick()
        rule.onNodeWithText("CHANGE ANSWERS").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("WHOSE RECORD IS THIS?").assertIsDisplayed()
    }

    /** A phone that has recorded for a few days, answered the questions, and never brought an export. */
    private fun waiting(days: Int, export: Boolean, control: Fakes.Control = Fakes.Control(Recording(true, 1_800_000_000L, 0L)), stays: Boolean = true): Fakes.Store {
        val store = Fakes.Store(record)
        store.hasImport.value = stays; store.hasHistory.value = export; store.recordedDays.value = days
        store.profile.value = Profile("Ana", Units.KM, listOf(Home(42.36, -71.06, "Boston", "US", 0, null)), emptyList())
        val maps = Fakes.Maps().apply { cur.value = com.elsewhere.app.data.Settings(limitedAccepted = true) }
        rule.setContent {
            val root = remember { RootViewModel(store, Fakes.Land, maps, Fakes.Account(), control) }
            val vm = remember { WaitingViewModel(store, control) }
            ElsewhereTheme {
                Root(vm = root, door = { _, _ -> androidx.compose.material3.Text("THE DOOR") }, app = { _, _, _, _ -> androidx.compose.material3.Text("THE APP") }, waiting = { WaitingRoute(it, vm) })
            }
        }
        rule.waitForIdle()
        return store
    }

    @Test fun `three recorded days and no export wait at the door as Day 4, until the seventh day`() {
        val store = waiting(days = 3, export = false)
        rule.onAllNodesWithText("DAY 4").onFirst().assertIsDisplayed()
        rule.onNodeWithText("Come back in a week.", substring = true).assertIsDisplayed()
        rule.onNodeWithText("THE APP").assertDoesNotExist()
        store.recordedDays.value = 7
        rule.waitForIdle()
        rule.onNodeWithText("THE APP").assertIsDisplayed()
    }

    @Test fun `an export never waits`() {
        waiting(days = 0, export = true)
        rule.onNodeWithText("THE APP").assertIsDisplayed()
        rule.onAllNodesWithText("DAY 1").assertCountEquals(0)
    }

    @Test fun `paused recording offers Resume, and a Timeline export lifts the door`() {
        val control = Fakes.Control()
        val store = waiting(days = 0, export = false, control = control)
        rule.onAllNodesWithText("DAY 1").onFirst().assertIsDisplayed()
        rule.onNodeWithText("RECORDING IS PAUSED").assertIsDisplayed()
        rule.onNodeWithText("RESUME").performClick()
        rule.waitForIdle()
        assertEquals(true, control.recording.value.enabled)
        rule.onNodeWithText("RECORDING IS PAUSED").assertDoesNotExist()
        store.hasHistory.value = true
        rule.waitForIdle()
        rule.onNodeWithText("THE APP").assertIsDisplayed()
    }

    @Test fun `a fresh recording with no stay closed yet still leaves the onboarding door for the wait`() {
        waiting(days = 0, export = false, stays = false)
        rule.onAllNodesWithText("DAY 1").onFirst().assertIsDisplayed()
        rule.onNodeWithText("THE DOOR").assertDoesNotExist()
        rule.onNodeWithText("THE APP").assertDoesNotExist()
    }

    /** A fresh phone that pulled another device's record: the stays are `sync` rows, not an export and not recorded here. */
    @Test fun `a record pulled from another device lands in the app, not at the door`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val dir = File("build/test-datastore/${UUID.randomUUID()}").apply { mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val db = Room.inMemoryDatabaseBuilder(ctx, ElsewhereDb::class.java).allowMainThreadQueries().build()
        val ds = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") }
        val repo = RecordRepository(db, SettingsStore(ds, SecretBox.inMemory()), CitiesLoader(ctx), scope)
        try {
            runBlocking {
                val pulled = (0 until 2).map { d -> VisitEntity(d, start / 1000 + d * 86400L, start / 1000 + d * 86400L + 3600L, 0, "dev:p$d", 42.36, -71.06, "UNKNOWN", 1.0, 0, null, null, null, SOURCE_SYNC) }
                repo.insertSynced(pulled, emptyMap())
                repo.saveProfile(Profile("Ana", Units.KM, listOf(Home(42.36, -71.06, "Boston", "US", 0, null)), emptyList()))
                repo.hasImport.first { it }; repo.profile.first { it != null }; repo.hasHistory.first { it }
            }
            val maps = Fakes.Maps().apply { cur.value = com.elsewhere.app.data.Settings(limitedAccepted = true) }
            rule.setContent {
                val root = remember { RootViewModel(repo, Fakes.Land, maps, Fakes.Account(), Fakes.Control()) }
                ElsewhereTheme { Root(vm = root, door = { _, _ -> androidx.compose.material3.Text("THE DOOR") }, app = { _, _, _, _ -> androidx.compose.material3.Text("THE APP") }, waiting = { androidx.compose.material3.Text("THE WAIT") }) }
            }
            rule.waitForIdle()
            rule.onNodeWithText("THE APP").assertIsDisplayed()
            rule.onNodeWithText("THE DOOR").assertDoesNotExist()
        } finally { scope.cancel(); db.close(); dir.deleteRecursively() }
    }
}
