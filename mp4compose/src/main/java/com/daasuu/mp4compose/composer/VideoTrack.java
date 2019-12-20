package com.daasuu.mp4compose.composer;


import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.opengl.EGLContext;
import android.util.Size;

import com.daasuu.mp4compose.FillMode;
import com.daasuu.mp4compose.FillModeCustomItem;
import com.daasuu.mp4compose.Rotation;
import com.daasuu.mp4compose.filter.GlFilter;
import com.daasuu.mp4compose.logger.Logger;
import com.daasuu.mp4compose.source.DataSource;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class VideoTrack {
    private static final String TAG = "VideoTrack";
    private DataSource srcDataSource;
    private MediaExtractor mediaExtractor;
    private int trackIndex;
    private MediaFormat outputFormat;
    private MuxRender muxRender;
    private MediaMetadataRetriever mediaMetadataRetriever;
    private long orgDurationUs;
    private static final String VIDEO_PREFIX = "video/";
    private VideoComposer videoComposer;
    private int videoTrackIndex;
    private long trimStartMs;
    private long trimEndMs;
    private long outputStartMs;
    private boolean sendEOS;
    private Logger logger;
    private Rotation rotation = Rotation.NORMAL;
    private int videoRotate;
    private Size inputResolution;

    public VideoTrack(
            @NonNull DataSource srcDataSource,
            final long trimStartMs,
            final long trimEndMs,
            final long outputStartMs,

            @NonNull Logger logger) {
        try {
            this.logger = logger;

            this.srcDataSource = srcDataSource;

            mediaExtractor = new MediaExtractor();
            if(srcDataSource.getFilePath() != null) {
                mediaExtractor.setDataSource(srcDataSource.getFilePath());
            } else {
                mediaExtractor.setDataSource(srcDataSource.getFileDescriptor());
            }
            mediaMetadataRetriever = new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(srcDataSource.getFilePath());
            try {
                orgDurationUs = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) * 1000;
            } catch (NumberFormatException e) {
                orgDurationUs = -1;
            }
            logger.debug(TAG, "Duration (us): " + orgDurationUs);


            // 回転検知
            videoRotate = getVideoRotation(srcDataSource);

            // 入力サイズ
            inputResolution = getVideoResolution(srcDataSource);


            // identify track indices
            MediaFormat format = mediaExtractor.getTrackFormat(0);
            String mime = format.getString(MediaFormat.KEY_MIME);

            if (mime.startsWith(VIDEO_PREFIX)) {
                videoTrackIndex = 0;
            } else {
                videoTrackIndex = 1;
            }
            this.trimStartMs = trimStartMs;
            this.trimEndMs = trimEndMs;
            this.outputStartMs = outputStartMs;

        } catch (Exception e) {
            e.printStackTrace();
            try {
                if (videoComposer != null) {
                    videoComposer.release();
                    videoComposer = null;
                }
                if (mediaExtractor != null) {
                    mediaExtractor.release();
                    mediaExtractor = null;
                }
            } catch (RuntimeException e2) {
                logger.error(TAG, "Could not shutdown mediaExtractor, codecs and mediaMuxer pipeline.", e2);
            }
            try {
                if (mediaMetadataRetriever != null) {
                    mediaMetadataRetriever.release();
                    mediaMetadataRetriever = null;
                }
            } catch (RuntimeException e2) {
                logger.error(TAG, "Failed to release mediaMetadataRetriever.", e2);
            }
        }
    }

    public DataSource getDataSource() {
        return srcDataSource;
    }

    public VideoComposer createVideoComposer(
            @NonNull MuxRender muxRender,
            @NonNull MediaFormat actualVideoOutputFormat,
            int timeScale,
            final boolean isFirst,
            final boolean sendEOS,

            final Size outputResolution,
            final GlFilter filter,
            final FillMode fillMode,
            final FillModeCustomItem fillModeCustomItem,
            final boolean flipVertical,
            final boolean flipHorizontal,
            final EGLContext shareContext) {

        Rotation rotate = Rotation.fromInt(rotation.getRotation() + videoRotate);

        videoComposer = new VideoComposer(mediaExtractor, videoTrackIndex, actualVideoOutputFormat, muxRender,
                timeScale, trimStartMs, trimEndMs, outputStartMs,
                isFirst, sendEOS,
                logger);
        videoComposer.setUp(filter, rotate, outputResolution,
                inputResolution, fillMode, fillModeCustomItem,
                flipVertical, flipHorizontal, shareContext);
        mediaExtractor.selectTrack(videoTrackIndex);

        return videoComposer;
    }

    public VideoComposer getVideoComposer() {
        return videoComposer;
    }

    public void release() {
        try {
            if (videoComposer != null) {
                videoComposer.release();
                videoComposer = null;
            }
            if (mediaExtractor != null) {
                mediaExtractor.release();
                mediaExtractor = null;
            }

        } catch(Exception e) {
            // 何もしない
            e.printStackTrace();
        }

    }

    @Nullable
    private Integer getVideoRotation(DataSource dataSource) {
        MediaMetadataRetriever mediaMetadataRetriever = null;
        try {
            mediaMetadataRetriever = new MediaMetadataRetriever();
            if(dataSource.getFilePath() != null) {
                mediaMetadataRetriever.setDataSource(dataSource.getFilePath());
            } else {
                mediaMetadataRetriever.setDataSource(dataSource.getFileDescriptor());
            }
            final String orientation = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            if (orientation == null) {
                return null;
            }
            return Integer.valueOf(orientation);
        } catch (IllegalArgumentException e) {
            logger.error("MediaMetadataRetriever", "getVideoRotation IllegalArgumentException", e);
            return 0;
        } catch (RuntimeException e) {
            logger.error("MediaMetadataRetriever", "getVideoRotation RuntimeException", e);
            return 0;
        } catch (Exception e) {
            logger.error("MediaMetadataRetriever", "getVideoRotation Exception", e);
            return 0;
        } finally {
            try {
                if (mediaMetadataRetriever != null) {
                    mediaMetadataRetriever.release();
                }
            } catch (RuntimeException e) {
                logger.error(TAG, "Failed to release mediaMetadataRetriever.", e);
            }
        }
    }

    /**
     * Extract the resolution of the video at the provided path, or null if the format is
     * unsupported.
     */
    @Nullable
    private Size getVideoResolution(DataSource dataSource) {
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            if(dataSource.getFilePath() != null) {
                retriever.setDataSource(dataSource.getFilePath());
            } else {
                retriever.setDataSource(dataSource.getFileDescriptor());
            }
            final String rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            final String rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            if (rawWidth == null || rawHeight == null) {
                return null;
            }
            final int width = Integer.valueOf(rawWidth);
            final int height = Integer.valueOf(rawHeight);

            return new Size(width, height);
        } finally {
            try {
                if (retriever != null) {
                    retriever.release();
                }
            } catch (RuntimeException e) {
                logger.error(TAG, "Failed to release mediaMetadataRetriever.", e);
            }
        }
    }

    public long getTotalDurationMs() {
        return trimEndMs - trimStartMs + outputStartMs;
    }
}