
package com.daasuu.mp4compose.composer;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.opengl.EGLContext;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;

import com.daasuu.mp4compose.FillMode;
import com.daasuu.mp4compose.FillModeCustomItem;
import com.daasuu.mp4compose.Rotation;
import com.daasuu.mp4compose.SampleType;
import com.daasuu.mp4compose.filter.GlFilter;
import com.daasuu.mp4compose.logger.Logger;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

// Refer: https://android.googlesource.com/platform/cts/+/lollipop-release/tests/tests/media/src/android/media/cts/ExtractDecodeEditEncodeMuxTest.java
// Refer: https://github.com/ypresto/android-transcoder/blob/master/lib/src/main/java/net/ypresto/androidtranscoder/engine/VideoTrackTranscoder.java
public class VideoComposer {
    public static long currentElapsedTimeUs = 0;
    private static final String TAG = "VideoComposer";
    private static final int DRAIN_STATE_NONE = 0;
    private static final int DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY = 1;
    private static final int DRAIN_STATE_CONSUMED = 2;

    private final MediaExtractor mediaExtractor;
    private final int trackIndex;
    private final MediaFormat outputFormat;
    private final MuxRender muxRender;
    private final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
    private MediaCodec decoder;
    private MediaCodec encoder;
    private MediaFormat actualOutputFormat;
    private DecoderSurface decoderSurface;
    private EncoderSurface encoderSurface;
    private boolean isExtractorEOS;
    private boolean isDecoderEOS;
    private boolean isEncoderEOS;
    private boolean decoderStarted;
    private boolean encoderStarted;
    private long writtenPresentationTimeUs;
    private final int timeScale;
    private final long trimStartUs;
    private final long trimEndUs;

    private long decoderElapsedTimeUs;
    private long decoderOutputStartTimeUs;
    private long decoderPreviousTimeUs;

    private long encoderElapsedTimeUs;
    private long encoderOutputStartTimeUs;
    private long encoderPreviousTimeUs;

    // 先頭
    private boolean isFirst;
    // EOSを出力するフラグ
    private boolean sendEOS;

    private final Logger logger;

    VideoComposer(@NonNull MediaExtractor mediaExtractor, int trackIndex,
                  @NonNull MediaFormat outputFormat, @NonNull MuxRender muxRender, int timeScale,
                  final long trimStartMs, final long trimEndMs, final long outputStartMs,
                  final boolean isFirst,
                  final boolean sendEOS,
                  @NonNull Logger logger) {
        this.mediaExtractor = mediaExtractor;
        this.trackIndex = trackIndex;
        this.outputFormat = outputFormat;
        this.muxRender = muxRender;
        this.timeScale = timeScale;
        this.trimStartUs = TimeUnit.MILLISECONDS.toMicros(trimStartMs);
        this.trimEndUs = trimEndMs == -1 ? trimEndMs : TimeUnit.MILLISECONDS.toMicros(trimEndMs);
        this.isFirst = isFirst;
        this.sendEOS = sendEOS;
        this.logger = logger;

        // 出力開始時間を設定する
        this.encoderOutputStartTimeUs = TimeUnit.MILLISECONDS.toMicros(outputStartMs);
        this.encoderPreviousTimeUs = 0;
        this.encoderElapsedTimeUs = 0;

        this.decoderOutputStartTimeUs = TimeUnit.MILLISECONDS.toMicros(outputStartMs);
        this.decoderPreviousTimeUs = 0;
        this.decoderElapsedTimeUs = 0;

    }


    void setUp(GlFilter filter,
               Rotation rotation,
               Size outputResolution,
               Size inputResolution,
               FillMode fillMode,
               FillModeCustomItem fillModeCustomItem,
               final boolean flipVertical,
               final boolean flipHorizontal,
               final EGLContext shareContext) {
        mediaExtractor.selectTrack(trackIndex);
        try {
            encoder = MediaCodec.createEncoderByType(outputFormat.getString(MediaFormat.KEY_MIME));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoderSurface = new EncoderSurface(encoder.createInputSurface(), shareContext);
        encoderSurface.makeCurrent();
        encoder.start();
        encoderStarted = true;

        MediaFormat inputFormat = mediaExtractor.getTrackFormat(trackIndex);
        mediaExtractor.seekTo(trimStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        if (inputFormat.containsKey("rotation-degrees")) {
            // Decoded video is rotated automatically in Android 5.0 lollipop.
            // Turn off here because we don't want to encode rotated one.
            // refer: https://android.googlesource.com/platform/frameworks/av/+blame/lollipop-release/media/libstagefright/Utils.cpp
            inputFormat.setInteger("rotation-degrees", 0);
        }
        decoderSurface = new DecoderSurface(filter, logger);
        decoderSurface.setRotation(rotation);
        decoderSurface.setOutputResolution(outputResolution);
        decoderSurface.setInputResolution(inputResolution);
        decoderSurface.setFillMode(fillMode);
        decoderSurface.setFillModeCustomItem(fillModeCustomItem);
        decoderSurface.setFlipHorizontal(flipHorizontal);
        decoderSurface.setFlipVertical(flipVertical);
        decoderSurface.completeParams();

        try {
            decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        decoder.configure(inputFormat, decoderSurface.getSurface(), null, 0);
        decoder.start();
        decoderStarted = true;
    }


    boolean stepPipeline() {
        boolean busy = false;

        int status;
        while (drainEncoder() != DRAIN_STATE_NONE) {
            busy = true;
        }
        do {
            status = drainDecoder();
            if (status != DRAIN_STATE_NONE) {
                busy = true;
            }
            // NOTE: not repeating to keep from deadlock when encoder is full.
        } while (status == DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY);
        while (drainExtractor() != DRAIN_STATE_NONE) {
            busy = true;
        }

        return busy;
    }


    long getWrittenPresentationTimeUs() {
        return writtenPresentationTimeUs;
    }


    boolean isFinished() {
        return isEncoderEOS;
    }


    void release() {
        if (decoderSurface != null) {
            decoderSurface.release();
            decoderSurface = null;
        }
        if (encoderSurface != null) {
            encoderSurface.release();
            encoderSurface = null;
        }
        if (decoder != null) {
            if (decoderStarted) decoder.stop();
            decoder.release();
            decoder = null;
        }
        if (encoder != null) {
            if (encoderStarted) encoder.stop();
            encoder.release();
            encoder = null;
        }
    }

    private int drainExtractor() {
        if (isExtractorEOS) return DRAIN_STATE_NONE;
        int trackIndex = mediaExtractor.getSampleTrackIndex();
        if (trackIndex >= 0 && trackIndex != this.trackIndex) {
            return DRAIN_STATE_NONE;
        }
        int result = decoder.dequeueInputBuffer(0);
        if (result < 0) return DRAIN_STATE_NONE;
        if (trackIndex < 0) {
            isExtractorEOS = true;
            decoder.queueInputBuffer(result, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            return DRAIN_STATE_NONE;
        }
        int sampleSizeCompat = mediaExtractor.readSampleData(decoder.getInputBuffer(result), 0);
        boolean isKeyFrame = (mediaExtractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0;
        decoder.queueInputBuffer(result, 0, sampleSizeCompat, mediaExtractor.getSampleTime() / timeScale, isKeyFrame ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0);
        mediaExtractor.advance();
        return DRAIN_STATE_CONSUMED;
    }

    private int drainDecoder() {
        if (isDecoderEOS) return DRAIN_STATE_NONE;
        int result = decoder.dequeueOutputBuffer(bufferInfo, 0);
        switch (result) {
            case MediaCodec.INFO_TRY_AGAIN_LATER:
                return DRAIN_STATE_NONE;
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY;
            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY;
        }
        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            encoder.signalEndOfInputStream();
            isDecoderEOS = true;
            bufferInfo.size = 0;
        }
        // 時間を超えた場合も終了処理する
        if(trimEndUs != -1 && bufferInfo.presentationTimeUs >= trimEndUs) {
            encoder.signalEndOfInputStream();
            isDecoderEOS = true;
            bufferInfo.size = 0;
        }
        final boolean doRender = (bufferInfo.size > 0
                && bufferInfo.presentationTimeUs >= trimStartUs
                && (bufferInfo.presentationTimeUs < trimEndUs || trimEndUs == -1));
        // NOTE: doRender will block if buffer (of encoder) is full.
        // Refer: http://bigflake.com/mediacodec/CameraToMpegTest.java.txt
        decoder.releaseOutputBuffer(result, doRender);
        if (doRender) {
            // 出力時間に調整する
            long currentTimeUs = bufferInfo.presentationTimeUs;
            // 前回の値との差分を増やす
            long diffTimeUs = currentTimeUs - decoderPreviousTimeUs;
            decoderElapsedTimeUs += diffTimeUs;

            decoderSurface.awaitNewImage();

            // 時間を記録する
            currentElapsedTimeUs = decoderOutputStartTimeUs + currentTimeUs;
            decoderSurface.drawImage();
            //encoderSurface.setPresentationTime(bufferInfo.presentationTimeUs * 1000);
            encoderSurface.setPresentationTime(bufferInfo.presentationTimeUs * 1000);
            encoderSurface.swapBuffers();

            decoderPreviousTimeUs = currentTimeUs;
        } else if (bufferInfo.presentationTimeUs != 0) {
            //writtenPresentationTimeUs = bufferInfo.presentationTimeUs;
            writtenPresentationTimeUs = encoderElapsedTimeUs;
        }
        return DRAIN_STATE_CONSUMED;
    }

    private int drainEncoder() {
        if (isEncoderEOS) return DRAIN_STATE_NONE;
        int result = encoder.dequeueOutputBuffer(bufferInfo, 0);
        switch (result) {
            case MediaCodec.INFO_TRY_AGAIN_LATER:
                return DRAIN_STATE_NONE;
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                if (actualOutputFormat != null) {
                    throw new RuntimeException("Video output format changed twice.");
                }
                actualOutputFormat = encoder.getOutputFormat();
                // 先頭だけ処理する
                if(isFirst) {
                    muxRender.setOutputFormat(SampleType.VIDEO, actualOutputFormat);
                    muxRender.onSetOutputFormat();
                }
                return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY;
            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY;
        }
        if (actualOutputFormat == null) {
            throw new RuntimeException("Could not determine actual output format.");
        }
        MediaCodec.BufferInfo outputBufferInfo = new MediaCodec.BufferInfo();

        /*
        Log.d(TAG, String.format("出力時間(offset:%d,size:%d,timeUs:%d,flags:%x,KeyFrame:%s,Config:%s,EOS:%s,PFrame:%s)",
                bufferInfo.offset,
                bufferInfo.size,
                bufferInfo.presentationTimeUs,
                bufferInfo.flags,
                (bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0 ? "YES" : "NO", // LSB (0x1)
                (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0 ? "YES" : "NO", // bit1 (0x2)
                (bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 ? "YES" : "NO", // bit2 (0x4)
                (bufferInfo.flags & MediaCodec.BUFFER_FLAG_PARTIAL_FRAME) != 0 ? "YES" : "NO") // bit3 (0x8)

        );
         */
        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            isEncoderEOS = true;
            bufferInfo.set(0, 0, 0, bufferInfo.flags);
            // 出力用の情報をつくる
            outputBufferInfo.set(0, 0, 0, bufferInfo.flags);
        } else {
            // 出力時間に調整する
            long currentTimeUs = bufferInfo.presentationTimeUs;
            // 前回の値との差分を増やす
            long diffTimeUs = currentTimeUs - encoderPreviousTimeUs;
            encoderElapsedTimeUs += diffTimeUs;
            /*
            Log.d(TAG, String.format("出力時間%d, %d, %d",
                currentTimeUs, previousTimeUs, elapsedTimeUs));
             */

            // bufferInfoを直接上書きするとDecodeが狂う
            //bufferInfo.presentationTimeUs = elapsedTimeUs;

            // 出力用の情報をつくる
            outputBufferInfo.set(bufferInfo.offset, bufferInfo.size, encoderOutputStartTimeUs + encoderElapsedTimeUs, bufferInfo.flags);

            // 次のフレームのための準備
            encoderPreviousTimeUs = currentTimeUs;
        }
        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            // SPS or PPS, which should be passed by MediaFormat.
            encoder.releaseOutputBuffer(result, false);
            return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY;
        }
        // EOSに到達した時
        if(isEncoderEOS && !sendEOS) {
            // 何もしない
        } else {
            muxRender.writeSampleData(SampleType.VIDEO, encoder.getOutputBuffer(result), outputBufferInfo);
        }
        writtenPresentationTimeUs = encoderElapsedTimeUs;
        Log.d(TAG, String.format("出力時間 %f s", (double)encoderElapsedTimeUs / 1000000));
        encoder.releaseOutputBuffer(result, false);
        return DRAIN_STATE_CONSUMED;
    }

    public long getOutputDurationTimeUs() {
        return trimEndUs - trimStartUs;
    }
}
