// TODO: move this class into new package 'emulator.cpu'.
package emulator.z80;

/**
 * Default implementation for RAM Memory.
 */
public class RAMMemory extends ROMMemory implements MemoryBus.Reader {
  private static int[] createRAMData(int size) {
    if (size < 0)
      throw new IllegalArgumentException("size < 0");
    return new int[size];
  }

  public RAMMemory(int baseAddress, int size) {
    super(baseAddress, createRAMData(size));
  }

  public void writeByte(int address, int value) {
    int addressOffset = (address - baseAddress) & 0xffff;
    if (addressOffset < data.length) {
      data[addressOffset] = value & 0xff;
    }
  }

  public void writeShort(int address, int value) {
    int addressOffset = (address - baseAddress) & 0xffff;
    if (addressOffset < data.length) {
      data[addressOffset] = value & 0xff;
    }
    addressOffset = (addressOffset + 1) & 0xffff;
    value >>>= 8;
    if (addressOffset < data.length) {
      data[addressOffset] = value & 0xff;
    }
  }

  public int[] getByteArray() {
    return data;
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
