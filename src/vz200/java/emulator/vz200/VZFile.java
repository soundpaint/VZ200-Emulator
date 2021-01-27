package emulator.vz200;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;

import emulator.z80.CPU;
import emulator.z80.WallClockProvider;

public class VZFile
{
  private static final int VZ_MAGIC_LENGTH = 4;
  private static final byte[] VZ_MAGIC_1 = {0x20, 0x20, 0x00, 0x00};
  private static final byte[] VZ_MAGIC_2 = {0x56, 0x5a, 0x46, 0x30};

  private static final int FILE_TYPE_BASIC = 0xf0;
  private static final int FILE_TYPE_BINARY = 0xf1;
  private static final int FILE_NAME_MAX_LEN = 16;

  // 24 bytes header (4 bytes magic + 20 bytes data)
  private final String fileName;
  private final byte[] fileNameBytes; // 17 bytes of 0-terminated
                                      // ASCII string
  private final int fileType;
  private final int startAddress;
  private final byte startAddrLowByte;
  private final byte startAddrHighByte;

  private List<Byte> fileData;
  private int endAddress;
  private byte endAddrLowByte;
  private byte endAddrHighByte;
  private int checkSum;

  private VZFile()
  {
    throw new UnsupportedOperationException("unsupported empty constructor");
  }

  /**
   * The VZ200's Z80 CPU supports 64k of address space, and 16k are
   * already covered by the ROMs (and even some more space by memory
   * mapped I/O).  Hence, there is no point in saving or loading more
   * than 48k of data, just to avoid loading of a crazy large file by
   * mistake or attack.
   */
  private final static int FILE_DATA_MAX_LENGTH = 49152;

  private VZFile(final String fileName,
                 final int fileType,
                 final int startAddress,
                 final byte[] fileData)
  {
    this(fileName, fileType, startAddress);
    addFileData(fileData);
  }

  private VZFile(final String fileName,
                 final int fileType,
                 final int startAddress)
  {
    if (fileName == null) {
      throw new NullPointerException("filename");
    }
    if (fileName.length() > 16) {
      throw new IllegalArgumentException("filename too long: " + fileName);
    }
    if ((fileType != FILE_TYPE_BASIC) && (fileType != FILE_TYPE_BINARY)) {
      throw new IllegalArgumentException("unsupported file type: " +
                                         String.format("%02x", fileType));
    }
    this.fileName = fileName;
    this.fileType = fileType;
    this.startAddress = startAddress;
    startAddrLowByte = (byte)startAddress;
    startAddrHighByte = (byte)(startAddress >>> 8);
    final byte[] fileNameBytes = fileName.getBytes(StandardCharsets.US_ASCII);
    this.fileNameBytes = new byte[17];
    for (int i = 0; i < this.fileNameBytes.length; i++) {
      this.fileNameBytes[i] = i < fileNameBytes.length ? fileNameBytes[i] : 0;
    }
    fileData = new ArrayList<Byte>();
    updateComputedProperties();
  }

  private void updateComputedProperties()
  {
    endAddress = startAddress + fileData.size();
    endAddrLowByte = (byte)endAddress;
    endAddrHighByte = (byte)(endAddress >>> 8);
    System.out.printf("%s: updated end address: %04x%n", fileName, endAddress);

    checkSum = (startAddrLowByte & 0xff) + (startAddrHighByte & 0xff);
    checkSum += (endAddrLowByte & 0xff) + (endAddrHighByte & 0xff);
    for (final byte b: fileData) {
      checkSum += b & 0xff;
    }
    checkSum &= 0xffff;
    System.out.printf("%s: updated checksum: %04x%n", fileName, checkSum);
  }

  public String getFileName() { return fileName; }

  public int getFileType() { return fileType; }

  public int getStartAddress() { return startAddress; }

  public int getEndAddress() { return endAddress; }

  private void addFileData(final byte[] fileData)
  {
    if (fileData.length > FILE_DATA_MAX_LENGTH) {
      throw new IllegalArgumentException("file too large");
    }
    for (final byte b : fileData) {
      this.fileData.add(b);
    }
    updateComputedProperties();
  }

  public int getContentSize()
  {
    return fileData.size();
  }

  public byte getContentByte(final int index)
  {
    return fileData.get(index);
  }

  public int getCheckSum()
  {
    return checkSum;
  }

  private static String toHexSequence(final byte[] bytes)
  {
    final StringBuffer s = new StringBuffer();
    for (final byte b : bytes) {
      if (s.length() > 0) {
        s.append(", ");
      }
      s.append(String.format("%02Xh", b));
    }
    return "(" + s + ")";
  }

  private static boolean matchesMagic(final byte[] magic1, final byte[] magic2)
  {
    if (magic1.length != magic2.length) {
      return false;
    }
    for (int i = 0; i < magic1.length; i++) {
      if (magic1[i] != magic2[i]) {
        return false;
      }
    }
    return true;
  }

  private static String readFixedSizeString(final FileInputStream is,
                                            final int length)
    throws IOException
  {
    final byte[] fileNameBytes = new byte[length];
    final int bytesRead = is.read(fileNameBytes);
    if (bytesRead != length) {
      throw new EOFException("premature end of file in file name data");
    }
    final StringBuffer s = new StringBuffer();
    for (final byte ch : fileNameBytes) {
      if ((ch == 0) && (s.length() > 0)) break;
      if (ch != 0)
        s.append((char)ch);
    }
    return s.toString();
  }

  private static String readNullTerminatedString(final FileInputStream is)
    throws IOException
  {
    final StringBuffer s = new StringBuffer();
    int ch = is.read();
    while (ch != 0) {
      s.append(ch);
      ch = is.read();
    }
    System.out.println("s={" + s + "}");
    return s.toString();
  }

  public static VZFile fromFile(final FileInputStream is)
    throws IOException
  {
    if (is.available() > FILE_DATA_MAX_LENGTH) {
      throw new IOException("file too large -- probably not a true .vz file");
    }

    // magic number
    final byte[] magic = new byte[VZ_MAGIC_LENGTH];
    for (int i = 0; i < VZ_MAGIC_LENGTH; i++) {
      final int dataByte = is.read();
      if (dataByte == -1) {
        throw new EOFException("premature end of file in magic number");
      }
      magic[i] = (byte)dataByte;
    }
    if (!matchesMagic(magic, VZ_MAGIC_1) &&
        !matchesMagic(magic, VZ_MAGIC_2)) {
        throw new IOException("bad magic number: expected either " +
                              toHexSequence(VZ_MAGIC_1) + " or " +
                              toHexSequence(VZ_MAGIC_2) + ", but got " +
                              toHexSequence(magic));
    }

    // file name
    final String fileName = readFixedSizeString(is, FILE_NAME_MAX_LEN + 1);

    // file type
    final int fileType = is.read();
    if (fileType == -1) {
      throw new EOFException("premature end of file in file type");
    }
    if ((fileType != FILE_TYPE_BASIC) && (fileType != FILE_TYPE_BINARY)) {
      throw new EOFException("unsupported file type: " +
                             String.format("%02x", fileType));
    }

    // start address
    final int startAddrLo = is.read();
    if (startAddrLo == -1) {
      throw new EOFException("premature end of file in start address LSB");
    }
    final int startAddrHi = is.read();
    if (startAddrHi == -1) {
      throw new EOFException("premature end of file in start address LSB");
    }
    final int startAddr = (startAddrHi << 8) | startAddrLo;

    // file data
    final int fileDataLength = is.available();
    if (fileDataLength > FILE_DATA_MAX_LENGTH) {
      throw new IOException("file too large -- probably not a true .vz file");
    }
    final byte[] fileData = new byte[fileDataLength];
    final int bytesRead = is.read(fileData);
    if (bytesRead != fileDataLength) {
      throw new EOFException("premature end of file in file data");
    }

    return new VZFile(fileName, fileType, startAddr, fileData);
  }

  public static VZFile fromFile(final File fromFile)
    throws IOException
  {
    FileInputStream is = null;
    try {
      is = new FileInputStream(fromFile);
      return fromFile(is);
    } catch (final EOFException e) {
      System.out.println("unexpected premature end of file");
      throw e;
    } catch (final IOException e) {
      System.out.println("I/O error: " + e);
      throw e;
    } finally {
      if (is != null) {
        try {
          is.close();
        } catch (final IOException e) {
          // ignore
        }
      }
    }
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
