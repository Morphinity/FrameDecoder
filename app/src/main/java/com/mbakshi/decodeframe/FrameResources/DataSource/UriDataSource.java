package com.mbakshi.decodeframe.FrameResources.DataSource;

/**
 * Created by mbakshi on 19/08/15.
 */
public interface UriDataSource extends DataSource {
    /**
     * When the source is open, returns the URI from which data is being read.
     * <p>
     * If redirection occurred, the URI after redirection is the one returned.
     *
     * @return When the source is open, the URI from which data is being read. Null otherwise.
     */
    String getUri();

}

