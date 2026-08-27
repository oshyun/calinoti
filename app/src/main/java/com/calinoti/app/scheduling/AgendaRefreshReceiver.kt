package com.calinoti.app.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.calinoti.app.AgendaApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 아젠다 갱신 브로드캐스트를 받는 리시버. 알람 예약과 재부팅(BOOT_COMPLETED) 복원이 같은 경로를 쓴다.
 * 갱신은 완료까지 보장되어야 해서 겹침 병합(launchAgendaRefresh)과 달리 이곳에서 직접 실행하고,
 * 브로드캐스트 처리 중 프로세스가 회수되지 않도록 goAsync로 수명을 유지한다.
 * 실행 자체는 refreshNow의 Mutex로 다른 경로와 직렬화된다.
 */
// FIXME(goasync-10s-window): goAsync의 프로세스 보장은 약 10초라, 부팅 직후 큰 캘린더 조회가
//   그 안에 끝나지 않으면 프로세스가 회수되어 다음 알람 예약이 누락될 수 있다.
// FIXME-REMOVE-WHEN: 리시버가 goAsync 대신 WorkManager 등 장수명 실행으로 전환되면
class AgendaRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                (context.applicationContext as AgendaApplication).agendaRefresher.refreshNow()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (runtimeError: Exception) {
                Log.e(TAG, "아젠다 알림 갱신에 실패했다", runtimeError)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "AgendaRefreshReceiver"
    }
}
