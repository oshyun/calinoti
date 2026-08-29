package com.calinoti.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.calinoti.app.CalinotiApplication

/**
 * 임박 일정 알림이 스와이프로 지워진 것을 감지해 재게시 금지를 접수한다. 알림 갱신 큐를
 * 건드리지 않으므로 갱신 리시버의 실행 겹침 방지(KEEP 정책)와 무간섭이다.
 */
class ImminentEventDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, INVALID_EVENT_ID)
        if (eventId == INVALID_EVENT_ID) return
        val beginTimeMilliseconds =
            intent.getLongExtra(EXTRA_EVENT_BEGIN_TIME_MILLISECONDS, 0L)
        (context.applicationContext as CalinotiApplication)
            .imminentEventNotifier
            .onDismissed(eventId, beginTimeMilliseconds)
    }

    companion object {
        private const val INVALID_EVENT_ID = -1L
        const val EXTRA_EVENT_ID = "com.calinoti.app.extra.IMMINENT_EVENT_ID"
        const val EXTRA_EVENT_BEGIN_TIME_MILLISECONDS =
            "com.calinoti.app.extra.IMMINENT_EVENT_BEGIN_TIME_MILLISECONDS"
    }
}
