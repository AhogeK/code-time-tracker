package com.ahogek.codetimetracker.service.sync

import com.ahogek.codetimetracker.model.CodingSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId

class SyncSessionMapperTest {

    private val sample = CodingSession(
        sessionUuid = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
        userId = "user-1",
        projectName = "ctt-server",
        language = "Java",
        platform = "macOS",
        ideName = "IntelliJ IDEA",
        startTime = LocalDateTime.of(2026, 8, 25, 9, 0),
        endTime = LocalDateTime.of(2026, 8, 25, 10, 0),
        lastModified = LocalDateTime.of(2026, 8, 25, 10, 0),
        syncVersion = 2,
    )

    @Test
    fun `should map all contract fields with zero drift`() {
        val dto = SyncSessionMapper.toSyncDto(sample)

        assertThat(dto.sessionUuid).isEqualTo(sample.sessionUuid)
        assertThat(dto.projectName).isEqualTo(sample.projectName)
        assertThat(dto.language).isEqualTo(sample.language)
        assertThat(dto.startTime).isEqualTo(
            sample.startTime.atZone(ZoneId.systemDefault()).toInstant().toString(),
        )
        assertThat(dto.endTime).isEqualTo(
            sample.endTime.atZone(ZoneId.systemDefault()).toInstant().toString(),
        )
        assertThat(dto.clientModifiedAt).isEqualTo(
            sample.lastModified.atZone(ZoneId.systemDefault()).toInstant().toString(),
        )
        assertThat(dto.clientVersion).isEqualTo(2)
    }

    @Test
    fun `should never mark a session as deleted`() {
        // The plugin has no soft-delete contract (published DB), so deleted is always false.
        assertThat(SyncSessionMapper.toSyncDto(sample).deleted).isFalse()
    }
}
