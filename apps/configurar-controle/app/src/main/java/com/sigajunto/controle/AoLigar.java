package com.sigajunto.controle;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/**
 * Ao ligar a multimídia: recoloca no /sdcard os perfis de controle que vêm no sistema (depois de um reset).
 * Logo no boot o /sdcard às vezes ainda não aceita escrita: tenta algumas vezes e reagenda.
 */
public class AoLigar extends BroadcastReceiver {
    private static final String TENTATIVA = "tentativa";

    @Override
    public void onReceive(Context c, Intent i) {
        final Context app = c.getApplicationContext();
        final int tentativa = i.getIntExtra(TENTATIVA, 0);
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                for (int t = 0; t < 5; t++) {
                    if (Perfis.restaurar() >= 0) return;
                    Thread.sleep(1500);
                }
                if (tentativa < 10) {
                    Intent de_novo = new Intent(app, AoLigar.class).putExtra(TENTATIVA, tentativa + 1);
                    PendingIntent pi = PendingIntent.getBroadcast(app, 7, de_novo,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    ((AlarmManager) app.getSystemService(Context.ALARM_SERVICE)).set(
                            AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 30000, pi);
                }
            } catch (Exception ignorado) {
            } finally {
                pr.finish();
            }
        }).start();
    }
}
