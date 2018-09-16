// TODO: move this class into new package 'emulator.cpu'.
package emulator.z80;

import java.io.DataInputStream;
import java.io.InputStream;
import java.io.IOException;

/**
 * Default implementation for ROM Memory.
 */
public class ROMMemory implements MemoryBus.BusWriter
{
  protected int baseAddress;
  protected int[] data;

  private ROMMemory() {
    throw new UnsupportedOperationException("unsupported constructor");
  }

  protected ROMMemory(int baseAddress, int[] data) {
    if (baseAddress < 0)
      throw new IllegalArgumentException("baseAddress < 0");
    if (data == null)
      throw new NullPointerException("data");
    int size = data.length;
    if (baseAddress + size < 0)
      throw new IllegalArgumentException("baseAddress + size beyond MAX_INT");
    this.baseAddress = baseAddress;
    this.data = data;
  }

  public ROMMemory(Class<? extends Object> baseClass, String resourceName,
                   int baseAddress, int size)
    throws IOException
  {
    this(baseAddress, loadROM(baseClass, resourceName, size));
  }

  private static int[] loadROM(Class<? extends Object> baseClass,
                               String resourceName, int size)
    throws IOException
  {
    byte[] romBytes = new byte[size];
    InputStream resource = baseClass.getResourceAsStream(resourceName);
    if (resource == null)
      throw new IOException("resource '" + resourceName + "' not found");
    DataInputStream is =
      new DataInputStream(resource);
    is.readFully(romBytes);
    if (is.read() != -1)
      throw new IOException("EOF expected: " + resourceName +
			    " file too long");
    is.close();
    int[] data = new int[size];
    for (int i = 0; i < size; i++)
      data[i] = romBytes[i] & 0xff;
    return data;
  }

  public int[] getByteArray() {
    return data;
  }

  public int readByte(int address, long wallClockTime) {
    int addressOffset = (address - baseAddress) & 0xffff;
    int result;
    if (addressOffset < data.length) {
      result = data[addressOffset];
    } else {
      result = BYTE_UNDEFINED;
    }
    return result;
  }

  public int readShort(int address, long wallClockTime) {
    int addressOffset = (address - baseAddress) & 0xffff;
    int resultLSB;
    if (addressOffset < data.length) {
      resultLSB = data[addressOffset];
    } else {
      resultLSB = BYTE_UNDEFINED;
    }
    addressOffset = (addressOffset + 1) & 0xffff;
    int resultMSB;
    if (addressOffset < data.length) {
      resultMSB = data[addressOffset];
    } else {
      resultMSB = BYTE_UNDEFINED;
    }
    return (resultMSB << 8) | resultLSB;
  }

  public void writeByte(int address, int value, long wallClockTime) {}
  public void writeShort(int address, int value, long wallClockTime) {}
  public void resync(long wallClockTime) {}

  public String toString()
  {
    return "ROM Memory[baseAddress=" + Util.hexShortStr(baseAddress) +
      ", size=" + Util.hexShortStr(data.length) + "]";
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
