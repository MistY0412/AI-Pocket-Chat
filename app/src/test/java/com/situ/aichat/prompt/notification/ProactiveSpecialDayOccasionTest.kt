package com.situ.aichat.prompt.notification

import com.situ.aichat.data.local.dao.UserProfileDao
import com.situ.aichat.data.local.entity.CharacterEntity
import com.situ.aichat.data.local.entity.UserProfileEntity
import com.situ.aichat.notification.ProactiveOccasionText
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * 时间感知四期·图纸一 §3.8 / M13：特别日子由头的锁定格式 + [ProactiveMessageComposer.specialDayOccasion]
 * （称呼口径同 compose：昵称空 → 「用户」；日子判定走 SpecialDays 单源）。农历节日 → Robolectric，时区钉沪。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProactiveSpecialDayOccasionTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private lateinit var originalTz: TimeZone

    @Before
    fun pinTimeZone() {
        originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
    }

    @After
    fun restoreTimeZone() = TimeZone.setDefault(originalTz)

    private val userProfileDao: UserProfileDao = mockk()
    private val composer = ProactiveMessageComposer(mockk(), mockk(), mockk(), mockk(), userProfileDao, mockk())

    private fun noonOf(d: LocalDate) = d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `由头格式锁定`() {
        assertEquals("今天是七夕", ProactiveOccasionText.occasionForSpecialDay(listOf("七夕")))
        assertEquals("今天是小明的生日，也是七夕", ProactiveOccasionText.occasionForSpecialDay(listOf("小明的生日", "七夕")))
    }

    @Test
    fun `情人节加用户生日_昵称空称用户_最多两个名字`() = runBlocking {
        val day = LocalDate.of(2026, 2, 14)
        coEvery { userProfileDao.get() } returns UserProfileEntity(
            nickname = "", birthday = LocalDate.of(1998, 2, 14).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        val character = CharacterEntity(uuid = "c1", name = "林深", creationDate = 0L)
        assertEquals("今天是用户的生日，也是情人节", composer.specialDayOccasion(character, noonOf(day), zone))
    }

    @Test
    fun `普通日_无资料_返回null`() = runBlocking {
        coEvery { userProfileDao.get() } returns null
        val character = CharacterEntity(uuid = "c1", name = "林深", creationDate = 0L)
        assertNull(composer.specialDayOccasion(character, noonOf(LocalDate.of(2026, 7, 10)), zone))
    }
}
