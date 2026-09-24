package com.suprxsidh.stride.reminders

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.RemoteInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.stride.ai.gemini.GeminiApi
import com.suprxsidh.stride.ai.gemini.GeminiCandidate
import com.suprxsidh.stride.ai.gemini.GeminiContent
import com.suprxsidh.stride.ai.gemini.GeminiFoodEstimator
import com.suprxsidh.stride.ai.gemini.GeminiGenerateContentRequest
import com.suprxsidh.stride.ai.gemini.GeminiGenerateContentResponse
import com.suprxsidh.stride.ai.gemini.GeminiPart
import com.suprxsidh.stride.data.db.StrideDatabase
import com.suprxsidh.stride.data.db.entity.ReminderEntity
import com.suprxsidh.stride.data.db.entity.WeighInEntity
import com.suprxsidh.stride.data.repository.FoodRepository
import com.suprxsidh.stride.data.repository.GeminiFoodRepository
import com.suprxsidh.stride.data.repository.ReminderRepository
import com.suprxsidh.stride.data.repository.SettingsRepository
import com.suprxsidh.stride.data.repository.WeightRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.LocalDate

/**
 * Feature E (completeness pass, spec §6): exercises [ReminderAlarmReceiver.handleAlarmFired] and
 * [ReminderAlarmReceiver.handleDirectReply] directly (bypassing `onReceive()`'s `goAsync()` +
 * detached-`CoroutineScope` plumbing, which a JUnit test can't reliably await -- see the
 * `internal` visibility comment on those functions). Dependencies are wired to in-memory-DB-
 * backed test doubles via the class's overridable `*Provider` companion vals, same seam pattern
 * [com.suprxsidh.stride.health.HealthConnectSyncWorker] already established.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderAlarmReceiverTest {
    private lateinit var db: StrideDatabase
    private lateinit var context: Context
    private lateinit var reminderRepository: ReminderRepository
    private lateinit var weightRepository: WeightRepository
    private lateinit var foodRepository: FoodRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var shadowNotificationManager: org.robolectric.shadows.ShadowNotificationManager
    private val receiver = ReminderAlarmReceiver()

    private class FakeGeminiApi(private val succeed: Boolean) : GeminiApi {
        override suspend fun generateContent(model: String, apiKey: String, request: GeminiGenerateContentRequest): GeminiGenerateContentResponse {
            if (!succeed) throw java.io.IOException("network down")
            val json = """{"items":[{"name":"2 rotis","kcal":180,"proteinG":6.0}],"totalKcal":180,"totalProteinG":6.0,"confidence":"medium"}"""
            return GeminiGenerateContentResponse(listOf(GeminiCandidate(GeminiContent(listOf(GeminiPart(text = json))))))
        }
    }

    private fun geminiRepository(succeed: Boolean): GeminiFoodRepository {
        val estimator = GeminiFoodEstimator(FakeGeminiApi(succeed))
        return GeminiFoodRepository(estimator, settingsRepository, foodRepository, db.pendingDraftDao())
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, StrideDatabase::class.java).allowMainThreadQueries().build()
        reminderRepository = ReminderRepository(db.reminderDao())
        weightRepository = WeightRepository(db.weighInDao(), clock = { java.time.LocalDateTime.of(2026, 9, 24, 12, 0) })
        foodRepository = FoodRepository(db.foodEntryDao(), db.customFoodDao())
        settingsRepository = SettingsRepository(db.appSettingsDao())

        ReminderAlarmReceiver.reminderRepositoryProvider = { reminderRepository }
        ReminderAlarmReceiver.weightRepositoryProvider = { weightRepository }
        ReminderAlarmReceiver.geminiFoodRepositoryProvider = { geminiRepository(succeed = true) }
        ReminderAlarmReceiver.pendingDraftDaoProvider = { db.pendingDraftDao() }
        ReminderAlarmReceiver.schedulerProvider = { ReminderScheduler(it) }

        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowNotificationManager = Shadows.shadowOf(notificationManager)
    }

    @After
    fun tearDown() {
        db.close()
        ReminderAlarmReceiver.reminderRepositoryProvider = { (it.applicationContext as? com.suprxsidh.stride.StrideApp)?.container?.reminderRepository }
        ReminderAlarmReceiver.weightRepositoryProvider = { (it.applicationContext as? com.suprxsidh.stride.StrideApp)?.container?.weightRepository }
        ReminderAlarmReceiver.geminiFoodRepositoryProvider = { (it.applicationContext as? com.suprxsidh.stride.StrideApp)?.container?.geminiFoodRepository }
        ReminderAlarmReceiver.pendingDraftDaoProvider = { (it.applicationContext as? com.suprxsidh.stride.StrideApp)?.container?.pendingDraftDao }
        ReminderAlarmReceiver.schedulerProvider = { ReminderScheduler(it) }
    }

    private fun activeNotificationIds(): List<Int> = shadowNotificationManager.activeNotifications.map { it.id }

    private fun notificationFor(id: Int): Notification = shadowNotificationManager.activeNotifications.first { it.id == id }.notification

    private fun replyIntent(reminderId: Long, replyText: String): Intent {
        val intent = Intent(ReminderAlarmReceiver.ACTION_DIRECT_REPLY).apply {
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        val remoteInput = RemoteInput.Builder(ReminderNotificationBuilder.KEY_REPLY_TEXT).build()
        val resultsBundle = Bundle().apply { putCharSequence(ReminderNotificationBuilder.KEY_REPLY_TEXT, replyText) }
        RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, resultsBundle)
        return intent
    }

    // --- handleAlarmFired -------------------------------------------------------------------

    @Test
    fun `weigh-in alarm posts a notification when no weigh-in exists today`() = runTest {
        val reminder = reminderRepository.save(ReminderEntity(type = ReminderType.WEIGH_IN.name, time = "07:00"))

        receiver.handleAlarmFired(context, reminder.id)

        assertTrue(activeNotificationIds().contains(ReminderNotificationBuilder.notificationId(reminder.id)))
    }

    @Test
    fun `weigh-in alarm is smart-suppressed when today's weigh-in already exists`() = runTest {
        db.weighInDao().upsert(WeighInEntity(date = LocalDate.of(2026, 9, 24).toString(), weightKg = 80.0))
        val reminder = reminderRepository.save(ReminderEntity(type = ReminderType.WEIGH_IN.name, time = "07:00"))

        receiver.handleAlarmFired(context, reminder.id)

        assertTrue(activeNotificationIds().isEmpty())
    }

    @Test
    fun `weigh-in alarm still reschedules the next occurrence even when suppressed`() = runTest {
        db.weighInDao().upsert(WeighInEntity(date = LocalDate.of(2026, 9, 24).toString(), weightKg = 80.0))
        val reminder = reminderRepository.save(ReminderEntity(type = ReminderType.WEIGH_IN.name, time = "07:00"))

        receiver.handleAlarmFired(context, reminder.id)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        assertTrue(Shadows.shadowOf(alarmManager).scheduledAlarms.isNotEmpty())
    }

    @Test
    fun `meal alarm posts to the meal channel`() = runTest {
        val reminder = reminderRepository.save(ReminderEntity(type = ReminderType.MEAL.name, time = "09:00", label = "Breakfast"))

        receiver.handleAlarmFired(context, reminder.id)

        val notification = notificationFor(ReminderNotificationBuilder.notificationId(reminder.id))
        assertEquals(NotificationChannels.MEAL, notification.channelId)
    }

    @Test
    fun `snack alarm posts to the snack channel`() = runTest {
        val reminder = reminderRepository.save(ReminderEntity(type = ReminderType.SNACK.name, time = "16:00", label = "Snack"))

        receiver.handleAlarmFired(context, reminder.id)

        val notification = notificationFor(ReminderNotificationBuilder.notificationId(reminder.id))
        assertEquals(NotificationChannels.SNACK, notification.channelId)
    }

    @Test
    fun `alarm for a deleted reminder id does nothing`() = runTest {
        receiver.handleAlarmFired(context, 999L)
        assertTrue(activeNotificationIds().isEmpty())
    }

    // --- handleDirectReply: weigh-in ---------------------------------------------------------

    @Test
    fun `weigh-in direct reply with a valid number logs it and cancels the notification`() = runTest {
        val reminder = reminderRepository.save(ReminderEntity(type = ReminderType.WEIGH_IN.name, time = "07:00"))
        receiver.handleAlarmFired(context, reminder.id) // posts the original notification

        receiver.handleDirectReply(context, reminder.id, replyIntent(reminder.id, "81.4"))

        assertTrue(weightRepository.hasWeighInForToday())
        assertTrue(activeNotificationIds().none { it == ReminderNotificationBuilder.notificationId(reminder.id) })
    }

    @Test
    fun `weigh-in direct reply with garbage text logs nothing and leaves the notification`() = runTest {
        val reminder = reminderRepository.save(ReminderEntity(type = ReminderType.WEIGH_IN.name, time = "07:00"))
        receiver.handleAlarmFired(context, reminder.id)

        receiver.handleDirectReply(context, reminder.id, replyIntent(reminder.id, "not a number"))

        assertTrue(!weightRepository.hasWeighInForToday())
        assertTrue(activeNotificationIds().contains(ReminderNotificationBuilder.notificationId(reminder.id)))
    }

    // --- handleDirectReply: meal/snack --------------------------------------------------------

    @Test
    fun `meal direct reply success logs the meal and posts a follow-up with the counted kcal`() = runTest {
        settingsRepository.setGeminiApiKey("test-key")
        ReminderAlarmReceiver.geminiFoodRepositoryProvider = { geminiRepository(succeed = true) }
        val reminder = reminderRepository.save(ReminderEntity(type = ReminderType.MEAL.name, time = "09:00", label = "Breakfast"))

        receiver.handleDirectReply(context, reminder.id, replyIntent(reminder.id, "2 rotis and chole"))

        assertEquals(1, foodRepository.observeTodayEntries().first().size)
        val followUp = notificationFor(ReminderNotificationBuilder.followUpNotificationId(reminder.id))
        assertEquals(NotificationChannels.MEAL_FOLLOW_UP, followUp.channelId)
    }

    @Test
    fun `meal direct reply falls back to the pending-draft queue on Gemini failure`() = runTest {
        settingsRepository.setGeminiApiKey("test-key")
        ReminderAlarmReceiver.geminiFoodRepositoryProvider = { geminiRepository(succeed = false) }
        val reminder = reminderRepository.save(ReminderEntity(type = ReminderType.MEAL.name, time = "09:00", label = "Breakfast"))

        receiver.handleDirectReply(context, reminder.id, replyIntent(reminder.id, "2 rotis and chole"))

        assertEquals(1, db.pendingDraftDao().getAll().size)
        assertEquals(0, foodRepository.observeTodayEntries().first().size)
    }

    @Test
    fun `meal direct reply with no Gemini key queues a pending draft by hand`() = runTest {
        settingsRepository.setGeminiApiKey(null)
        ReminderAlarmReceiver.geminiFoodRepositoryProvider = { geminiRepository(succeed = true) }
        val reminder = reminderRepository.save(ReminderEntity(type = ReminderType.MEAL.name, time = "09:00", label = "Breakfast"))

        receiver.handleDirectReply(context, reminder.id, replyIntent(reminder.id, "2 rotis and chole"))

        val drafts = db.pendingDraftDao().getAll()
        assertEquals(1, drafts.size)
        assertEquals("2 rotis and chole", drafts.single().payload)
        assertEquals("MEAL_TEXT", drafts.single().type)
    }

    @Test
    fun `blank meal reply text does nothing`() = runTest {
        val reminder = reminderRepository.save(ReminderEntity(type = ReminderType.MEAL.name, time = "09:00", label = "Breakfast"))

        receiver.handleDirectReply(context, reminder.id, replyIntent(reminder.id, "   "))

        assertEquals(0, db.pendingDraftDao().getAll().size)
    }

    @Test
    fun `direct reply for a deleted reminder id does nothing`() = runTest {
        receiver.handleDirectReply(context, 999L, replyIntent(999L, "81.4"))
        assertTrue(!weightRepository.hasWeighInForToday())
    }
}
