package com.blackberry.kafka.lowoverhead.compression;

import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import com.blackberry.kafka.lowoverhead.Constants;

public class GzipDecompressor implements Decompressor {
  // various fields, as named in rfc 1952
  private static final byte ID1 = (byte) 0x1f;
  private static final byte ID2 = (byte) 0x8b;
  private static final byte CM = 8; // Compression Method = Deflate

  // masks for various flags
  // FTEXT flag is ignored, but we keep it here for completeness
  @SuppressWarnings("unused")
  private static final byte FLG_FTEXT = 0x01;
  private static final byte FLG_FHCRC = 0x02;
  private static final byte FLG_FEXTRA = 0x04;
  private static final byte FLG_FNAME = 0x08;
  private static final byte FLG_COMMENT = 0x10;

  private byte flags;
  private boolean fhcrc;
  private boolean fextra;
  private boolean fname;
  private boolean comment;

  private Inflater inflater;
  private int pos;
  private short extraLength;
  private int decompressedLength;

  public GzipDecompressor() {
    inflater = new Inflater(true);
  }

  @Override
  public byte getAttribute() {
    return Constants.GZIP;
  }

  @Override
  public int decompress(byte[] src, int srcPos, int length, byte[] dest,
      int destPos, int maxLength) throws IOException {
    pos = srcPos;

    // Check the magic number and compression method
    if (src[pos] != ID1 || src[pos + 1] != ID2) {
      throw new IOException("Wrong gzip magic number.");
    }
    if (src[pos + 2] != CM) {
      throw new IOException("Unrecognized compession method.");
    }
    pos += 3;

    // read flags
    flags = src[pos];
    // ftext = (FLG_FTEXT == (FLG_FTEXT & flags));
    fhcrc = (FLG_FHCRC == (FLG_FHCRC & flags));
    fextra = (FLG_FEXTRA == (FLG_FEXTRA & flags));
    fname = (FLG_FNAME == (FLG_FNAME & flags));
    comment = (FLG_COMMENT == (FLG_COMMENT & flags));
    pos++;

    // ignore the timestamp. 4 bytes
    // ignore extra flags. 1 byte
    // ignore OS. 1 byte
    pos += 6;

    if (fextra) {
      // skip it
      extraLength = readShort(src, pos);
      pos += 2 + extraLength;
    }

    if (fname) {
      // skip it
      while (src[pos] != 0x00) {
        pos++;
      }
      pos++;
    }

    if (comment) {
      // skip it
      while (src[pos] != 0x00) {
        pos++;
      }
      pos++;
    }

    if (fhcrc) {
      // skip it
      pos += 2;
    }

    inflater.reset();
    inflater.setInput(src, pos, length - (pos - srcPos) - 8);
    try {
      decompressedLength = inflater.inflate(dest, destPos, maxLength);
    } catch (DataFormatException e) {
      throw new IOException("Error decompressing data.", e);
    }

    return decompressedLength;
  }

  private short readShort(byte[] src, int pos) {
    // little endian!
    return (short) ((src[pos] & 0xFF) | (src[pos + 1] & 0xFF) << 8);
  }
}