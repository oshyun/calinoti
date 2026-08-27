package com.calinoti.app.scheduling

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.calinoti.app.AgendaApplication

/**
 * 알람·재부팅이 요청한 아젠다 갱신을 WorkManager의 프로세스 보증 아래 실행한다.
 * 브로드캐스트 리시버의 goAsync 창(약 10초)과 달리 완료까지 실행이 보장된다.
 */
class AgendaRefreshWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {

    override suspend fun doWork(): Result {
        val application = applicationContext as AgendaApplication
        // refreshNow가 실패를 삼키고 안전망 알람까지 예약하므로 결과를 성공으로만 돌려준다.
        application.agendaRefresher.refreshNow()
        return Result.success()
    }
}
