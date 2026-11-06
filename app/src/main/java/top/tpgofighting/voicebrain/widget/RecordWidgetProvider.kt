package top.tpgofighting.voicebrain.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import top.tpgofighting.voicebrain.MainActivity
import top.tpgofighting.voicebrain.R

/**
 * 桌面小组件：一键直达录音。
 * 点击后打开主界面并立即开始录音（权限未授予时主界面会先弹用途说明，
 * 与应用内点「开始录音」的路径完全一致，不绕过任何确认）。
 */
class RecordWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(MainActivity.EXTRA_START_RECORDING, true)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                widgetId,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val views = RemoteViews(context.packageName, R.layout.widget_record)
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
