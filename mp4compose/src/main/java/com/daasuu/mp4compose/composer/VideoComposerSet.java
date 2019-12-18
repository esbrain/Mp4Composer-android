package com.daasuu.mp4compose.composer;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.opengl.EGLContext;
import android.os.Build;
import android.util.Log;
import android.util.Size;

import com.daasuu.mp4compose.FillMode;
import com.daasuu.mp4compose.FillModeCustomItem;
import com.daasuu.mp4compose.Rotation;
import com.daasuu.mp4compose.filter.GlFilter;
import com.daasuu.mp4compose.logger.Logger;
import com.daasuu.mp4compose.source.DataSource;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

// 複数のVideoComposerをシーケンシャルに管理する
public class VideoComposerSet {
    private static final String TAG = "VideoComposerSet";
    private VideoComposer[] videoComposers;
    private VideoComposer currentVideoComposer;
    private int currentVideoComposerIndex = -1;
    private VideoTrack[] videoTracks;
    private boolean isEOS = false;
    private long totalWrittenPresentationTimeUs;

    private MuxRender muxRender;
    private MediaFormat actualVideoOutputFormat;
    private int timeScale;
    private Size outputResolution;
    private GlFilter filter;
    private EGLContext shareContext;

    public VideoComposerSet(
        @NonNull MuxRender muxRender,
        @NonNull MediaFormat actualVideoOutputFormat,
        int timeScale,
        final Size outputResolution,
        final GlFilter filter,
        final EGLContext shareContext) {

        this.muxRender = muxRender;
        this.actualVideoOutputFormat = actualVideoOutputFormat;
        this.timeScale = timeScale;
        this.outputResolution = outputResolution;
        this.filter = filter;
        this.shareContext = shareContext;
    }

    public void setVideoTracks(VideoTrack[] videoTracks) {
        this.videoTracks = videoTracks;
        this.totalWrittenPresentationTimeUs = 0;
        this.isEOS = false;
    }

    public boolean isFinished() {
        return isEOS;
    }

    public boolean stepPipeline() {
        VideoComposer vc = getCurrentComposer();
        if(vc == null) {
            return false;
        }
        boolean result = vc.stepPipeline();

        if(vc.isFinished()) {
            // 現在使用中のvideoComposerを停止する
            releaseCurrentVideoComposer();
        }

        return result;
    }

    public void release() {
        releaseCurrentVideoComposer();
    }

    public long getWrittenPresentationTimeUs() {
        long time = totalWrittenPresentationTimeUs;
        if(getCurrentComposer() != null) {
            time += getCurrentComposer().getWrittenPresentationTimeUs();
        }
        Log.d(TAG, String.format("総出力時間 %f s", (double)time / 1000000));
        return time;
    }

    private void releaseCurrentVideoComposer() {
        if(currentVideoComposer == null) {
            return;
        }
        // 出力トータル時間を更新する
        totalWrittenPresentationTimeUs += currentVideoComposer.getOutputDurationTimeUs();
        videoTracks[currentVideoComposerIndex].release();
        currentVideoComposer = null;
        if(currentVideoComposerIndex + 1 >= videoTracks.length) {
            isEOS = true;
        }
    }

    private VideoComposer getCurrentComposer() {
        if(currentVideoComposer == null) {
            // 超えてはいけない
            int index = currentVideoComposerIndex + 1;
            if(index >= videoTracks.length) {
                return null;
            }
            Log.d("VideoComposer", String.format("VideoComposerを切り替えます %d", index));
            currentVideoComposerIndex = index;
            currentVideoComposer = videoTracks[currentVideoComposerIndex].createVideoComposer(
                    this.muxRender,
                    this.actualVideoOutputFormat,
                    this.timeScale,
                    index == 0,
                    index == videoTracks.length - 1,
                    this.outputResolution,
                    this.filter,
                    FillMode.PRESERVE_ASPECT_FIT,
                    null,
                    false,
                    false,
                    this.shareContext);
        }
        return currentVideoComposer;
    }
}

