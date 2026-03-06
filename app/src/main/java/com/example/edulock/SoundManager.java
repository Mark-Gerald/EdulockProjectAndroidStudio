package com.example.edulock;

import android.content.Context;
import android.media.MediaPlayer;

public class SoundManager {

    public static void playButtonSound(Context context) {
        MediaPlayer mp = MediaPlayer.create(context, R.raw.button_sound_effect);
        mp.setOnCompletionListener(MediaPlayer::release);
        mp.start();
    }
}
