package com.example.edulock.utils;

import android.content.Context;
import android.media.MediaPlayer;

import com.example.edulock.R;

public class SoundManager {

    public static void playButtonSound(Context context) {
        MediaPlayer mp = MediaPlayer.create(context, R.raw.button_sound_effect);
        mp.setOnCompletionListener(MediaPlayer::release);
        mp.start();
    }
}
