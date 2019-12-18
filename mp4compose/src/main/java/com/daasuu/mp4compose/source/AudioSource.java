package com.daasuu.mp4compose.source;

public class AudioSource {
    private String srcPath;

    public AudioSource(final String srcPath) {
        this.srcPath = srcPath;
    }

    public String getAudioPath() {
        return srcPath;
    }
}
