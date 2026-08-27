package com.calinoti.app.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * 아젠다 갱신 브로드캐스트를 받는 리시버. 알람 예약과 재부팅(BOOT_COMPLETED) 복원이 같은 경로를 쓴다.
 * 갱신은 goAsync의 약 10초 브로드캐스트 창 너머까지 걸릴 수 있어 WorkManager에 위임하고
 * 리시버는 즉시 반환한다. 실행 중인 갱신을 교체(REPLACE)하면 그 갱신의 알람 예약이 사라지므로
 * 유지(KEEP)로 큐에 넣는다.
 */
class AgendaRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<AgendaRefreshWorker>().build(),
        )
    }

    private companion object {
        const val UNIQUE_WORK_NAME = "agenda-refresh"
    }
}
