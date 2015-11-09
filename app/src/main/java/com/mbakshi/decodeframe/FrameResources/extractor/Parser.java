package com.mbakshi.decodeframe.FrameResources.extractor;

import android.net.Uri;
import android.util.Log;

import com.mbakshi.decodeframe.FrameResources.Assertions;
import com.mbakshi.decodeframe.FrameResources.Constants;
import com.mbakshi.decodeframe.FrameResources.DataSource.DataSource;
import com.mbakshi.decodeframe.FrameResources.DataSource.DataSpec;
import com.mbakshi.decodeframe.FrameResources.Tracks.Track;
import com.mbakshi.decodeframe.FrameResources.Tracks.TrackOutput;
import com.mbakshi.decodeframe.FrameResources.Tracks.TrackSampleTable;
import com.mbakshi.decodeframe.FrameResources.Util.Atom.Atom;
import com.mbakshi.decodeframe.FrameResources.Util.Atom.AtomParsers;
import com.mbakshi.decodeframe.FrameResources.Util.CodecUtil.H264Util;
import com.mbakshi.decodeframe.FrameResources.Util.ParsableByteArray;
import com.mbakshi.decodeframe.FrameResources.Util.PositionHolder;
import com.mbakshi.decodeframe.FrameResources.extractor.Input.CustomExtractorInput;
import com.mbakshi.decodeframe.FrameResources.extractor.Input.ExtractorInput;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Created by mbakshi on 19/08/15.
 */
public class Parser {
    private static final String TAG = "CustomExtractor";
    // Extractor State
    private static final int STATE_READING_ATOM_HEADER = 0;
    private static final int STATE_READING_ATOM_PAYLOAD = 1;
    private static final int STATE_READING_SAMPLE = 2;
    private int extractorState;

    private ExtractorOutput extractorOutput;

    private static final int RELOAD_MINIMUM_SEEK_DISTANCE = 256 * 1024;

    // Atom Data
    private ParsableByteArray atomHeader;
    private long atomSize;
    private int atomType;
    private long rootAtomBytesRead;
    private int atomBytesRead;
    private ParsableByteArray atomData;
    private final Stack<Atom.ContainerAtom> containerAtoms;


    // Result Data
    public static final int RESULT_END_OF_INPUT = -1;
    public static final int RESULT_SEEK = 0;
    public static final int RESULT_CONTINUE = 1;

    // Track Data
    private Mp4Track[] tracks;

    // Data
    private int sampleBytesWritten;
    private int sampleSize;
    private final ParsableByteArray nalStartCode;
    private final ParsableByteArray nalLength;
    private int sampleCurrentNalBytesRemaining;

    public Parser() {
        atomHeader = new ParsableByteArray(Atom.LONG_HEADER_SIZE);
        containerAtoms = new Stack<>();
        extractorState = STATE_READING_ATOM_HEADER;
        nalStartCode = new ParsableByteArray(H264Util.NAL_START_CODE);
        nalLength = new ParsableByteArray(4);
    }

    public boolean prepare(ExtractorInput input, DataSource dataSource, Uri uri) throws InterruptedException, IOException {
        PositionHolder positionHolder = new PositionHolder();
        positionHolder.position = 0;
        boolean retry = false;
        extractorState = STATE_READING_ATOM_HEADER;
        while (true) {
            switch (extractorState) {
                case STATE_READING_ATOM_HEADER:
                    Log.i(TAG, "Read atom header");
                    if (!readAtomHeader(input)) {
                        if(input != null) {
                            positionHolder.position = input.getPosition();
                        }
                        retry = true;
                    }
                    break;
                case STATE_READING_ATOM_PAYLOAD:
                    Log.i(TAG, "Read atom payload");
                    if (readAtomPayload(input, positionHolder)) {
                        retry = true;
                    }
                    break;
                default:
                    return true;
            }
            if(retry) {
                Log.i(TAG, "Retry: Closing Data source");
                dataSource.close();
                Log.i(TAG, "Retry: Closed Data source");
                long position = positionHolder.position;
                Log.i(TAG, "Retry: Opening data source");
                try {
                    long length = dataSource.open(new DataSpec(uri, position, Constants.LENGTH_UNBOUNDED, null));
                    Log.i(TAG, "Retyr: Opened data source");
                    if (length != Constants.LENGTH_UNBOUNDED) {
                        length += position;
                    }
                    input = new CustomExtractorInput(dataSource, position, length);
                    retry = false;
                }
                catch (IOException ex) {
                    Log.e(TAG, "IOException while opening data source " + ex);
                    throw ex;
                }
            }
        }
    }

    public void setExtractorOutput(ExtractorOutput extractorOutput) {
        this.extractorOutput = extractorOutput;
    }

    public void seek() {
        rootAtomBytesRead = 0;
        sampleBytesWritten = 0;
        sampleCurrentNalBytesRemaining = 0;
    }

    private long getPrevSyncPosition(long timeUs) {
        long earliestSamplePosition = Long.MAX_VALUE;
        for (int trackIndex = 0; trackIndex < tracks.length; trackIndex++) {
            TrackSampleTable sampleTable = tracks[trackIndex].sampleTable;
            int sampleIndex = sampleTable.getIndexOfEarlierOrEqualSynchronizationSample(timeUs);
            if (sampleIndex == TrackSampleTable.NO_SAMPLE) {
                sampleIndex = sampleTable.getIndexOfLaterOrEqualSynchronizationSample(timeUs);
            }
            tracks[trackIndex].sampleIndex = sampleIndex;

            long offset = sampleTable.offsets[tracks[trackIndex].sampleIndex];
            if (offset < earliestSamplePosition) {
                earliestSamplePosition = offset;
            }
        }
        return earliestSamplePosition;
    }

    private long getNextOrEqualClosestPosition(long timeUs) {
        long earliestSamplePosition = Long.MAX_VALUE;
        for (int trackIndex = 0; trackIndex < tracks.length; trackIndex++) {
            TrackSampleTable sampleTable = tracks[trackIndex].sampleTable;
            int sampleIndex = sampleTable.getIndexOfLaterOrEqualClosestSample(timeUs);
            if (sampleIndex == TrackSampleTable.NO_SAMPLE) {
                sampleIndex = sampleTable.getIndexOfEarlierOrEqualSynchronizationSample(timeUs);
            }
            tracks[trackIndex].sampleIndex = sampleIndex;

            long offset = sampleTable.offsets[tracks[trackIndex].sampleIndex];
            if (offset < earliestSamplePosition) {
                earliestSamplePosition = offset;
            }
        }
        return earliestSamplePosition;
    }

    private long getNextOrEqualSyncPosition(long timeUs) {
        long earliestSamplePosition = Long.MAX_VALUE;
        for (int trackIndex = 0; trackIndex < tracks.length; trackIndex++) {
            TrackSampleTable sampleTable = tracks[trackIndex].sampleTable;
            int sampleIndex = sampleTable.getIndexOfEarlierOrEqualSynchronizationSample(timeUs);
            if (sampleIndex == TrackSampleTable.NO_SAMPLE) {
                sampleIndex = sampleTable.getIndexOfLaterOrEqualSynchronizationSample(timeUs);
            }
            tracks[trackIndex].sampleIndex = sampleIndex;

            long offset = sampleTable.offsets[tracks[trackIndex].sampleIndex];
            if (offset < earliestSamplePosition) {
                earliestSamplePosition = offset;
            }
        }
        return earliestSamplePosition;
    }

    public long getPosition(long timeUs, int seekType) {
        switch (seekType) {
            case MediaExtractor.SEEK_TO_PREV_SYNC :
                return getPrevSyncPosition(timeUs);
            case MediaExtractor.SEEK_TO_NEXT_CLOSEST_FRAME :
                return getNextOrEqualClosestPosition(timeUs);
            case MediaExtractor.SEEK_TO_NEXT_SYNC :
                return getNextOrEqualSyncPosition(timeUs);
            default:
                return getPrevSyncPosition(timeUs);
        }
    }

    /**
     * ****** Internal Stuff **********
     */
    private boolean readAtomHeader(ExtractorInput input) throws IOException, InterruptedException {
        if (!input.readFully(atomHeader.data, 0, Atom.HEADER_SIZE, true)) {
            return false;
        }

        atomHeader.setPosition(0);
        atomSize = atomHeader.readUnsignedInt();
        atomType = atomHeader.readInt();
        if (atomSize == Atom.LONG_SIZE_PREFIX) {
            // The extended atom size is contained in the next 8 bytes, so try to read it now.
            input.readFully(atomHeader.data, Atom.HEADER_SIZE, Atom.LONG_HEADER_SIZE - Atom.HEADER_SIZE);
            atomSize = atomHeader.readLong();
            rootAtomBytesRead += Atom.LONG_HEADER_SIZE;
            atomBytesRead = Atom.LONG_HEADER_SIZE;
        } else {
            rootAtomBytesRead += Atom.HEADER_SIZE;
            atomBytesRead = Atom.HEADER_SIZE;
        }

        if (shouldParseContainerAtom(atomType)) {
            if (atomSize == Atom.LONG_SIZE_PREFIX) {
                containerAtoms.add(
                        new Atom.ContainerAtom(atomType, rootAtomBytesRead + atomSize - atomBytesRead));
            } else {
                containerAtoms.add(
                        new Atom.ContainerAtom(atomType, rootAtomBytesRead + atomSize - atomBytesRead));
            }
            extractorState = STATE_READING_ATOM_HEADER;
        } else if (shouldParseLeafAtom(atomType)) {
            Assertions.checkState(atomSize < Integer.MAX_VALUE);
            atomData = new ParsableByteArray((int) atomSize);
            System.arraycopy(atomHeader.data, 0, atomData.data, 0, Atom.HEADER_SIZE);
            extractorState = STATE_READING_ATOM_PAYLOAD;
        } else {
            atomData = null;
            extractorState = STATE_READING_ATOM_PAYLOAD;
        }

        return true;
    }

    private boolean readAtomPayload(ExtractorInput input, PositionHolder positionHolder)
            throws IOException, InterruptedException {
        extractorState = STATE_READING_ATOM_HEADER;
        rootAtomBytesRead += atomSize - atomBytesRead;
        long atomRemainingBytes = atomSize - atomBytesRead;
        boolean seekRequired = atomData == null
                && (atomSize >= RELOAD_MINIMUM_SEEK_DISTANCE || atomSize > Integer.MAX_VALUE);
        if (seekRequired) {
            positionHolder.position = rootAtomBytesRead;
        } else if (atomData != null) {
            input.readFully(atomData.data, atomBytesRead, (int) atomRemainingBytes);
            if (!containerAtoms.isEmpty()) {
                containerAtoms.peek().add(new Atom.LeafAtom(atomType, atomData));
            }
        } else {
            input.skipFully((int) atomRemainingBytes);
        }

        while (!containerAtoms.isEmpty() && containerAtoms.peek().endByteOffset == rootAtomBytesRead) {
            Atom.ContainerAtom containerAtom = containerAtoms.pop();
            if (containerAtom.type == Atom.TYPE_moov) {
                processMoovAtom(containerAtom);
            } else if (!containerAtoms.isEmpty()) {
                containerAtoms.peek().add(containerAtom);
            }
        }
        return seekRequired;
    }

    public int readSampleData(ExtractorInput input, PositionHolder positionHolder, int trackIndex) throws IOException, InterruptedException {
        int earliestTrackIndex = getTrackIndexOfEarliestCurrentSample();
        if (earliestTrackIndex == TrackSampleTable.NO_SAMPLE) {
            return RESULT_END_OF_INPUT;
        }
        Mp4Track track = tracks[earliestTrackIndex];
        if(trackIndex != earliestTrackIndex) { // do not load the sample for this track
            track.sampleIndex++;
            return RESULT_CONTINUE;
        }
        int sampleIndex = track.sampleIndex;
        long position = track.sampleTable.offsets[sampleIndex];
        long skipAmount = position - input.getPosition() + sampleBytesWritten;
        if (skipAmount < 0 || skipAmount >= RELOAD_MINIMUM_SEEK_DISTANCE) {
            positionHolder.position = position;
            return RESULT_SEEK;
        }
        input.skipFully((int) skipAmount);
        sampleSize = track.sampleTable.sizes[sampleIndex];
        //Log.i(TAG, "SampleSize " + sampleSize + " " + track.sampleTable.timestampsUs[sampleIndex]);
        if (track.track.nalUnitLengthFieldLength != -1) {
            // Zero the top three bytes of the array that we'll use to parse nal unit lengths, in case
            // they're only 1 or 2 bytes long.
            byte[] nalLengthData = nalLength.data;
            nalLengthData[0] = 0;
            nalLengthData[1] = 0;
            nalLengthData[2] = 0;
            int nalUnitLengthFieldLength = track.track.nalUnitLengthFieldLength;
            int nalUnitLengthFieldLengthDiff = 4 - track.track.nalUnitLengthFieldLength;
            // NAL units are length delimited, but the decoder requires start code delimited units.
            // Loop until we've written the sample to the track output, replacing length delimiters with
            // start codes as we encounter them.
            while (sampleBytesWritten < sampleSize) {
                if (sampleCurrentNalBytesRemaining == 0) {
                    // Read the NAL length so that we know where we find the next one.
                    input.readFully(nalLength.data, nalUnitLengthFieldLengthDiff, nalUnitLengthFieldLength);
                    nalLength.setPosition(0);
                    sampleCurrentNalBytesRemaining = nalLength.readUnsignedIntToInt();
                    // Write a start code for the current NAL unit.
                    nalStartCode.setPosition(0);
                    track.trackOutput.sampleData(nalStartCode, 4);
                    sampleBytesWritten += 4;
                    sampleSize += nalUnitLengthFieldLengthDiff;
                } else {
                    // Write the payload of the NAL unit.
                    int writtenBytes = track.trackOutput.sampleData(input, sampleCurrentNalBytesRemaining);
                    sampleBytesWritten += writtenBytes;
                    sampleCurrentNalBytesRemaining -= writtenBytes;
                }
            }
        } else {
            while (sampleBytesWritten < sampleSize) {
                int writtenBytes = track.trackOutput.sampleData(input, sampleSize - sampleBytesWritten);
                sampleBytesWritten += writtenBytes;
                sampleCurrentNalBytesRemaining -= writtenBytes;
            }
        }
        track.trackOutput.sampleMetadata(track.sampleTable.timestampsUs[sampleIndex],
                track.sampleTable.flags[sampleIndex], sampleSize, 0, null);
        track.sampleIndex++;
        sampleBytesWritten = 0;
        sampleCurrentNalBytesRemaining = 0;
        return RESULT_CONTINUE;
    }

    private void processMoovAtom(Atom.ContainerAtom moov) {
        List<Mp4Track> tracks = new ArrayList<>();
        long earliestSampleOffset = Long.MAX_VALUE;
        for (int i = 0; i < moov.containerChildren.size(); i++) {
            Atom.ContainerAtom atom = moov.containerChildren.get(i);
            if (atom.type != Atom.TYPE_trak) {
                continue;
            }

            Track track = AtomParsers.parseTrak(atom, moov.getLeafAtomOfType(Atom.TYPE_mvhd));
            if (track == null || (track.type != Track.TYPE_AUDIO && track.type != Track.TYPE_VIDEO)) {
                continue;
            }

            Atom.ContainerAtom stblAtom = atom.getContainerAtomOfType(Atom.TYPE_mdia)
                    .getContainerAtomOfType(Atom.TYPE_minf).getContainerAtomOfType(Atom.TYPE_stbl);
            TrackSampleTable trackSampleTable = AtomParsers.parseStbl(track, stblAtom);
            if (trackSampleTable.sampleCount == 0) {
                continue;
            }

            Mp4Track mp4Track = new Mp4Track(track, trackSampleTable, extractorOutput.getTrackOutput(i));
            mp4Track.trackOutput.format(track.mediaFormat);
            tracks.add(mp4Track);

            long firstSampleOffset = trackSampleTable.offsets[0];
            if (firstSampleOffset < earliestSampleOffset) {
                earliestSampleOffset = firstSampleOffset;
            }
        }
        this.tracks = tracks.toArray(new Mp4Track[0]);
        extractorOutput.builtTracks();
        extractorState = STATE_READING_SAMPLE;
    }

    private int getTrackIndexOfEarliestCurrentSample() {
        int earliestSampleTrackIndex = TrackSampleTable.NO_SAMPLE;
        long earliestSampleOffset = Long.MAX_VALUE;
        for (int trackIndex = 0; trackIndex < tracks.length; trackIndex++) {
            Mp4Track track = tracks[trackIndex];
            int sampleIndex = track.sampleIndex;
            if (sampleIndex == track.sampleTable.sampleCount) {
                continue;
            }

            long trackSampleOffset = track.sampleTable.offsets[sampleIndex];
            if (trackSampleOffset < earliestSampleOffset) {
                earliestSampleOffset = trackSampleOffset;
                earliestSampleTrackIndex = trackIndex;
            }
        }

        return earliestSampleTrackIndex;
    }

    private static boolean shouldParseContainerAtom(int atom) {
        return atom == Atom.TYPE_moov || atom == Atom.TYPE_trak || atom == Atom.TYPE_mdia
                || atom == Atom.TYPE_minf || atom == Atom.TYPE_stbl || atom == Atom.TYPE_mp4a;
    }

    private static boolean shouldParseLeafAtom(int atom) {
        return atom == Atom.TYPE_mdhd || atom == Atom.TYPE_mvhd || atom == Atom.TYPE_hdlr
                || atom == Atom.TYPE_vmhd || atom == Atom.TYPE_smhd || atom == Atom.TYPE_stsd
                || atom == Atom.TYPE_avc1 || atom == Atom.TYPE_avcC || atom == Atom.TYPE_mp4a
                || atom == Atom.TYPE_esds || atom == Atom.TYPE_stts || atom == Atom.TYPE_stss
                || atom == Atom.TYPE_ctts || atom == Atom.TYPE_stsc || atom == Atom.TYPE_stsz
                || atom == Atom.TYPE_stco || atom == Atom.TYPE_co64 || atom == Atom.TYPE_tkhd
                || atom == Atom.TYPE_wave;
    }

    private static final class Mp4Track {
        public final Track track;
        public final TrackSampleTable sampleTable;
        public final TrackOutput trackOutput;

        public int sampleIndex;

        public Mp4Track(Track track, TrackSampleTable sampleTable, TrackOutput trackOutput) {
            this.track = track;
            this.sampleTable = sampleTable;
            this.trackOutput = trackOutput;
        }
    }
}
