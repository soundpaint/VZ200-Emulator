// TODO: move this class into new package 'emulator.cpu'.
package emulator.z80;

/**
 * Default implementation for RAM Memory.
 */
public class RAMMemory implements CPU.Memory {
  protected int[] ram;
  protected int minAddr, maxAddr;

  protected RAMMemory() {}

  public RAMMemory(int minAddr, int size) {
    if (size < 0)
      throw new IllegalArgumentException("size < 0");
    if (minAddr < 0)
      throw new IllegalArgumentException("minAddr < 0");
    if (minAddr + size < 0)
      throw new IllegalArgumentException("minAddr + size beyond MAX_INT");
    ram = new int[size];
    this.minAddr = minAddr;
    this.maxAddr = minAddr + size - 1;
  }

  public boolean isValidAddr(int address) {
    return (minAddr <= address) && (address <= maxAddr);
  }

  public int readByte(int address) {
    address -= minAddr;
    return
      ram[address];
  }

  public int readShort(int address) {
    address -= minAddr;
    return
      ram[address++] |
      (ram[address] << 8);
  }

  public int readInt(int address) {
    address -= minAddr;
    return
      ram[address++] |
      (ram[address++] << 8) |
      (ram[address++] << 16) |
      (ram[address] << 24);
  }

  public void writeByte(int address, int value) {
    address -= minAddr;
    ram[address] = value & 0xff;
  }

  public void writeShort(int address, int value) {
    address -= minAddr;
    ram[address++] = value & 0xff;
    value >>>= 8;
    ram[address] = value & 0xff;
  }

  public void writeInt(int address, int value) {
    address -= minAddr;
    ram[address++] = value & 0xff;
    value >>>= 8;
    ram[address++] = value & 0xff;
    value >>>= 8;
    ram[address++] = value & 0xff;
    value >>>= 8;
    ram[address] = value & 0xff;
  }

  public int[] getByteArray() {
    return ram;
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
