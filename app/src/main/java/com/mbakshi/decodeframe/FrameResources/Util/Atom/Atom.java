package com.mbakshi.decodeframe.FrameResources.Util.Atom;

import com.mbakshi.decodeframe.FrameResources.Util.ParsableByteArray;
import com.mbakshi.decodeframe.FrameResources.Util.Utilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by mbakshi on 20/08/15.
 */
public abstract class Atom {

    /** Size of an atom header, in bytes. */
    public static final int HEADER_SIZE = 8;

    /** Size of a full atom header, in bytes. */
    public static final int FULL_HEADER_SIZE = 12;

    /** Size of a long atom header, in bytes. */
    public static final int LONG_HEADER_SIZE = 16;

    /** Value for the first 32 bits of atomSize when the atom size is actually a long value. */
    public static final int LONG_SIZE_PREFIX = 1;

    public static final int TYPE_ftyp = Utilities.getIntegerCodeForString("ftyp");
    public static final int TYPE_avc1 = Utilities.getIntegerCodeForString("avc1");
    public static final int TYPE_avc3 = Utilities.getIntegerCodeForString("avc3");
    public static final int TYPE_esds = Utilities.getIntegerCodeForString("esds");
    public static final int TYPE_mdat = Utilities.getIntegerCodeForString("mdat");
    public static final int TYPE_mp4a = Utilities.getIntegerCodeForString("mp4a");
    public static final int TYPE_ac_3 = Utilities.getIntegerCodeForString("ac-3");
    public static final int TYPE_dac3 = Utilities.getIntegerCodeForString("dac3");
    public static final int TYPE_ec_3 = Utilities.getIntegerCodeForString("ec-3");
    public static final int TYPE_dec3 = Utilities.getIntegerCodeForString("dec3");
    public static final int TYPE_tfdt = Utilities.getIntegerCodeForString("tfdt");
    public static final int TYPE_tfhd = Utilities.getIntegerCodeForString("tfhd");
    public static final int TYPE_trex = Utilities.getIntegerCodeForString("trex");
    public static final int TYPE_trun = Utilities.getIntegerCodeForString("trun");
    public static final int TYPE_sidx = Utilities.getIntegerCodeForString("sidx");
    public static final int TYPE_moov = Utilities.getIntegerCodeForString("moov");
    public static final int TYPE_mvhd = Utilities.getIntegerCodeForString("mvhd");
    public static final int TYPE_trak = Utilities.getIntegerCodeForString("trak");
    public static final int TYPE_mdia = Utilities.getIntegerCodeForString("mdia");
    public static final int TYPE_minf = Utilities.getIntegerCodeForString("minf");
    public static final int TYPE_stbl = Utilities.getIntegerCodeForString("stbl");
    public static final int TYPE_avcC = Utilities.getIntegerCodeForString("avcC");
    public static final int TYPE_moof = Utilities.getIntegerCodeForString("moof");
    public static final int TYPE_traf = Utilities.getIntegerCodeForString("traf");
    public static final int TYPE_mvex = Utilities.getIntegerCodeForString("mvex");
    public static final int TYPE_tkhd = Utilities.getIntegerCodeForString("tkhd");
    public static final int TYPE_mdhd = Utilities.getIntegerCodeForString("mdhd");
    public static final int TYPE_hdlr = Utilities.getIntegerCodeForString("hdlr");
    public static final int TYPE_stsd = Utilities.getIntegerCodeForString("stsd");
    public static final int TYPE_pssh = Utilities.getIntegerCodeForString("pssh");
    public static final int TYPE_sinf = Utilities.getIntegerCodeForString("sinf");
    public static final int TYPE_schm = Utilities.getIntegerCodeForString("schm");
    public static final int TYPE_schi = Utilities.getIntegerCodeForString("schi");
    public static final int TYPE_tenc = Utilities.getIntegerCodeForString("tenc");
    public static final int TYPE_encv = Utilities.getIntegerCodeForString("encv");
    public static final int TYPE_enca = Utilities.getIntegerCodeForString("enca");
    public static final int TYPE_frma = Utilities.getIntegerCodeForString("frma");
    public static final int TYPE_saiz = Utilities.getIntegerCodeForString("saiz");
    public static final int TYPE_uuid = Utilities.getIntegerCodeForString("uuid");
    public static final int TYPE_senc = Utilities.getIntegerCodeForString("senc");
    public static final int TYPE_pasp = Utilities.getIntegerCodeForString("pasp");
    public static final int TYPE_TTML = Utilities.getIntegerCodeForString("TTML");
    public static final int TYPE_vmhd = Utilities.getIntegerCodeForString("vmhd");
    public static final int TYPE_smhd = Utilities.getIntegerCodeForString("smhd");
    public static final int TYPE_mp4v = Utilities.getIntegerCodeForString("mp4v");
    public static final int TYPE_stts = Utilities.getIntegerCodeForString("stts");
    public static final int TYPE_stss = Utilities.getIntegerCodeForString("stss");
    public static final int TYPE_ctts = Utilities.getIntegerCodeForString("ctts");
    public static final int TYPE_stsc = Utilities.getIntegerCodeForString("stsc");
    public static final int TYPE_stsz = Utilities.getIntegerCodeForString("stsz");
    public static final int TYPE_stco = Utilities.getIntegerCodeForString("stco");
    public static final int TYPE_co64 = Utilities.getIntegerCodeForString("co64");
    public static final int TYPE_wave = Utilities.getIntegerCodeForString("wave");

    public final int type;

    Atom(int type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return getAtomTypeString(type);
    }

    /** An MP4 atom that is a leaf. */
    public static final class LeafAtom extends Atom {

        public final ParsableByteArray data;

        public LeafAtom(int type, ParsableByteArray data) {
            super(type);
            this.data = data;
        }

    }

    /** An MP4 atom that has child atoms. */
    public static final class ContainerAtom extends Atom {

        public final long endByteOffset;
        public final List<LeafAtom> leafChildren;
        public final List<ContainerAtom> containerChildren;

        public ContainerAtom(int type, long endByteOffset) {
            super(type);

            leafChildren = new ArrayList<LeafAtom>();
            containerChildren = new ArrayList<ContainerAtom>();
            this.endByteOffset = endByteOffset;
        }

        public void add(LeafAtom atom) {
            leafChildren.add(atom);
        }

        public void add(ContainerAtom atom) {
            containerChildren.add(atom);
        }

        public LeafAtom getLeafAtomOfType(int type) {
            int childrenSize = leafChildren.size();
            for (int i = 0; i < childrenSize; i++) {
                LeafAtom atom = leafChildren.get(i);
                if (atom.type == type) {
                    return atom;
                }
            }
            return null;
        }

        public ContainerAtom getContainerAtomOfType(int type) {
            int childrenSize = containerChildren.size();
            for (int i = 0; i < childrenSize; i++) {
                ContainerAtom atom = containerChildren.get(i);
                if (atom.type == type) {
                    return atom;
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return getAtomTypeString(type)
                    + " leaves: " + Arrays.toString(leafChildren.toArray(new LeafAtom[0]))
                    + " containers: " + Arrays.toString(containerChildren.toArray(new ContainerAtom[0]));
        }

    }

    /**
     * Parses the version number out of the additional integer component of a full atom.
     */
    public static int parseFullAtomVersion(int fullAtomInt) {
        return 0x000000FF & (fullAtomInt >> 24);
    }

    /**
     * Parses the atom flags out of the additional integer component of a full atom.
     */
    public static int parseFullAtomFlags(int fullAtomInt) {
        return 0x00FFFFFF & fullAtomInt;
    }

    /**
     * Converts a numeric atom type to the corresponding four character string.
     *
     * @param type The numeric atom type.
     * @return The corresponding four character string.
     */
    public static String getAtomTypeString(int type) {
        return "" + (char) (type >> 24)
                + (char) ((type >> 16) & 0xFF)
                + (char) ((type >> 8) & 0xFF)
                + (char) (type & 0xFF);
    }
}
