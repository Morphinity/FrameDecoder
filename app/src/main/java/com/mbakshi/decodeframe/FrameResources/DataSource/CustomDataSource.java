package com.mbakshi.decodeframe.FrameResources.DataSource;

import android.content.Context;
import android.text.TextUtils;

import com.mbakshi.decodeframe.FrameResources.Assertions;
import com.mbakshi.decodeframe.FrameResources.DataSource.ImplDataSource.AssetDataSource;
import com.mbakshi.decodeframe.FrameResources.DataSource.ImplDataSource.ContentDataSource;
import com.mbakshi.decodeframe.FrameResources.DataSource.ImplDataSource.FileDataSource;

import java.io.IOException;

/**
 * Created by mbakshi on 19/08/15.
 */
public class CustomDataSource implements UriDataSource{

    private static final String SCHEME_FILE = "file";
    private static final String SCHEME_ASSET = "asset";
    private static final String SCHEME_CONTENT = "content";

    //private final UriDataSource httpDataSource;
    private final UriDataSource fileDataSource;
    private final UriDataSource assetDataSource;
    private final UriDataSource contentDataSource;


    private UriDataSource dataSource;

    /**
     * Constructs a new instance.
     * <p>
     * The constructed instance will not follow cross-protocol redirects (i.e. redirects from HTTP to
     * HTTPS or vice versa) when fetching remote data. Cross-protocol redirects can be enabled by
     * using {@link #CustomDataSource(Context, String)} (Context, TransferListener, String, boolean)} and passing
     * {@code true} as the final argument.
     *
     * @param context A context.
     * @param userAgent The User-Agent string that should be used when requesting remote data.
     */
    public CustomDataSource(Context context, String userAgent) {
        this(context, null, userAgent, false);
    }

    /**
     * Constructs a new instance.
     * <p>
     * The constructed instance will not follow cross-protocol redirects (i.e. redirects from HTTP to
     * HTTPS or vice versa) when fetching remote data. Cross-protocol redirects can be enabled by
     * using {@link #CustomDataSource(Context, String)} (Context, TransferListener, String, boolean)} and passing
     * {@code true} as the final argument.
     *
     * @param context A context.
     * @param listener An optional
     * @param userAgent The User-Agent string that should be used when requesting remote data.
     */
    public CustomDataSource(Context context, TransferListener listener, String userAgent) {
        this(context, listener, userAgent, false);
    }

    /**
     * Constructs a new instance, optionally configured to follow cross-protocol redirects.
     *
     * @param context A context.
     * @param listener An optional {@link TransferListener}.
     * @param userAgent The User-Agent string that should be used when requesting remote data.
     * @param allowCrossProtocolRedirects Whether cross-protocol redirects (i.e. redirects from HTTP
     *     to HTTPS and vice versa) are enabled when fetching remote data..
     */
    public CustomDataSource(Context context, TransferListener listener, String userAgent,
                            boolean allowCrossProtocolRedirects) {
        this(context, listener);
    }

    public CustomDataSource(Context context, TransferListener listener) {
        this.fileDataSource = new FileDataSource(listener);
        this.assetDataSource = new AssetDataSource(context, listener);
        this.contentDataSource = new ContentDataSource(context, listener);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        Assertions.checkState(dataSource == null);
        // Choose the correct source for the scheme.
        String scheme = dataSpec.uri.getScheme();
        if (SCHEME_FILE.equals(scheme) || TextUtils.isEmpty(scheme)) {
            if (dataSpec.uri.getPath().startsWith("/android_asset/")) {
                dataSource = assetDataSource;
            } else {
                dataSource = fileDataSource;
            }
        } else if (SCHEME_ASSET.equals(scheme)) {
            dataSource = assetDataSource;
        } else if (SCHEME_CONTENT.equals(scheme)) {
            dataSource = contentDataSource;
        } else {
            dataSource = null;
        }
        // Open the source and return.
        return dataSource.open(dataSpec);
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        return dataSource.read(buffer, offset, readLength);
    }

    @Override
    public String getUri() {
        return dataSource == null ? null : dataSource.getUri();
    }

    @Override
    public void close() throws IOException {
        if (dataSource != null) {
            try {
                dataSource.close();
            } finally {
                dataSource = null;
            }
        }
    }
}
